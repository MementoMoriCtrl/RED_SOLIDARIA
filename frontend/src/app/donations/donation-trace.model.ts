export interface DonationTrace {
  trazabilidad?: import('./donation-custody.model').PublicCustody | null;
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
  recurso?: string | null;
  unidad?: string | null;
  cantidadEnviada?: number | null;
  cantidadRecibida?: number | null;
  origen?: import('./donation-custody.model').CustodyPlace | null;
  destino?: import('./donation-custody.model').CustodyPlace | null;
  zona?: string | null;
  descripcion?: string;
  estadoAnterior: string | null;
  estadoNuevo: string;
  fechaHora: string;
  ubicacion: string | null;
}
