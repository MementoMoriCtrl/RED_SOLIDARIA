// Public allowlist; administrative notes and personal addresses belong to donation-admin.model.
export interface CustodyPlace { tipo: string; nombre: string; direccionInstitucional: string | null; }
export interface CustodyEvent {
  evento: string; fechaHora: string | null; estado: string; origen: CustodyPlace | null;
  destino: CustodyPlace | null; zona: string | null; cantidadEnviada: number | null; cantidadRecibida: number | null;
}
export interface PublicCustody {
  recepcionAcopioConfirmada?: boolean;
  ultimaActualizacion?: string | null;
  presentacion: string; emergencia: string | null;
  recursos: Array<{ recurso: string; unidad: string; estadoLogistico: string;
    ultimaCustodiaConfirmada: CustodyPlace | null; ultimaConfirmacion: string | null; ultimaActualizacion: string | null;
    posiciones: Array<{ estado: string; centro: CustodyPlace | null; zona: string | null; cantidad: number }>;
    movimientos: CustodyEvent[]; aclaracion: string;
    cantidades?: { declarada: number; recibidaConfirmada: number; enTransito: number;
      enDistribucion: number; disponibleDistribucion: number; asignada: number; entregada: number };
    asignaciones?: Array<{ zona: string; asignada: number; entregada: number }> }>;
}
