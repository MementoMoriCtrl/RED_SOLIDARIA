import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { DistributionCenter, LogisticsApiService } from './logistics-api.service';
@Component({
  selector: 'app-distribution-centers', standalone: true, imports: [FormsModule],
  styleUrl: '../needs/need-management-page.component.css',
  template: `
    <section class="panel"><h2>Centros de distribución</h2>
      <p>Reciben traslados de los centros de acopio y concentran recursos para atender necesidades. Registra aquí el centro principal.</p>
      @if (error) { <p class="alert" role="alert">{{ error }}</p> }
      @if (success) { <p role="status">{{ success }}</p> }
      <button type="button" [disabled]="loading || saving" (click)="load()">Recargar</button>
      @if (loading) { <p role="status">Cargando centros…</p> }
      @else { <ul>@for (center of centers; track center.id) { <li><strong>{{ center.nombre }}</strong> · {{ center.ubicacion }}
        <p>{{ center.descripcion }}</p><button type="button" [disabled]="saving" (click)="edit(center)">Editar centro</button></li>
      } @empty { <li>Todavía no hay centros de distribución registrados.</li> }</ul> }
      <h3>{{ editingId ? 'Editar centro de distribución' : 'Registrar centro de distribución' }}</h3>
      <form #form="ngForm" (ngSubmit)="save()"><fieldset [disabled]="saving || loading || unconfirmed">
        <label>Nombre *<input name="distributionName" [(ngModel)]="name" maxlength="150" required /></label>
        <label>Ubicación<input name="distributionLocation" [(ngModel)]="location" maxlength="200" /></label>
        <label>Descripción<textarea name="distributionDescription" [(ngModel)]="description" maxlength="2000"></textarea></label>
        <button type="submit" class="primary-button" [disabled]="form.invalid">Guardar centro</button>
        @if (editingId) { <button type="button" (click)="reset()">Cancelar edición</button> }
      </fieldset></form>
    </section>`,
})
export class DistributionCentersComponent implements OnInit {
  private readonly api = inject(LogisticsApiService);
  private readonly cd = inject(ChangeDetectorRef);
  centers: DistributionCenter[] = []; editingId: number | null = null;
  name = ''; location = ''; description = ''; loading = false; saving = false; unconfirmed = false;
  error: string | null = null; success: string | null = null;
  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    this.api.centers().pipe(finalize(() => { this.loading = false; this.cd.markForCheck(); }))
      .subscribe({ next: centers => { this.centers = centers; }, error: () => this.error = 'No se pudieron consultar los centros.' });
  }
  edit(center: DistributionCenter) {
    this.editingId = center.id; this.name = center.nombre; this.location = center.ubicacion ?? ''; this.description = center.descripcion ?? '';
  }
  reset() { this.editingId = null; this.name = this.location = this.description = ''; }
  save() {
    if (this.saving || this.loading || this.unconfirmed || !this.name.trim()) return;
    this.saving = true; this.error = this.success = null;
    this.api.saveCenter(this.editingId, { nombre: this.name.trim(), ubicacion: this.location.trim(), descripcion: this.description.trim() })
      .pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: () => { this.success = 'Centro de distribución guardado.'; this.reset(); this.load(); },
        error: (error: HttpErrorResponse) => {
          this.unconfirmed = this.editingId === null && (error.status === 0 || error.status >= 500);
          this.error = this.unconfirmed ? 'El alta no fue confirmada. Verifica el listado antes de registrar otro centro.' : 'No se pudo guardar. Revisa los campos y tus permisos.';
        } });
  }
}
