export interface NeedItem {
  id: number; zonaId: number; recursoId: number; recursoNombre: string; unidad: string;
  prioridad: string; estado: string; requerida: number; asignada: number; entregada: number;
  pendienteAsignar: number; pendienteEntregar: number;
}
export interface DonationSource {
  detalleDonacionId: number; codigoTrazabilidad: string; estado: string;
  recibidaAcopio: number; asignadaDonacion: number; disponible: number;
  asignadaNecesidad: number; entregadaNecesidad: number; habilitada: boolean;
}
export interface ResultPage<T> { content: T[]; totalElements: number; totalPages: number; page: number; }
export interface CreateNeedRequest { recursoId: number; cantidad: number; prioridad: string; observaciones: string | null; }
export interface SaveDeliveryRequest { totalAsignado: number; totalEntregado: number; }
export interface DeliveryResult { entregaId: number | null; totalAsignado: number; totalEntregado: number; repetida: boolean; }
