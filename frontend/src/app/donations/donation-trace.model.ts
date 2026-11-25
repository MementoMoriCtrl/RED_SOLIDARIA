export interface DonationTrace {
  codigoTrazabilidad: string;
  fechaRegistro: string;
  emergencia: string | null;
  recursos: string[];
  estadoActual: string;
  explicacionEstado: string;
  hitos: PublicTraceMilestone[];
}

export interface PublicTraceMilestone {
  estado: string;
  explicacion: string;
  fechaHora: string | null;
}
