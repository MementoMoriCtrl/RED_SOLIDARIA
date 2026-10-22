import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdministrativeDonationDetail, AdministrativeDonationFilters, AdministrativeDonationListItem } from './donation-admin.model';

@Injectable({ providedIn: 'root' })
export class DonationAdminService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/donaciones/administracion';

  list(filters: AdministrativeDonationFilters = {}): Observable<AdministrativeDonationListItem[]> {
    let params = new HttpParams();
    if (filters.codigo?.trim()) params = params.set('codigo', filters.codigo.trim());
    if (filters.estado?.trim()) params = params.set('estado', filters.estado.trim());
    return this.http.get<AdministrativeDonationListItem[]>(`${this.endpoint}/listado`, { params });
  }

  findById(id: number): Observable<AdministrativeDonationDetail> {
    return this.http.get<AdministrativeDonationDetail>(`${this.endpoint}/${id}`);
  }
}
