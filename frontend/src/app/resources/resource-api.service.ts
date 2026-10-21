import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangeResourceStateRequest, MeasurementUnit, ResourceItem, SaveResourceRequest } from './resource.model';

@Injectable({ providedIn: 'root' })
export class ResourceApiService {
  private readonly http = inject(HttpClient);
  private readonly resourcesUrl = '/api/recursos';

  resources(): Observable<ResourceItem[]> {
    return this.http.get<ResourceItem[]>(this.resourcesUrl);
  }

  measurementUnits(): Observable<MeasurementUnit[]> {
    return this.http.get<MeasurementUnit[]>('/api/unidades-medida');
  }

  create(request: SaveResourceRequest): Observable<ResourceItem> {
    return this.http.post<ResourceItem>(this.resourcesUrl, request);
  }

  update(id: number, request: SaveResourceRequest): Observable<ResourceItem> {
    return this.http.put<ResourceItem>(`${this.resourcesUrl}/${id}`, request);
  }

  changeState(id: number, request: ChangeResourceStateRequest): Observable<ResourceItem> {
    return this.http.patch<ResourceItem>(`${this.resourcesUrl}/${id}/estado`, request);
  }
}
