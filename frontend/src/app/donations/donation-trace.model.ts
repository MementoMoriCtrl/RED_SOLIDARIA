export interface DonationTrace {
  codigoTrazabilidad: string;
  fechaRegistro: string;
  fechaIngreso: string | null;
  estado: string;
  centroAcopio: CollectionCenterSummary | null;
  detalles: DonationDetail[];
  movimientos: DonationMovement[];
}

export interface CollectionCenterSummary {
  nombre: string;
  ubicacion: string | null;
}

export interface DonationDetail {
  recursoNombre: string;
  cantidadDonada: number;
  cantidadRecibidaAcopio: number | null;
  unidadCodigo: string;
  unidadNombre: string;
  unidadSimbolo: string | null;
}

export interface DonationMovement {
  estadoAnterior: string | null;
  estadoNuevo: string;
  fechaHora: string;
  ubicacion: string | null;
}
