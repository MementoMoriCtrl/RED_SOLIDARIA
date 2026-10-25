import { DecimalPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, Input, OnChanges } from '@angular/core';
import { finalize } from 'rxjs';

export interface ResourceBalance {
  recursoId: number; recursoNombre: string; unidadCodigo: string; requerida: number; recibidaAcopio: number;
  asignada: number; entregada: number; disponible: number; pendienteAsignar: number; pendienteEntregar: number; faltanteAbastecimiento: number;
}
@Component({
  selector: 'app-emergency-balance', standalone: true, imports: [DecimalPipe],
  template: `
    <section class="panel" aria-labelledby="balance-title"><h2 id="balance-title">Balance de la emergencia por recurso</h2>
      <p>Recibido en acopio no significa entregado a la población. Cada fila usa la unidad indicada; no hay suma entre recursos.</p>
      <button type="button" [disabled]="loading" (click)="load()">Recargar balance</button>
      @if (loading) { <p role="status">Cargando balance…</p> }
      @else if (error) { <p class="alert" role="alert">{{ error }}</p> }
      @else if (rows.length === 0) { <p role="status">No hay necesidades ni donaciones para esta emergencia.</p> }
      @else {
        <div style="overflow-x: auto"><table><thead><tr><th>Recurso</th><th>Unidad</th><th>Requerida</th><th>Recibida en acopio</th><th>Asignada</th><th>Entregada</th><th>Disponible</th><th>Sin asignar</th><th>Pendiente de entregar</th><th>Faltante de abastecimiento</th></tr></thead>
          <tbody>@for (row of rows; track row.recursoId) {
            <tr><td>{{ row.recursoNombre }}</td><td>{{ row.unidadCodigo }}</td><td>{{ row.requerida | number:'1.0-3' }}</td>
              <td>{{ row.recibidaAcopio | number:'1.0-3' }}</td><td>{{ row.asignada | number:'1.0-3' }}</td><td>{{ row.entregada | number:'1.0-3' }}</td>
              <td>{{ row.disponible | number:'1.0-3' }}</td><td>{{ row.pendienteAsignar | number:'1.0-3' }}</td><td>{{ row.pendienteEntregar | number:'1.0-3' }}</td><td>{{ row.faltanteAbastecimiento | number:'1.0-3' }}</td></tr>
          }</tbody></table></div>
        <p>Disponible excluye reservas y estados no habilitados. Faltante de abastecimiento = sin asignar − disponible, con mínimo cero. Las recepciones históricas se muestran incluso cuando su estado bloquea disponibilidad.</p>
      }
    </section>`,
  styles: ['.panel { margin-top: 1rem; } table { width: 100%; border-collapse: collapse; text-align: left; } th, td { padding: .6rem; border-bottom: 1px solid #cbd5e1; }'],
})
export class EmergencyBalanceComponent implements OnChanges {
  @Input({ required: true }) emergencyId!: number;
  private readonly http = inject(HttpClient);
  private readonly cd = inject(ChangeDetectorRef);
  private requestVersion = 0;
  loading = false; error: string | null = null; rows: ResourceBalance[] = [];
  ngOnChanges(): void { this.load(); }
  load(): void {
    const version = ++this.requestVersion;
    this.loading = true; this.error = null; this.rows = [];
    this.http.get<ResourceBalance[]>(`/api/admin/emergencias/${this.emergencyId}/balance`)
      .pipe(finalize(() => { if (version === this.requestVersion) { this.loading = false; this.cd.markForCheck(); } }))
      .subscribe({ next: rows => { if (version === this.requestVersion) this.rows = rows; },
        error: (response: HttpErrorResponse) => { if (version === this.requestVersion) this.error = response.status === 403
          ? 'Tu cuenta no tiene permiso para consultar el balance.' : 'No se pudo cargar el balance. Revisa la conexión e inténtalo de nuevo.'; } });
  }
}
