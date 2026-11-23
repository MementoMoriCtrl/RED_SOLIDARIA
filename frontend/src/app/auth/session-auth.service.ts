import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { CsrfService } from './csrf.service';

export type SessionAuthState = 'loading' | 'signed-out' | 'pending' | 'authenticated' | 'invalid' | 'forbidden' | 'csrf-error' | 'error' | 'server-error';
export interface AuthenticatedProfile {
  nombre: string; apellido: string; correo: string; rolCodigo: string; rolNombre: string; permisos: string[];
}

@Injectable({ providedIn: 'root' })
export class SessionAuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly currentState = signal<SessionAuthState>('loading');
  private readonly currentProfile = signal<AuthenticatedProfile | null>(null);
  private restoration: Observable<void> | null = null;
  readonly state = this.currentState.asReadonly();
  readonly profile = this.currentProfile.asReadonly();
  readonly username = () => this.currentProfile()?.correo ?? null;

  restore(): Observable<void> {
    if (this.currentState() !== 'loading') return of(undefined);
    if (!this.restoration) {
      this.restoration = this.http.get<AuthenticatedProfile>('/api/auth/me').pipe(
        tap(profile => this.markAuthenticated(profile)), map(() => undefined),
        catchError((error: HttpErrorResponse) => {
          this.currentProfile.set(null);
          this.currentState.set(error.status === 401 ? 'signed-out' : 'error');
          return of(undefined);
        }), shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.restoration;
  }
  initialize(): Promise<void> { return firstValueFrom(this.restore()); }
  login(email: string, password: string): Observable<void> {
    this.currentState.set('pending');
    const body = new URLSearchParams({ correo: email.trim(), password }).toString();
    return this.http.post<void>('/api/auth/login', body, {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    }).pipe(
      switchMap(() => { this.csrf.clear(); return this.csrf.get(); }),
      switchMap(() => this.http.get<AuthenticatedProfile>('/api/auth/me')),
      tap(profile => this.markAuthenticated(profile)), map(() => undefined),
      catchError((error: HttpErrorResponse) => {
        this.currentProfile.set(null);
        this.currentState.set(error.status === 401 ? 'invalid' : error.status === 403 && error.error?.code === 'CSRF_INVALID'
          ? 'csrf-error' : error.status === 403 ? 'forbidden' : error.status === 0 ? 'error' : 'server-error');
        return throwError(() => error);
      }),
    );
  }
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(
      switchMap(() => { this.clear(); this.csrf.clear(); return this.csrf.get(); }),
      map(() => undefined),
      catchError(error => { this.clear(); this.csrf.clear(); return throwError(() => error); }),
    );
  }
  markAuthenticated(profile: AuthenticatedProfile): void { this.currentProfile.set(profile); this.currentState.set('authenticated'); }
  hasPermission(permission: string): boolean { return this.currentProfile()?.permisos.includes(permission) ?? false; }
  markForbidden(): void { this.currentState.set('forbidden'); }
  rejectCredentials(): void { this.clear(); }
  clear(): void { this.currentProfile.set(null); this.currentState.set('signed-out'); }
}
