import { DOCUMENT } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { BasicAuthService } from './basic-auth.service';

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);
const BACKEND_ORIGINS = new Set([
  'http://localhost:8080',
  'http://127.0.0.1:8080',
  'http://[::1]:8080',
]);

export const basicAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(BasicAuthService);
  const header = auth.authorizationHeader();
  const pageUrl = inject(DOCUMENT).location.href;
  if (!header || isPublicDonationTraceRead(request.method, request.url, pageUrl)
      || !isLocalDonationApiRequest(request.url, pageUrl)) {
    return next(request);
  }

  return next(request.clone({ setHeaders: { Authorization: header } }));
};

function isPublicDonationTraceRead(method: string, requestUrl: string, pageUrl: string): boolean {
  try {
    const path = new URL(requestUrl, pageUrl).pathname.split('/').filter(Boolean);
    return method === 'GET' && path.length === 3 && path[0] === 'api' && path[1] === 'donaciones';
  } catch {
    return false;
  }
}

function isLocalDonationApiRequest(requestUrl: string, pageUrl: string): boolean {
  try {
    const request = new URL(requestUrl, pageUrl);
    const isApiPath = request.pathname.startsWith('/api/');
    if (!isApiPath) return false;

    if (requestUrl.startsWith('/api/')) {
      return LOOPBACK_HOSTS.has(new URL(pageUrl).hostname);
    }

    return request.protocol === 'http:' && BACKEND_ORIGINS.has(request.origin);
  } catch {
    return false;
  }
}
