import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { EmergencyApiService } from '../emergencies/emergency-api.service';
import { ResourceApiService } from '../resources/resource-api.service';
import { ResourceItem } from '../resources/resource.model';
import { AppShellComponent } from '../shared/app-shell.component';
import { NeedApiService } from './need-api.service';
import { DonationSource, NeedItem, ResultPage } from './need.model';

@Component({
  selector: 'app-need-management-page', standalone: true,
  imports: [AppShellComponent, FormsModule, DecimalPipe, RouterLink],
  templateUrl: './need-management-page.component.html', styleUrl: './need-management-page.component.css',
})
export class NeedManagementPageComponent implements OnInit {
  private readonly api = inject(NeedApiService);
  private readonly resourcesApi = inject(ResourceApiService);
  private readonly emergencies = inject(EmergencyApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly emergencyId = Number(this.route.snapshot.paramMap.get('id'));
  readonly zoneId = Number(this.route.snapshot.paramMap.get('zoneId'));
  title = ''; resources: ResourceItem[] = [];
  needs: ResultPage<NeedItem> = { content: [], totalElements: 0, totalPages: 0, page: 0 };
  sources: ResultPage<DonationSource> = { content: [], totalElements: 0, totalPages: 0, page: 0 };
  selected: NeedItem | null = null; source: DonationSource | null = null;
  loading = true; loadingSources = false; saving = false;
  error: string | null = null; success: string | null = null;
  resourceId: number | null = null; quantity: number | null = null; priority = ''; observations = '';
  assigned: number | null = null; delivered: number | null = null;

  ngOnInit(): void {
    forkJoin({ resources: this.resourcesApi.resources(), emergency: this.emergencies.findById(this.emergencyId),
      needs: this.api.list(this.emergencyId, this.zoneId) })
      .pipe(finalize(() => { this.loading = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: ({ resources, emergency, needs }) => {
        this.resources = resources.filter(item => item.estado === 'ACTIVO'); this.needs = needs;
        this.title = `${emergency.nombre} / ${emergency.zonas.find(zone => zone.id === this.zoneId)?.nombre ?? ''}`;
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }

  load(page = 0): void {
    if (this.saving || this.loadingSources) return;
    this.loading = true; this.error = null; this.selected = null; this.source = null;
    this.api.list(this.emergencyId, this.zoneId, page)
      .pipe(finalize(() => { this.loading = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: result => { this.needs = result; }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }

  create(): void {
    if (this.saving || this.loading || this.loadingSources) return;
    if (!this.resourceId || !this.validQuantity(this.quantity, true) || !this.priority.trim()
        || this.priority.trim().length > 30 || new TextEncoder().encode(this.observations).length > 65535) {
      this.error = 'Selecciona un recurso y revisa la cantidad, prioridad y observaciones.'; return;
    }
    this.saving = true; this.error = null; this.success = null;
    this.api.create(this.emergencyId, this.zoneId, { recursoId: this.resourceId, cantidad: this.quantity!,
      prioridad: this.priority.trim(), observaciones: this.observations.trim() || null })
      .pipe(finalize(() => { this.saving = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: () => {
        this.quantity = null; this.observations = ''; this.success = 'Necesidad registrada.';
        this.saving = false; this.load(0);
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }

  select(need: NeedItem, page = 0): void {
    if (this.saving || this.loading || this.loadingSources) return;
    this.selected = need; this.source = null; this.error = null; this.loadingSources = true;
    this.sources = { content: [], totalElements: 0, totalPages: 0, page: 0 };
    this.api.sources(need.id, page)
      .pipe(finalize(() => { this.loadingSources = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: result => { this.sources = result; }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }

  selectSource(source: DonationSource): void {
    if (this.saving || this.loadingSources || !source.habilitada) return;
    this.source = source; this.assigned = source.asignadaNecesidad; this.delivered = source.entregadaNecesidad;
    this.error = null; this.success = null;
  }

  saveDelivery(): void {
    if (this.saving || this.loadingSources || !this.selected || !this.source) return;
    if (!this.validQuantity(this.assigned) || !this.validQuantity(this.delivered) || this.delivered! > this.assigned!
        || this.assigned! < this.source.asignadaNecesidad || this.delivered! < this.source.entregadaNecesidad) {
      this.error = 'Usa totales acumulados válidos: entregado no supera asignado y los totales no pueden disminuir.'; return;
    }
    this.saving = true; this.error = null; this.success = null;
    this.api.saveDelivery(this.selected.id, this.source.detalleDonacionId,
      { totalAsignado: this.assigned!, totalEntregado: this.delivered! })
      .pipe(finalize(() => { this.saving = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: result => {
        this.success = result.repetida ? 'Estos totales ya estaban registrados; no se duplicó la operación.' : 'Asignación y entrega registradas.';
        this.saving = false; this.load(this.needs.page);
      }, error: (response: HttpErrorResponse) => { this.error = this.message(response); } });
  }

  private validQuantity(value: number | null, positive = false): boolean {
    return value !== null && Number.isFinite(value) && value >= (positive ? 0.001 : 0)
      && value <= 99999999999.999 && /^\d+(\.\d{1,3})?$/.test(String(value));
  }
  private message(response: HttpErrorResponse): string {
    if (response.status === 401) return 'Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para esta operación.';
    if (response.status === 404) return 'No se encontró la emergencia, zona, necesidad o donación solicitada.';
    if (response.status === 409) return 'Los saldos o estados no admiten la operación. Recarga y revisa disponibilidad y totales.';
    if (response.status === 400) return 'Revisa los datos ingresados y los límites de las cantidades.';
    return 'No se pudo completar la operación. Revisa la conexión y vuelve a intentarlo.';
  }
}
