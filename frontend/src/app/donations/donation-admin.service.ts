import { VerificationProgress, ReceiptHistoryEntry } from './donation-admin.model';
import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdministrativeDonationDetail, AdministrativeDonationFilters, AdministrativeDonationListItem, RegisterDonationReceiptRequest } from './donation-admin.model';
import { CreateDonationRequest, CatalogDetail, CatalogItem, CatalogPage } from './donation-admin.model';
import { MeasurementUnit } from '../resources/resource.model';

@Injectable({ providedIn: 'root' })
export class DonationAdminService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/donaciones/administracion';

  list(filters: AdministrativeDonationFilters = {}): Observable<AdministrativeDonationListItem[]> {
    let params = new HttpParams();
    if (filters.codigo?.trim()) params = params.set('codigo', filters.codigo.trim());
    if (filters.estado?.trim()) params = params.set('estado', filters.estado.trim());
    if (filters.fechaDesde) params = params.set('fechaDesde', filters.fechaDesde);
    if (filters.fechaHasta) params = params.set('fechaHasta', filters.fechaHasta);
    return this.http.get<AdministrativeDonationListItem[]>(`${this.endpoint}/listado`, { params });
  }

  findById(id: number): Observable<AdministrativeDonationDetail> {
    return this.http.get<AdministrativeDonationDetail>(`${this.endpoint}/${id}`);
  }

  receiptHistory(id: number, page = 0) {
    return this.http.get<ReceiptHistoryEntry[]>(`${this.endpoint}/${id}/recepciones`, { params: { page } });
  }
  verification(id: number) { return this.http.get<VerificationProgress[]>(`${this.endpoint}/${id}/verificacion`); }
  decideSurplus(id: number, uuid: string, request: { operacionId: string; aprobar: boolean; observaciones: string }) {
    return this.http.post(`${this.endpoint}/${id}/recepciones/${uuid}/excedente`, request);
  }
  registerReceipt(id: number, request: RegisterDonationReceiptRequest): Observable<unknown> {
    return this.http.post(`${this.endpoint}/${id}/recepciones`, request);
  }
  register(request: CreateDonationRequest) {
    return this.http.post<{ id: number; codigoTrazabilidad: string }>('/api/donaciones', request);
  }
  catalog(kind: string, text = '', page = 0) {
    return this.http.get<CatalogPage>(`/api/donaciones/catalogos/${kind}`, { params: { texto: text, page } });
  }
  createCatalog(kind: string, request: { nombre: string; ubicacion: string; descripcion: string }) {
    return this.http.post<CatalogItem>(`/api/admin/catalogos-donaciones/${kind}`, request);
  }
  catalogDetail(kind: 'donantes' | 'centros', id: number) {
    return this.http.get<CatalogDetail>(`/api/admin/catalogos-donaciones/${kind}/${id}`);
  }
  updateCatalog(kind: 'donantes' | 'centros', id: number, request: { nombre: string; ubicacion: string | null; descripcion: string | null }) {
    return this.http.put<CatalogDetail>(`/api/admin/catalogos-donaciones/${kind}/${id}`, request);
  }
  createUnit(request: { codigo: string; nombre: string; simbolo: string }) {
    return this.http.post('/api/admin/catalogos-donaciones/unidades', request);
  }
  updateUnit(id: number, request: { nombre: string; simbolo: string | null }) {
    return this.http.put<MeasurementUnit>(`/api/admin/catalogos-donaciones/unidades/${id}`, request);
  }
}
