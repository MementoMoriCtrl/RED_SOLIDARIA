import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { EmergencyApiService } from './emergency-api.service';
import { EmergencyBalanceComponent } from './emergency-balance.component';
import { AffectedZone, CreateEmergencyRequest, EmergencyDetail, EmergencyListItem, SaveAffectedZoneRequest, SaveEmergencyRequest } from './emergency.model';

@Component({
  selector: 'app-emergency-management-page', standalone: true,
  imports: [AppShellComponent, FormsModule, DatePipe, RouterLink, EmergencyBalanceComponent],
  templateUrl: './emergency-management-page.component.html',
  styleUrl: './emergency-management-page.component.css',
})
export class EmergencyManagementPageComponent implements OnInit {
  private readonly api = inject(EmergencyApiService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  emergencies: EmergencyListItem[] = [];
  selected: EmergencyDetail | null = null;
  page = 0; totalPages = 0; totalElements = 0; last = true;
  loading = true; loadingDetail = false; saving = false;
  error: string | null = null; success: string | null = null;
  text = ''; stateFilter = '';
  formOpen = false; editingId: number | null = null;
  name = ''; type = ''; description = ''; startDate = ''; endDate = ''; createState = '';
  nextState = ''; zoneId: number | null = null; zoneName = ''; zoneLocation = ''; zoneDescription = '';

  ngOnInit(): void { this.load(); }

  load(page = 0): void {
    this.loading = true; this.error = null;
    this.api.list(this.text, this.stateFilter, page).pipe(finalize(() => {
      this.loading = false; this.changeDetector.markForCheck();
    })).subscribe({
      next: (result) => { this.emergencies = result.content; this.page = result.page;
        this.totalPages = result.totalPages; this.totalElements = result.totalElements; this.last = result.last; },
      error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); },
    });
  }

  newEmergency(): void {
    if (this.saving || this.loadingDetail) return;
    this.selected = null; this.nextState = ''; this.resetZoneForm();
    this.editingId = null; this.name = this.type = this.description = this.startDate = this.endDate = this.createState = '';
    this.formOpen = true; this.success = null; this.error = null;
  }

  editEmergency(item: EmergencyListItem): void {
    if (this.saving || this.loadingDetail) return;
    this.formOpen = false; this.selected = null; this.editingId = null; this.resetZoneForm();
    this.error = null; this.success = null; this.loadingDetail = true;
    this.api.findById(item.id).pipe(finalize(() => {
      this.loadingDetail = false; this.changeDetector.markForCheck();
    })).subscribe({
      next: (detail) => { this.selected = detail; this.editingId = detail.id; this.name = detail.nombre;
        this.type = detail.tipo; this.description = detail.descripcion ?? ''; this.startDate = detail.fechaInicio;
        this.endDate = detail.fechaFin ?? ''; this.nextState = detail.estado; this.formOpen = true; this.resetZoneForm(); },
      error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); },
    });
  }

  saveEmergency(): void {
    if (this.saving || this.loadingDetail) return;
    const validation = this.validateEmergencyForm();
    if (validation) { this.error = validation; return; }
    const payload: SaveEmergencyRequest = { nombre: this.name.trim(), tipo: this.type.trim(),
      descripcion: this.description.trim() || null, fechaInicio: this.startDate, fechaFin: this.endDate || null };
    this.saving = true; this.error = null;
    const request = this.editingId === null
      ? this.api.create({ ...payload, estado: this.createState.trim() })
      : this.api.update(this.editingId, payload);
    request.pipe(finalize(() => { this.saving = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: (detail) => { this.selected = detail; this.editingId = detail.id;
        this.name = detail.nombre; this.type = detail.tipo; this.description = detail.descripcion ?? '';
        this.startDate = detail.fechaInicio; this.endDate = detail.fechaFin ?? ''; this.nextState = detail.estado;
        this.success = 'Los datos de la emergencia se guardaron correctamente.'; this.load(this.page); },
        error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); } });
  }

  saveState(): void {
    if (this.saving || this.loadingDetail) return;
    if (!this.selected || !this.nextState.trim() || this.nextState.trim().length > 30) {
      this.error = 'El estado es obligatorio y admite hasta 30 caracteres.'; return;
    }
    this.saving = true; this.error = null;
    this.api.changeState(this.selected.id, this.nextState.trim())
      .pipe(finalize(() => { this.saving = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: (detail) => { this.selected = detail; this.success = 'El estado se actualizó correctamente.'; this.load(this.page); },
        error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); } });
  }

  editZone(zone: AffectedZone): void {
    if (this.saving || this.loadingDetail) return;
    this.zoneId = zone.id; this.zoneName = zone.nombre; this.zoneLocation = zone.ubicacion ?? ''; this.zoneDescription = zone.descripcion ?? '';
  }

  saveZone(): void {
    if (this.saving || this.loadingDetail) return;
    if (!this.selected || !this.zoneName.trim() || this.zoneName.trim().length > 150
        || this.zoneLocation.trim().length > 200 || new TextEncoder().encode(this.zoneDescription).length > 65535) {
      this.error = 'Revisa el nombre, la ubicación y la descripción de la zona afectada.'; return;
    }
    const payload: SaveAffectedZoneRequest = { nombre: this.zoneName.trim(),
      ubicacion: this.zoneLocation.trim() || null, descripcion: this.zoneDescription.trim() || null };
    this.saving = true; this.error = null;
    const request = this.zoneId === null ? this.api.createZone(this.selected.id, payload)
      : this.api.updateZone(this.selected.id, this.zoneId, payload);
    request.pipe(finalize(() => { this.saving = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: () => { this.success = 'La zona afectada se guardó correctamente.'; this.resetZoneForm(); this.refreshSelected(); },
        error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); } });
  }

  closeForm(): void {
    if (this.saving || this.loadingDetail) return;
    this.formOpen = false; this.editingId = null; this.selected = null; this.resetZoneForm();
  }
  get firstResult(): number { return this.totalElements === 0 ? 0 : this.page * 20 + 1; }
  get lastResult(): number { return Math.min((this.page + 1) * 20, this.totalElements); }

  private refreshSelected(): void {
    if (!this.selected) return;
    this.loadingDetail = true;
    this.api.findById(this.selected.id).pipe(finalize(() => { this.loadingDetail = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: (detail) => { this.selected = detail; }, error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); } });
  }
  private resetZoneForm(): void { this.zoneId = null; this.zoneName = this.zoneLocation = this.zoneDescription = ''; }
  private validateEmergencyForm(): string | null {
    if (!this.name.trim() || this.name.trim().length > 150 || !this.type.trim() || this.type.trim().length > 100 || !this.startDate)
      return 'Nombre, tipo y fecha de inicio son obligatorios.';
    if (this.endDate && this.endDate < this.startDate) return 'La fecha de fin debe ser igual o posterior a la fecha de inicio.';
    if (new TextEncoder().encode(this.description).length > 65535) return 'La descripción supera el límite permitido.';
    if (this.editingId === null && (!this.createState.trim() || this.createState.trim().length > 30))
      return 'Indica el estado usado por el sistema (máximo 30 caracteres).';
    return null;
  }
  private errorMessage(response: HttpErrorResponse): string {
    if (response.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    if (response.status === 400) return 'Revisa los datos y fechas ingresados.';
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para administrar emergencias.';
    if (response.status === 404) return 'La emergencia o zona solicitada ya no está disponible.';
    return 'No se pudo completar la operación de emergencias.';
  }
}
