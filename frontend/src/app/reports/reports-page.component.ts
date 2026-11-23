import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { DeliveryReportPage, ReportsService } from './reports.service';

@Component({
  selector: 'app-reports-page', standalone: true,
  imports: [AppShellComponent, RouterLink, FormsModule, DatePipe, DecimalPipe],
  template: `
  <app-shell breadcrumb="Administración / Reportes" footerText="Reportes">
    <section class="intro"><h1>Reportes administrativos</h1><p>Consulta los datos operativos registrados.</p></section>
    <section class="panel"><h2>Consultas disponibles</h2>
      <ul><li><a routerLink="/admin/donaciones">Donaciones</a>: fecha de registro, estado y destino.</li>
      <li><a routerLink="/admin/logistica">Inventario actual</a>: existencias por lote y ubicación.</li>
      <li><a href="#reporte-entregas">Entregas</a>: eventos de entrega a la población.</li>
      <li><a routerLink="/admin/emergencias">Emergencias</a>: fechas, estado y balance por recurso.</li></ul>
    </section>
    <section class="panel" id="reporte-entregas"><h2>Reporte de entregas</h2>
      <p>Cada fila corresponde a un evento con cantidad entregada; las cantidades son incrementos, no totales acumulados.</p>
      <form (ngSubmit)="load(0)"><label>Desde <input type="date" name="from" [(ngModel)]="from" /></label>
      <label>Hasta <input type="date" name="to" [(ngModel)]="to" /></label>
      <button type="submit" [disabled]="loading">Buscar</button></form>
      @if (error) { <p role="alert">{{ error }}</p> }
      @if (loading) { <p role="status">Cargando entregas…</p> }
      @else if (!error && result.content.length === 0) { <p role="status">No hay entregas para los filtros seleccionados.</p> }
      @else if (!error) { <div style="overflow-x:auto"><table><thead><tr><th>Fecha</th><th>Emergencia / zona</th><th>Necesidad</th><th>Donación</th><th>Recurso</th><th>Asignada en evento</th><th>Entregada en evento</th></tr></thead>
        <tbody>@for (item of result.content; track item.id) { <tr><td>{{ item.fecha | date:'dd/MM/yyyy HH:mm' }}</td>
        <td>{{ item.emergencia }} / {{ item.zona }}</td><td>{{ item.necesidadId }}</td><td>{{ item.codigoDonacion }}</td>
        <td>{{ item.recurso }} ({{ item.unidad }})</td><td>{{ item.cantidadAsignada | number:'1.0-3' }}</td><td>{{ item.cantidadEntregada | number:'1.0-3' }}</td></tr> }</tbody></table></div> }
      <nav aria-label="Páginas de entregas"><button type="button" [disabled]="loading || result.page === 0" (click)="load(result.page - 1)">Anterior</button>
      <span> Página {{ result.page + 1 }} de {{ result.totalPages || 1 }} · {{ result.totalElements }} entregas </span>
      <button type="button" [disabled]="loading || result.last" (click)="load(result.page + 1)">Siguiente</button></nav>
    </section>
  </app-shell>`,
})
export class ReportsPageComponent implements OnInit {
  private readonly api = inject(ReportsService);
  private readonly cd = inject(ChangeDetectorRef);
  from = ''; to = ''; loading = false; error: string | null = null;
  result: DeliveryReportPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true };
  ngOnInit(): void { this.load(); }
  load(page = 0): void {
    if (this.loading) return;
    if (this.from && this.to && this.from > this.to) { this.error = 'La fecha inicial no puede ser posterior a la final.'; return; }
    this.loading = true; this.error = null;
    this.api.deliveries(page, this.from, this.to).pipe(finalize(() => { this.loading = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => { this.result = result; }, error: (response: HttpErrorResponse) => {
        this.error = response.status === 401 ? 'Inicia sesión para consultar los reportes.'
          : response.status === 403 ? 'Tu cuenta no tiene permiso para consultar los reportes.'
          : response.status === 400 ? 'Revisa las fechas seleccionadas.' : 'No se pudieron cargar las entregas.';
      } });
  }
}
