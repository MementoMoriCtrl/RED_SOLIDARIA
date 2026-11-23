import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { SessionAuthService } from './session-auth.service';

function guard(permission?: string): ReturnType<CanActivateFn> {
  const auth = inject(SessionAuthService);
  const router = inject(Router);
  return auth.restore().pipe(map(() => auth.state() === 'authenticated' && (!permission || auth.hasPermission(permission))
    ? true : router.createUrlTree(['/acceso-organizaciones'])));
}
export const authenticatedGuard: CanActivateFn = () => guard();
export const administratorGuard: CanActivateFn = () => guard('ACCESO_ADMINISTRAR');
export const donationRegistrationGuard: CanActivateFn = () => guard('DONACION_REGISTRAR');
