import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { BasicAuthService } from './basic-auth.service';

export const authenticatedGuard: CanActivateFn = () => {
  const auth = inject(BasicAuthService);
  return auth.state() === 'authenticated' && auth.hasCredentials()
    ? true
    : inject(Router).createUrlTree(['/acceso-organizaciones']);
};

export const administratorGuard: CanActivateFn = () => {
  const auth = inject(BasicAuthService);
  return auth.state() === 'authenticated' && auth.hasCredentials() && auth.hasPermission('ACCESO_ADMINISTRAR')
    ? true
    : inject(Router).createUrlTree(['/acceso-organizaciones']);
};
