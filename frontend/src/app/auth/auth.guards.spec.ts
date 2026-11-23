import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { SessionAuthService } from './session-auth.service';
import { administratorGuard, authenticatedGuard, donationRegistrationGuard } from './auth.guards';

describe('session guards', () => {
  let auth: SessionAuthService; let router: Router;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(SessionAuthService); router = TestBed.inject(Router); });
  it('redirects anonymous users', async () => {
    auth.clear();
    const result = TestBed.runInInjectionContext(() => authenticatedGuard({} as never, {} as never));
    expect(router.serializeUrl(await firstValueFrom(result as Observable<UrlTree>))).toBe('/acceso-organizaciones');
  });
  it('waits for session restoration before deciding', async () => {
    const result = TestBed.runInInjectionContext(() => authenticatedGuard({} as never, {} as never));
    const decision = firstValueFrom(result as Observable<unknown>);
    TestBed.inject(HttpTestingController).expectOne('/api/auth/me').flush({ nombre:'A', apellido:'B', correo:'a@test', rolCodigo:'R', rolNombre:'R', permisos:[] });
    expect(await decision).toBe(true);
  });
  it('keeps administrative and registration permissions independent', async () => {
    auth.markAuthenticated({ nombre:'A', apellido:'B', correo:'a@test', rolCodigo:'R', rolNombre:'R', permisos:['DONACION_REGISTRAR'] });
    const registration = TestBed.runInInjectionContext(() => donationRegistrationGuard({} as never, {} as never));
    const admin = TestBed.runInInjectionContext(() => administratorGuard({} as never, {} as never));
    expect(await firstValueFrom(registration as Observable<unknown>)).toBe(true);
    expect(await firstValueFrom(admin as Observable<unknown>)).toBeInstanceOf(UrlTree);
  });
});
