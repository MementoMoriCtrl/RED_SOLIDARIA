import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DonationTrace } from './donation-trace.model';

@Injectable({ providedIn: 'root' })
export class DonationTraceService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/donaciones';

  findByTrackingCode(code: string): Observable<DonationTrace> {
    const normalizedCode = code.trim();
    return this.http.get<DonationTrace>(
      `${this.endpoint}/${encodeURIComponent(normalizedCode)}`,
    );
  }
}
