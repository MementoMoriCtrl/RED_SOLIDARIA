import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface DeliveryReportItem {
  id: number; fecha: string; emergencia: string; zona: string; necesidadId: number;
  recurso: string; unidad: string; codigoDonacion: string; cantidadAsignada: number; cantidadEntregada: number;
}
export interface DeliveryReportPage {
  content: DeliveryReportItem[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  deliveries(page: number, from: string, to: string) {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (from) params = params.set('fechaDesde', from);
    if (to) params = params.set('fechaHasta', to);
    return this.http.get<DeliveryReportPage>('/api/admin/reportes/entregas', { params });
  }
}
