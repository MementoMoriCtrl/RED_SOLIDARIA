import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SessionAuthService } from './session-auth.service';
import { sessionInterceptor } from './session.interceptor';

describe('SessionAuthService', () => {
  let auth: SessionAuthService; let http: HttpTestingController;
  const profile = { nombre: 'A', apellido: 'B', correo: 'a@test', rolCodigo: 'ADMIN', rolNombre: 'Admin', permisos: ['ACCESO_ADMINISTRAR'] };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting()] });
    auth = TestBed.inject(SessionAuthService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('restores a profile and permissions', () => {
    auth.restore().subscribe(); http.expectOne('/api/auth/me').flush(profile);
    expect(auth.state()).toBe('authenticated'); expect(auth.hasPermission('ACCESO_ADMINISTRAR')).toBe(true);
  });
  it('treats a 401 during restoration as anonymous', () => {
    auth.restore().subscribe(); http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.state()).toBe('signed-out');
  });
  it('keeps a network failure distinct from an expired session', () => {
    auth.restore().subscribe(); http.expectOne('/api/auth/me').error(new ProgressEvent('error'));
    expect(auth.state()).toBe('error');
  });
  it('logs in with a CSRF token and refreshes it before fetching the profile', () => {
    auth.login(' a@test ', 'secret').subscribe();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'before' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toBe('correo=a%40test&password=secret');
    expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('before');
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'after' });
    http.expectOne('/api/auth/me').flush(profile);
    expect(auth.profile()).toEqual(profile);
  });
  it('logs out on the server and obtains a new anonymous token', () => {
    auth.markAuthenticated(profile); auth.logout().subscribe();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'old' });
    const logout = http.expectOne('/api/auth/logout'); expect(logout.request.headers.get('X-CSRF-TOKEN')).toBe('old');
    logout.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'new' });
    expect(auth.state()).toBe('signed-out'); expect(auth.profile()).toBeNull();
  });
  it('clears the profile when a protected request reports an expired session', () => {
    auth.markAuthenticated(profile);
    TestBed.inject(HttpClient).get('/api/admin/usuarios').subscribe({ error: () => {} });
    http.expectOne('/api/admin/usuarios').flush({ code: 'SESSION_INVALIDATED' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.state()).toBe('signed-out'); expect(auth.profile()).toBeNull();
  });
});
