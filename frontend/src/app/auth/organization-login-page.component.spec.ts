import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../app.routes';
import { basicAuthInterceptor } from './basic-auth.interceptor';
import { BasicAuthService } from './basic-auth.service';
import { OrganizationLoginPageComponent } from './organization-login-page.component';

describe('OrganizationLoginPageComponent', () => {
  let fixture: ComponentFixture<OrganizationLoginPageComponent>;
  let auth: BasicAuthService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrganizationLoginPageComponent],
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([basicAuthInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OrganizationLoginPageComponent);
    auth = TestBed.inject(BasicAuthService);
    http = TestBed.inject(HttpTestingController);
    auth.logout();
    fixture.detectChanges();
  });

  it('confirms valid credentials only after the backend responds successfully', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    fixture.detectChanges();

    expect(auth.state()).toBe('pending');
    expect(fixture.nativeElement.textContent).not.toContain('El backend validó');
    const request = http.expectOne('/api/auth/validate');
    expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(auth.state()).toBe('authenticated');
    expect(fixture.nativeElement.textContent).toContain('El backend validó');
  });

  it('shows invalid credentials and clears their password from memory', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'wrong';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(auth.state()).toBe('invalid');
    expect(auth.authorizationHeader()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('El correo o la contraseña son incorrectos.');
  });

  it('reports denied access and connection errors separately', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(auth.state()).toBe('forbidden');

    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(auth.state()).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('No se pudo conectar');
  });

  it('clears in-memory credentials on logout', () => {
    auth.provideCredentials('org@example.test', 'test-password');
    fixture.componentInstance.cerrarSesion();
    expect(auth.state()).toBe('signed-out');
    expect(auth.authorizationHeader()).toBeNull();
    http.expectNone('/api/auth/validate');
  });
});
