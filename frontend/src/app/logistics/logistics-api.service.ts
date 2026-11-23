import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
export interface DistributionCenter { id: number; nombre: string; ubicacion: string | null; descripcion: string | null; }
export interface LogisticsStock {
  detalleDonacionId: number; codigoTrazabilidad: string; centroAcopioId: number | null; centroAcopio: string | null;
  recurso: string; unidad: string; recibidaAcopio: number; trasladada: number; recibidaDistribucion: number;
  asignada: number; disponibleAcopio: number; disponibleDistribucion: number; enTransito: number;
  diferencia: number; reservaHistoricaSinDistribucion: number;
  saldoBrutoAcopio?: number; saldoBrutoDistribucion?: number; centroDistribucion?: string | null;
}
export interface Transfer {
  operacionId: string; detalleDonacionId: number; origen: string; destino: string; recurso: string; unidad: string;
  cantidadEnviada: number; cantidadRecibida: number | null; estado: string; fechaSalida: string; fechaRecepcion: string | null;
  responsableSalida: number; responsableRecepcion: number | null; observaciones: string | null; observacionesRecepcion: string | null;
}
export interface DispatchRequest { operacionId: string; detalleDonacionId: number; centroDistribucionId: number; cantidad: number; observaciones: string; }
export interface ReceiveRequest { operacionId: string; cantidad: number; observaciones: string; }
export interface ResolutionRequest { operacionId: string; tipo: string; motivo: string; evidencia: string; }
export interface TransferResolution { operacionId: string; trasladoId: string; actorId: number; fecha: string; tipo: string;
  motivo: string; evidencia: string | null; cantidadEnviada: number; cantidadRecibida: number; diferencia: number; }
@Injectable({ providedIn: 'root' })
export class LogisticsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/admin/logistica';
  centers() { return this.http.get<DistributionCenter[]>(`${this.base}/centros-distribucion`); }
  saveCenter(id: number | null, request: { nombre: string; ubicacion: string; descripcion: string }) {
    return id === null ? this.http.post<void>(`${this.base}/centros-distribucion`, request)
      : this.http.put<void>(`${this.base}/centros-distribucion/${id}`, request);
  }
  stock(page = 0) { return this.http.get<LogisticsStock[]>(`${this.base}/existencias`, { params: { page } }); }
  history(page = 0) { return this.http.get<Transfer[]>(`${this.base}/traslados`, { params: { page } }); }
  dispatch(request: DispatchRequest) { return this.http.post<Transfer>(`${this.base}/traslados`, request); }
  receive(id: string, request: ReceiveRequest) { return this.http.post<Transfer>(`${this.base}/traslados/${id}/recepcion`, request); }
  resolutions(page = 0) { return this.http.get<TransferResolution[]>(`${this.base}/resoluciones`, { params: { page } }); }
  resolve(id: string, request: ResolutionRequest) { return this.http.post<TransferResolution>(`${this.base}/traslados/${id}/resolucion`, request); }
}
