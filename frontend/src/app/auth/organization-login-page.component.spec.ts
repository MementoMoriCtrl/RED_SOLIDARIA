import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { OrganizationLoginPageComponent } from './organization-login-page.component';
import { SessionAuthService } from './session-auth.service';
import { sessionInterceptor } from './session.interceptor';

describe('organization session login', () => {
  let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ imports: [OrganizationLoginPageComponent], providers: [
    provideRouter([]), provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting(),
  ] }); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('sends the form, fetches the fresh token and restores the profile', () => {
    const fixture = TestBed.createComponent(OrganizationLoginPageComponent);
    fixture.componentInstance.correo = 'org@example.test'; fixture.componentInstance.contrasena = 'secret';
    fixture.componentInstance.continuar();
    expect(fixture.componentInstance.contrasena).toBe('');
    http.expectOne('/api/auth/csrf').flush({ headerName:'X-CSRF-TOKEN', token:'one' });
    const login = http.expectOne('/api/auth/login'); expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('one');
    login.flush(null, { status:204, statusText:'No Content' });
    http.expectOne('/api/auth/csrf').flush({ headerName:'X-CSRF-TOKEN', token:'two' });
    http.expectOne('/api/auth/me').flush({ nombre:'Org', apellido:'Test', correo:'org@example.test', rolCodigo:'ORG', rolNombre:'Org', permisos:[] });
    expect(TestBed.inject(SessionAuthService).state()).toBe('authenticated');
  });
  it('shows invalid credentials after a 401 response', () => {
    const page = TestBed.createComponent(OrganizationLoginPageComponent).componentInstance;
    page.correo = 'org@example.test'; page.contrasena = 'wrong'; page.continuar();
    http.expectOne('/api/auth/csrf').flush({ headerName:'X-CSRF-TOKEN', token:'one' });
    http.expectOne('/api/auth/login').flush({}, { status:401, statusText:'Unauthorized' });
    expect(TestBed.inject(SessionAuthService).state()).toBe('invalid');
  });
});
