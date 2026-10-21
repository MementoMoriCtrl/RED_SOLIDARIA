export interface MeasurementUnit {
  id: number;
  codigo: string;
  nombre: string;
  simbolo: string | null;
}

export interface ResourceItem {
  id: number;
  nombre: string;
  categoria: string;
  descripcion: string | null;
  estado: string;
  unidadMedida: MeasurementUnit;
}

export interface SaveResourceRequest {
  nombre: string;
  categoria: string;
  descripcion: string | null;
  unidadMedidaId: number;
}

export interface ChangeResourceStateRequest {
  estado: 'ACTIVO' | 'INACTIVO';
}
