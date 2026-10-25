export interface AuditEvent {
  id: number;
  fechaHora: string;
  accion: string;
  entidad: string;
  idRegistro: string;
  responsableNombre: string;
}

export interface AuditEventPage {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AuditFilters {
  accion?: string;
  entidad?: string;
  idRegistro?: string;
  usuario?: string;
  desde?: string;
  hasta?: string;
}
