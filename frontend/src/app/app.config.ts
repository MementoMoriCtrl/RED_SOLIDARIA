import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { sessionInterceptor } from './auth/session.interceptor';
import { SessionAuthService } from './auth/session-auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([sessionInterceptor])),
    provideAppInitializer(() => inject(SessionAuthService).initialize())
  ]
};
