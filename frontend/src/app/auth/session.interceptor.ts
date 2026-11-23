import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, of, switchMap, throwError } from 'rxjs';
import { CsrfService, CsrfToken } from './csrf.service';
import { SessionAuthService } from './session-auth.service';

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const page = inject(DOCUMENT).location.href;
  let target: URL;
  try { target = new URL(request.url, page); } catch { return next(request); }
  if (target.origin !== new URL(page).origin || !target.pathname.startsWith('/api/')) return next(request);
  const csrf = inject(CsrfService);
  const auth = inject(SessionAuthService);
  const unsafe = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method.toUpperCase());
  const token = csrf.current();
  const token$: Observable<CsrfToken | null> = unsafe ? (token ? of(token) : csrf.get()) : of(null);
  return token$.pipe(
    switchMap(value => next(value ? request.clone({ setHeaders: { [value.headerName]: value.token } }) : request)),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401 && target.pathname !== '/api/auth/login') { auth.clear(); csrf.clear(); }
        if (error.status === 403 && error.error?.code === 'CSRF_INVALID') csrf.clear();
      }
      return throwError(() => error);
    }),
  );
};
