export interface AffectedZone {
  id: number;
  nombre: string;
  ubicacion: string | null;
  descripcion: string | null;
  creadoEn: string | null;
}

export interface EmergencyListItem {
  id: number;
  nombre: string;
  tipo: string;
  fechaInicio: string;
  fechaFin: string | null;
  estado: string;
}

export interface EmergencyDetail extends EmergencyListItem {
  descripcion: string | null;
  creadoEn: string | null;
  actualizadoEn: string | null;
  zonas: AffectedZone[];
}

export interface EmergencyPage {
  content: EmergencyListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface SaveEmergencyRequest {
  nombre: string;
  tipo: string;
  descripcion: string | null;
  fechaInicio: string;
  fechaFin: string | null;
}

export interface CreateEmergencyRequest extends SaveEmergencyRequest { estado: string; }

export interface SaveAffectedZoneRequest {
  nombre: string;
  ubicacion: string | null;
  descripcion: string | null;
}
