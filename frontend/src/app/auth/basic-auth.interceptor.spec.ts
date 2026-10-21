import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from './basic-auth.service';
import { basicAuthInterceptor } from './basic-auth.interceptor';

describe('basicAuthInterceptor', () => {
  let http: HttpTestingController;
  let auth: BasicAuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(BasicAuthService);
    auth.provideCredentials('user@example.test', 'test-password');
  });

  afterEach(() => http.verify());

  it('does not send credentials with the public tracking lookup', () => {
    TestBed.inject(HttpClient).get('/api/donaciones/RS-TEST').subscribe();
    const request = http.expectOne('/api/donaciones/RS-TEST');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('adds Basic authorization to a protected local API request', () => {
    TestBed.inject(HttpClient).get('/api/admin/roles').subscribe();
    const request = http.expectOne('/api/admin/roles');
    expect(request.request.headers.get('Authorization')).toBe(`Basic ${btoa('user@example.test:test-password')}`);
    request.flush([]);
  });

  it('never sends credentials to an external domain', () => {
    TestBed.inject(HttpClient).get('https://example.com/api/donaciones/RS-TEST').subscribe();
    const request = http.expectOne('https://example.com/api/donaciones/RS-TEST');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
});
