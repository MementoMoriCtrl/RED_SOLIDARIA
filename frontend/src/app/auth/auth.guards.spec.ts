import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from './basic-auth.service';
import { administratorGuard, authenticatedGuard, donationRegistrationGuard } from './auth.guards';
import { routes } from '../app.routes';

describe('authentication route guards', () => {
  let auth: BasicAuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    auth = TestBed.inject(BasicAuthService);
    router = TestBed.inject(Router);
    auth.logout();
  });

  it('redirects anonymous visitors from protected routes to organization access', () => {
    const result = TestBed.runInInjectionContext(() => authenticatedGuard({} as never, {} as never));
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/acceso-organizaciones');
  });

  it('allows resources for an authenticated account and denies admin without its permission', () => {
    auth.provideCredentials('org@example.test', 'test-password');
    auth.markAuthenticated({ nombre: 'Org', apellido: 'User', correo: 'org@example.test', rolCodigo: 'ORGANIZACION',
      rolNombre: 'Organización', permisos: ['DONACION_REGISTRAR'] });
    expect(TestBed.runInInjectionContext(() => authenticatedGuard({} as never, {} as never))).toBe(true);
    const result = TestBed.runInInjectionContext(() => administratorGuard({} as never, {} as never));
    expect(result).toBeInstanceOf(UrlTree);
  });
  it.each([
    { permissions: [], registration: false, catalogs: false },
    { permissions: ['DONACION_REGISTRAR'], registration: true, catalogs: false },
    { permissions: ['ACCESO_ADMINISTRAR'], registration: false, catalogs: true },
    { permissions: ['DONACION_REGISTRAR', 'ACCESO_ADMINISTRAR'], registration: true, catalogs: true },
  ])('preserves independent donation route permissions: $permissions', ({ permissions, registration, catalogs }) => {
    auth.provideCredentials('fixture@test', 'test-password');
    auth.markAuthenticated({ nombre: 'Test', apellido: 'Test', correo: 'fixture@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: permissions });
    for (const [path, guard, allowed, page] of [
      ['donaciones/registro', donationRegistrationGuard, registration, 'registration'],
      ['admin/catalogos-donaciones', administratorGuard, catalogs, 'catalogs'],
    ] as const) {
      const route = routes.find(item => item.path === path)!;
      expect(route.canActivate).toEqual([guard]);
      expect(route.data?.['donationPage']).toBe(page);
      const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
      if (allowed) expect(result).toBe(true);
      else expect(router.serializeUrl(result as UrlTree)).toBe('/acceso-organizaciones');
      auth.logout();
      const anonymous = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
      expect(router.serializeUrl(anonymous as UrlTree)).toBe('/acceso-organizaciones');
      auth.provideCredentials('fixture@test', 'test-password');
      auth.markAuthenticated({ nombre: 'Test', apellido: 'Test', correo: 'fixture@test',
        rolCodigo: 'TEST', rolNombre: 'Test', permisos: permissions });
    }
  });
});
