import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
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

  it('confirms valid credentials only after the backend responds successfully', async () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    fixture.detectChanges();

    expect(auth.state()).toBe('pending');
    const request = http.expectOne('/api/auth/validate');
    expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
    const profileRequest = http.expectOne('/api/auth/me');
    expect(profileRequest.request.headers.has('Authorization')).toBe(true);
    profileRequest.flush({ nombre: 'Admin', apellido: 'Prueba', correo: 'org@example.test', rolCodigo: 'ADMINISTRADOR',
      rolNombre: 'Administrador', permisos: ['ACCESO_ADMINISTRAR'] });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.state()).toBe('authenticated');
    expect(TestBed.inject(Router).url).toBe('/admin');
  });

  it('shows invalid credentials and clears their password from memory', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'wrong';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectNone('/api/auth/me');
    http.expectNone('/api/auth/validate');
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
    http.expectNone('/api/auth/me');
    fixture.detectChanges();
    expect(auth.state()).toBe('forbidden');

    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').error(new ProgressEvent('error'));
    http.expectNone('/api/auth/validate');
    fixture.detectChanges();
    expect(auth.state()).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('No se pudo conectar');
  });

  it('reports server failures separately without retrying validation', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').flush({}, { status: 500, statusText: 'Server Error' });
    http.expectNone('/api/auth/validate');
    fixture.detectChanges();
    expect(auth.state()).toBe('server-error');
    expect(fixture.nativeElement.textContent).toContain('El servidor no pudo completar la validación');
  });

  it('accepts valid credentials but does not open the panel without admin permission', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    http.expectOne('/api/auth/validate').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/me').flush({ nombre: 'Org', apellido: 'User', correo: 'org@example.test',
      rolCodigo: 'ORGANIZACION', rolNombre: 'Organización', permisos: ['DONACION_REGISTRAR'] });
    fixture.detectChanges();
    expect(auth.state()).toBe('authenticated');
    expect(auth.hasPermission('ACCESO_ADMINISTRAR')).toBe(false);
    expect(TestBed.inject(Router).url).not.toBe('/admin');
    expect(fixture.nativeElement.textContent).toContain('no tiene permiso de acceso administrativo');
  });

  it('clears in-memory credentials on logout', () => {
    auth.provideCredentials('org@example.test', 'test-password');
    fixture.componentInstance.cerrarSesion();
    expect(auth.state()).toBe('signed-out');
    expect(auth.authorizationHeader()).toBeNull();
    http.expectNone('/api/auth/validate');
  });
});
