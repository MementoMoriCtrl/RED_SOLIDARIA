import { Injectable, signal } from '@angular/core';

export type BasicAuthState = 'signed-out' | 'pending' | 'authenticated' | 'invalid' | 'forbidden' | 'error';

interface BasicCredentials {
  username: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class BasicAuthService {
  private readonly credentials = signal<BasicCredentials | null>(null);
  private readonly currentState = signal<BasicAuthState>('signed-out');
  private readonly currentUsername = signal<string | null>(null);

  readonly state = this.currentState.asReadonly();
  readonly username = this.currentUsername.asReadonly();

  provideCredentials(username: string, password: string): boolean {
    const normalizedUsername = username.trim();
    if (!normalizedUsername || !password) return false;

    this.credentials.set({ username: normalizedUsername, password });
    this.currentUsername.set(normalizedUsername);
    this.currentState.set('pending');
    return true;
  }

  authorizationHeader(): string | null {
    const credentials = this.credentials();
    if (!credentials) return null;

    const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return `Basic ${btoa(binary)}`;
  }

  markAuthenticated(): void {
    if (this.credentials()) this.currentState.set('authenticated');
  }

  markForbidden(): void {
    if (this.credentials()) this.currentState.set('forbidden');
  }

  markConnectionError(): void {
    if (this.credentials()) this.currentState.set('error');
  }

  rejectCredentials(): void {
    this.credentials.set(null);
    this.currentUsername.set(null);
    this.currentState.set('invalid');
  }

  logout(): void {
    this.clear();
  }

  hasCredentials(): boolean {
    return this.credentials() !== null;
  }

  private clear(): void {
    this.credentials.set(null);
    this.currentUsername.set(null);
    this.currentState.set('signed-out');
  }
}
