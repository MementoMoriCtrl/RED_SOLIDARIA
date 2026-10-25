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
  operacionId: string;
  detalles: Array<{ recursoId: number; cantidad: number }>;
  ubicacion: string;
  observaciones: string;
}

export interface AdministrativeDonationFilters {
  codigo?: string;
  estado?: string;
}

export interface CreateDonationRequest {
  donanteId: number; emergenciaId: number | null; centroAcopioId: number | null;
  ubicacion: string; observaciones: string; detalles: Array<{ recursoId: number; cantidad: number }>;
}
export interface CatalogItem { id: number; nombre: string; ubicacion: string | null; }
export interface CatalogPage { content: CatalogItem[]; page: number; totalPages: number; }
