import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, shareReplay, tap } from 'rxjs';

export interface CsrfToken { headerName: string; token: string; }

@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private token: CsrfToken | null = null;
  private pending: Observable<CsrfToken> | null = null;

  get(): Observable<CsrfToken> {
    if (!this.pending) {
      this.pending = this.http.get<CsrfToken>('/api/auth/csrf').pipe(
        tap(value => { this.token = value; }),
        finalize(() => { this.pending = null; }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.pending;
  }
  current(): CsrfToken | null { return this.token; }
  clear(): void { this.token = null; }
}
