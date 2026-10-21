import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from './basic-auth.service';
import { administratorGuard, authenticatedGuard } from './auth.guards';

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
});
