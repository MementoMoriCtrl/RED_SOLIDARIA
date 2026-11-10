export interface AdministrativeDonationListItem {
  id: number;
  codigoTrazabilidad: string;
  fechaRegistro: string;
  estado: string;
  donante: { id: number; nombre: string };
  emergenciaId: number | null;
  emergenciaNombre: string | null;
  centroAcopio: { id: number; nombre: string; ubicacion: string | null } | null;
}

export interface AdministrativeDonationDetail {
  id: number;
  codigoTrazabilidad: string;
  fechaRegistro: string;
  fechaIngreso: string | null;
  estado: string;
  observaciones: string | null;
  donante: { id: number; nombre: string };
  emergenciaId: number | null;
  emergenciaNombre: string | null;
  centroAcopio: { id: number; nombre: string; ubicacion: string | null } | null;
  detalles: Array<{
    recursoId: number;
    recursoNombre: string;
    cantidadDonada: number;
    cantidadRecibidaAcopio: number;
    unidadCodigo: string;
    unidadNombre: string;
    unidadSimbolo: string | null;
  }>;
  movimientos: Array<{
    estadoAnterior: string | null;
    estadoNuevo: string;
    fechaHora: string;
    ubicacion: string | null;
    observaciones: string | null;
    responsableNombre: string | null;
  }>;
}

export interface RegisterDonationReceiptRequest {
  centroAcopioId?: number;
  operacionId: string;
  detalles: Array<{ recursoId: number; cantidad: number; examinada?: number; noAceptada?: number; excedente?: number; cerrar?: boolean }>;
  ubicacion: string;
  observaciones: string;
}

export interface AdministrativeDonationFilters {
  codigo?: string;
  estado?: string;
}

export interface CreateDonationRequest {
  donanteId: number; emergenciaId: number | null; centroAcopioId: number | null;
  ubicacion: string; observaciones: string; detalles: Array<{ recursoId: number; cantidad: number; examinada?: number; noAceptada?: number; excedente?: number; cerrar?: boolean }>;
}
export interface CatalogItem { id: number; nombre: string; ubicacion: string | null; }
export interface CatalogPage { content: CatalogItem[]; page: number; totalPages: number; }

export interface VerificationProgress {
  recursoId: number; recursoNombre: string; unidad: string; declarada: number; aceptada: number; examinada: number;
  noAceptada: number; excedentePendiente: number; excedenteAprobado: number; cerrada: boolean; diferencia: number; avance: string; puedeVerificar: boolean;
}
export interface ReceiptHistoryEntry {
  operacionId: string; decision: string | null; decisorId: number | null; fechaDecision: string | null; observacionesDecision: string | null;
  recepcion: { fechaHora: string; ubicacion: string; observaciones: string; responsableId: number | null; centroAcopioId: number | null;
    registradorId?: number | null; autoverificacionMaestro?: boolean | null;
    detalles: Array<{ recursoNombre: string; cantidadRecibidaOperacion: number; examinada?: number; noAceptada?: number; excedente?: number; cerrada?: boolean }> };
}
