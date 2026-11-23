import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CreateNeedRequest, DeliveryResult, DonationSource, NeedItem, ResultPage, SaveDeliveryRequest, UpdateNeedRequest } from './need.model';

@Injectable({ providedIn: 'root' })
export class NeedApiService {
  private readonly http = inject(HttpClient);
  private zoneUrl(emergencyId: number, zoneId: number): string {
    return `/api/admin/emergencias/${emergencyId}/zonas/${zoneId}/necesidades`;
  }
  list(emergencyId: number, zoneId: number, page = 0) {
    return this.http.get<ResultPage<NeedItem>>(this.zoneUrl(emergencyId, zoneId), { params: { page } });
  }
  create(emergencyId: number, zoneId: number, request: CreateNeedRequest) {
    return this.http.post<NeedItem>(this.zoneUrl(emergencyId, zoneId), request);
  }
  update(emergencyId: number, zoneId: number, needId: number, request: UpdateNeedRequest) {
    return this.http.put<NeedItem>(`${this.zoneUrl(emergencyId, zoneId)}/${needId}`, request);
  }
  transition(emergencyId: number, zoneId: number, needId: number, action: 'cerrar' | 'cancelar', motivo: string) {
    return this.http.post<NeedItem>(`${this.zoneUrl(emergencyId, zoneId)}/${needId}/${action}`, { motivo });
  }
  sources(needId: number, page = 0) {
    return this.http.get<ResultPage<DonationSource>>(`/api/admin/necesidades/${needId}/donaciones`, { params: { page } });
  }
  saveDelivery(needId: number, sourceId: number, request: SaveDeliveryRequest) {
    return this.http.put<DeliveryResult>(`/api/admin/necesidades/${needId}/donaciones/${sourceId}/entrega`, request);
  }
}
