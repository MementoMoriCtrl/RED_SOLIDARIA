import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditEventPage, AuditFilters } from './audit.model';

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/admin/auditoria';

  search(filters: AuditFilters = {}, page = 0, size = 20): Observable<AuditEventPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    const values: Record<string, string | undefined> = {
      accion: filters.accion, entidad: filters.entidad, idRegistro: filters.idRegistro,
      usuario: filters.usuario, desde: filters.desde, hasta: filters.hasta,
    };
    for (const [key, value] of Object.entries(values)) {
      if (value?.trim()) params = params.set(key, value.trim());
    }
    return this.http.get<AuditEventPage>(this.endpoint, { params });
  }
}
