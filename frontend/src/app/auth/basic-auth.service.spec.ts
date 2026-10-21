import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from './basic-auth.service';

describe('BasicAuthService', () => {
  let auth: BasicAuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    auth = TestBed.inject(BasicAuthService);
    auth.logout();
  });

  it('keeps credentials pending until a protected request confirms them', () => {
    expect(auth.provideCredentials('user@example.test', 'test-password')).toBe(true);
    expect(auth.state()).toBe('pending');
    expect(auth.authorizationHeader()).toBe(`Basic ${btoa('user@example.test:test-password')}`);
    auth.markAuthenticated();
    expect(auth.state()).toBe('authenticated');
  });

  it('clears credentials when logging out or rejecting a 401', () => {
    auth.provideCredentials('user@example.test', 'test-password');
    auth.logout();
    expect(auth.state()).toBe('signed-out');
    expect(auth.username()).toBeNull();
    expect(auth.authorizationHeader()).toBeNull();

    auth.provideCredentials('user@example.test', 'test-password');
    auth.rejectCredentials();
    expect(auth.state()).toBe('invalid');
    expect(auth.username()).toBeNull();
    expect(auth.authorizationHeader()).toBeNull();
  });
});
