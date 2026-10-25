import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AffectedZone, CreateEmergencyRequest, EmergencyDetail, EmergencyPage, SaveAffectedZoneRequest, SaveEmergencyRequest } from './emergency.model';

@Injectable({ providedIn: 'root' })
export class EmergencyApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/admin/emergencias';

  list(text = '', state = '', page = 0, size = 20): Observable<EmergencyPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (text.trim()) params = params.set('texto', text.trim());
    if (state.trim()) params = params.set('estado', state.trim());
    return this.http.get<EmergencyPage>(this.endpoint, { params });
  }
  findById(id: number): Observable<EmergencyDetail> { return this.http.get<EmergencyDetail>(`${this.endpoint}/${id}`); }
  create(request: CreateEmergencyRequest): Observable<EmergencyDetail> { return this.http.post<EmergencyDetail>(this.endpoint, request); }
  update(id: number, request: SaveEmergencyRequest): Observable<EmergencyDetail> { return this.http.put<EmergencyDetail>(`${this.endpoint}/${id}`, request); }
  changeState(id: number, state: string): Observable<EmergencyDetail> { return this.http.patch<EmergencyDetail>(`${this.endpoint}/${id}/estado`, { estado: state }); }
  createZone(id: number, request: SaveAffectedZoneRequest): Observable<AffectedZone> { return this.http.post<AffectedZone>(`${this.endpoint}/${id}/zonas`, request); }
  updateZone(id: number, zoneId: number, request: SaveAffectedZoneRequest): Observable<AffectedZone> {
    return this.http.put<AffectedZone>(`${this.endpoint}/${id}/zonas/${zoneId}`, request);
  }
}
