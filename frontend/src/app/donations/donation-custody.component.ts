import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import { PublicCustody } from './donation-custody.model';

@Component({
  selector: 'app-donation-custody', standalone: true, imports: [DatePipe, DecimalPipe],
  template: `
    <section class="panel" aria-labelledby="custody-title">
      <p class="eyebrow">SEGUIMIENTO POR RECURSO</p><h2 id="custody-title">Ubicación y estado de tu donación</h2>
      <p>Estado general registrado: <strong class="custody-status" [class.status--blue]="['REGISTRADA', 'RECIBIDA', 'ALMACENADA', 'CLASIFICADA'].includes(donationState)" [class.status--purple]="donationState === 'EN_TRANSITO'" [class.status--green]="donationState === 'ENTREGADA'" [class.status--amber]="donationState === 'DEVUELTA' || donationState === 'PENDIENTE'" [class.status--red]="donationState === 'INCIDENCIA'">{{ donationState }}</strong>. Consulta debajo la situación real de cada recurso.</p>
      @if (data; as trace) {
        <p>{{ trace.presentacion }}</p>
        <p>Última actualización de la donación: {{ trace.ultimaActualizacion ? (trace.ultimaActualizacion | date:'dd/MM/yyyy HH:mm') : 'No disponible' }}</p>
        @if (trace.emergencia) { <p>Emergencia de destino: {{ trace.emergencia }}</p> }
        @for (resource of trace.recursos; track $index) {
          <article class="custody-resource">
            <div class="custody-heading"><h3>{{ resource.recurso }} <span>/ {{ resource.unidad }}</span></h3>
              <span class="custody-status" [class.status--purple]="resource.estadoLogistico === 'EN_TRANSITO'" [class.status--blue]="['EN_ACOPIO', 'EN_DISTRIBUCION', 'RECIBIDA', 'RECIBIDO', 'CUSTODIA_DISTRIBUIDA'].includes(resource.estadoLogistico)" [class.status--green]="resource.estadoLogistico === 'ENTREGADA'" [class.status--amber]="['ASIGNADA', 'ENTREGA_PARCIAL', 'PENDIENTE_DE_VERIFICACION'].includes(resource.estadoLogistico)" [class.status--red]="['RECIBIDO_CON_DIFERENCIA', 'SIN_CUSTODIA_CONFIRMADA', 'CUSTODIA_NO_DETERMINADA', 'RESERVA_HISTORICA_SIN_CUSTODIA_CONFIRMADA'].includes(resource.estadoLogistico)">{{ label(resource.estadoLogistico) }}</span></div>
            @if (resource.cantidades; as quantities) {
              <dl class="custody-quantities" aria-label="Cantidades por recurso">
                <div><dt>Declarada</dt><dd>{{ quantities.declarada | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>Recibida y confirmada</dt><dd>{{ quantities.recibidaConfirmada | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>En tránsito</dt><dd>{{ quantities.enTransito | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>En custodia de distribución</dt><dd>{{ quantities.enDistribucion | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>Sin asignar en distribución</dt><dd>{{ quantities.disponibleDistribucion | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>Asignada acumulada</dt><dd>{{ quantities.asignada | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
                <div><dt>Entrega final registrada</dt><dd>{{ quantities.entregada | number:'1.0-3' }} {{ resource.unidad }}</dd></div>
              </dl>
              <p class="field-help">Las cifras representan etapas distintas y no se suman como existencias nuevas. Las reservas permanecen en distribución hasta registrar la entrega.</p>
            }
            <p>Último punto de custodia confirmado: {{ resource.ultimaCustodiaConfirmada?.nombre || 'No determinado' }}
              @if (resource.ultimaCustodiaConfirmada?.direccionInstitucional) { · {{ resource.ultimaCustodiaConfirmada?.direccionInstitucional }} }
            </p>
            <p>Última confirmación: {{ resource.ultimaConfirmacion ? (resource.ultimaConfirmacion | date:'dd/MM/yyyy HH:mm') : 'Fecha no disponible' }}<br />
              Última actualización: {{ resource.ultimaActualizacion ? (resource.ultimaActualizacion | date:'dd/MM/yyyy HH:mm') : 'No disponible' }}</p>
            <ul>
              @for (point of resource.posiciones; track $index) {
                <li><span class="custody-status" [class.status--purple]="point.estado === 'EN_TRANSITO'" [class.status--blue]="['EN_ACOPIO', 'EN_DISTRIBUCION', 'RECIBIDA', 'RECIBIDO', 'CUSTODIA_DISTRIBUIDA'].includes(point.estado)" [class.status--green]="point.estado === 'ENTREGADA'" [class.status--amber]="['ASIGNADA', 'ENTREGA_PARCIAL', 'PENDIENTE_DE_VERIFICACION'].includes(point.estado)" [class.status--red]="['RECIBIDO_CON_DIFERENCIA', 'SIN_CUSTODIA_CONFIRMADA', 'CUSTODIA_NO_DETERMINADA', 'RESERVA_HISTORICA_SIN_CUSTODIA_CONFIRMADA'].includes(point.estado)">{{ label(point.estado) }}</span>: {{ point.cantidad | number:'1.0-3' }} {{ resource.unidad }}
                  @if (point.centro) { · {{ point.estado === 'EN_TRANSITO' ? 'Destino previsto: ' : '' }}{{ point.centro.nombre }}
                    @if (point.centro.direccionInstitucional) { · {{ point.centro.direccionInstitucional }} }
                  }
                  @if (point.zona) { · Zona: {{ point.zona }} }
                </li>
              } @empty { <li>Pendiente de verificación física o sin evidencia suficiente de custodia.</li> }
            </ul>
            @for (allocation of resource.asignaciones || []; track $index) {
              <p class="custody-allocation">Destino: <strong>{{ allocation.zona }}</strong> · Asignada {{ allocation.asignada | number:'1.0-3' }} {{ resource.unidad }}
                · Entrega registrada {{ allocation.entregada | number:'1.0-3' }} {{ resource.unidad }}.
                @if (allocation.asignada > allocation.entregada) { <span>Existe cantidad asignada pendiente de entrega física.</span> }
              </p>
            }
            <p class="field-help">{{ resource.aclaracion }}</p>
            <h4>Historial logístico de {{ resource.recurso }}</h4>
            <ol class="custody-timeline">
              @for (event of resource.movimientos; track $index) {
                <li><strong>{{ label(event.evento) }}</strong> · <span class="custody-status" [class.status--purple]="event.estado === 'EN_TRANSITO'" [class.status--blue]="['EN_ACOPIO', 'EN_DISTRIBUCION', 'RECIBIDA', 'RECIBIDO', 'CUSTODIA_DISTRIBUIDA'].includes(event.estado)" [class.status--green]="event.estado === 'ENTREGADA'" [class.status--amber]="['ASIGNADA', 'ENTREGA_PARCIAL', 'PENDIENTE_DE_VERIFICACION'].includes(event.estado)" [class.status--red]="['RECIBIDO_CON_DIFERENCIA', 'SIN_CUSTODIA_CONFIRMADA', 'CUSTODIA_NO_DETERMINADA', 'RESERVA_HISTORICA_SIN_CUSTODIA_CONFIRMADA'].includes(event.estado)">{{ label(event.estado) }}</span>
                  <p>{{ event.fechaHora ? (event.fechaHora | date:'dd/MM/yyyy HH:mm') : 'Fecha no disponible' }}</p>
                  @if (event.origen) { <p>Origen: {{ event.origen.nombre }} · {{ event.origen.direccionInstitucional }}</p> }
                  @if (event.destino) { <p>{{ event.evento === 'SALIDA_TRASLADO' ? 'Destino previsto' : 'Centro receptor' }}: {{ event.destino.nombre }} · {{ event.destino.direccionInstitucional }}</p> }
                  @if (event.zona) { <p>Zona de destino: {{ event.zona }}</p> }
                  @if (event.cantidadEnviada !== null) { <p>Enviada: {{ event.cantidadEnviada | number:'1.0-3' }} {{ resource.unidad }}</p> }
                  @if (event.cantidadRecibida !== null) { <p>{{ event.evento === 'ASIGNACION_RECURSOS' ? 'Asignada (reserva)' : event.evento === 'ENTREGA_POBLACION' ? 'Entregada a la población' : 'Recibida' }}: {{ event.cantidadRecibida | number:'1.0-3' }} {{ resource.unidad }}</p> }
                </li>
              } @empty { <li>No hay movimientos detallados disponibles. No se reconstruyen recepciones históricas.</li> }
            </ol>
          </article>
        }
      } @else { <p role="status">La consulta no incluyó el detalle logístico. No podemos confirmar la ubicación con esta respuesta. Vuelve a consultar más tarde.</p> }
    </section>`,
  styles: [`:host { display:block; min-width:0; } .panel { overflow-wrap:anywhere; }
    .custody-resource { border:1px solid var(--border, #dbe2e8); border-radius:1rem; padding:1.25rem; margin-top:1.25rem; min-width:0; }
    .custody-heading { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:.75rem; }
    h3 { margin:0; } h3 span { font-weight:400; color:var(--muted); }
    .custody-status { display:inline-block; background:var(--status-blue-bg); color:var(--status-blue-dark); border-radius:1.5rem; padding:.4rem .75rem; font-size:.8rem; font-weight:600; }
    .custody-quantities { display:grid; grid-template-columns:repeat(auto-fit,minmax(145px,1fr)); gap:.6rem; margin:1rem 0; }
    .custody-quantities div { background:#f6f8fa; border-radius:.6rem; padding:.75rem; min-width:0; }
    dt { font-size:.78rem; color:var(--muted); line-height:1.5; } dd { margin:.4rem 0 0; font-weight:600; }
    .custody-allocation { background:#f5f7fb; border-left:3px solid #6f89ac; padding:.8rem; }
    .custody-timeline { border-left:2px solid #aac9bc; padding-left:1.4rem; list-style:none; }
    .custody-timeline li { position:relative; margin-bottom:1rem; padding:.25rem 0 .5rem; }
    .custody-timeline li::before { content:''; position:absolute; left:-1.8rem; top:.5rem; width:.65rem; height:.65rem; background:#39775b; border:3px solid white; border-radius:50%; }
    p { line-height:1.6; } li p { margin:.3rem 0; }
    @media(max-width:600px) { .custody-resource { padding:.85rem; } .custody-timeline { padding-left:1rem; }
      .custody-timeline li::before { left:-1.4rem; } .custody-quantities { grid-template-columns:repeat(2,minmax(0,1fr)); } }`],
})
export class DonationCustodyComponent {
  @Input() data: PublicCustody | null | undefined;
  @Input() donationState = '';
  label(value: string): string {
    const labels: Record<string,string> = {
      SALIDA_TRASLADO: 'Salida de traslado', RECEPCION_ACOPIO: 'Recepción física en acopio',
      RECEPCION_DISTRIBUCION: 'Recepción en distribución', ASIGNACION_RECURSOS: 'Asignación a una necesidad',
      ENTREGA_POBLACION: 'Entrega confirmada a la población', EN_TRANSITO: 'En tránsito',
      EN_ACOPIO: 'En acopio', EN_DISTRIBUCION: 'En distribución', ENTREGADA: 'Entrega confirmada',
      ASIGNADA: 'Asignada, pendiente de entrega', RECIBIDA: 'Recibida', RECIBIDO: 'Recibido',
      ENTREGA_PARCIAL: 'Entrega parcial registrada',
      RECIBIDO_CON_DIFERENCIA: 'Recibido con diferencia', CUSTODIA_DISTRIBUIDA: 'Cantidades en varios puntos o etapas',
      PENDIENTE_DE_VERIFICACION: 'Pendiente de verificación física', SIN_CUSTODIA_CONFIRMADA: 'Sin custodia actual confirmada',
      CUSTODIA_NO_DETERMINADA: 'Recepción registrada sin centro confirmado',
      RESERVA_HISTORICA_SIN_CUSTODIA_CONFIRMADA: 'Reserva histórica sin ubicación confirmada',
    };
    return labels[value] ?? value;
  }
}
