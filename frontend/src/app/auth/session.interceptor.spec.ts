import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { sessionInterceptor } from './session.interceptor';
import { CsrfService } from './csrf.service';

describe('sessionInterceptor', () => {
  let http: HttpTestingController; let client: HttpClient;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController); client = TestBed.inject(HttpClient); });
  afterEach(() => http.verify());
  it('adds CSRF only to same-origin API writes and does not retry a rejected operation', () => {
    client.post('/api/admin/test', {}).subscribe({ error: () => {} });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
    const request = http.expectOne('/api/admin/test'); expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('test-token');
    request.flush({ code: 'CSRF_INVALID' }, { status: 403, statusText: 'Forbidden' });
    http.expectNone('/api/admin/test');
  });
  it('never sends the token to an external origin', () => {
    TestBed.inject(CsrfService).get().subscribe();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
    client.post('https://external.example/api/test', {}).subscribe();
    const request = http.expectOne('https://external.example/api/test');
    expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(false); request.flush({});
    http.expectNone('/api/auth/csrf');
  });
});
