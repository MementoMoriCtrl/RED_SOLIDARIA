import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AdministrativeDonationDetail } from './donation-admin.model';
import { DonationAdminService } from './donation-admin.service';
import { BasicAuthService } from '../auth/basic-auth.service';

@Component({
  selector: 'app-donation-administration-detail-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, AppShellComponent],
  templateUrl: './donation-administration-detail-page.component.html',
  styleUrl: './donation-administration-detail-page.component.css',
})
export class DonationAdministrationDetailPageComponent implements OnInit {
  private readonly api = inject(DonationAdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly auth = inject(BasicAuthService);

  donation: AdministrativeDonationDetail | null = null;
  loading = true;
  error: string | null = null;
  receiptError: string | null = null;
  receiptSuccess: string | null = null;
  savingReceipt = false;
  receiptLocation = '';
  receiptObservations = '';
  readonly receiptAmounts: Record<number, string> = {};

  get canReceive(): boolean {
    return this.auth.hasPermission('ACCESO_ADMINISTRAR') && this.donation?.estado === 'REGISTRADA'
      && !!this.donation.detalles.some(line => line.cantidadRecibidaAcopio < line.cantidadDonada);
  }

  setReceiptAmount(resourceId: number, value: string): void { this.receiptAmounts[resourceId] = value; }

  submitReceipt(): void {
    if (!this.donation || !this.canReceive || this.savingReceipt) return;
    const detalles = Object.entries(this.receiptAmounts)
      .filter(([, value]) => value.trim() !== '')
      .map(([recursoId, cantidad]) => ({ recursoId: Number(recursoId), cantidad: Number(cantidad) }));
    if (!detalles.length || detalles.some(line => !Number.isFinite(line.cantidad) || line.cantidad <= 0 || !/^\d+(\.\d{1,3})?$/.test(String(this.receiptAmounts[line.recursoId]).trim()))) {
      this.receiptError = 'Ingresa una cantidad positiva con hasta 3 decimales para al menos un recurso.';
      return;
    }
    this.receiptError = null;
    this.receiptSuccess = null;
    this.savingReceipt = true;
    this.api.registerReceipt(this.donation.id, { detalles, ubicacion: this.receiptLocation, observaciones: this.receiptObservations }).pipe(
      finalize(() => { this.savingReceipt = false; this.changeDetector.markForCheck(); }),
    ).subscribe({
      next: () => {
        this.receiptSuccess = 'La recepción se registró correctamente.';
        Object.keys(this.receiptAmounts).forEach(resourceId => this.receiptAmounts[Number(resourceId)] = '');
        this.loadDonation(this.donation!.id);
      },
      error: (response: HttpErrorResponse) => this.receiptError = this.receiptErrorMessage(response),
    });
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.loading = false;
      this.error = 'El identificador de la donación no es válido.';
      return;
    }
    this.loadDonation(id);
  }

  private loadDonation(id: number): void {
    this.api.findById(id).pipe(finalize(() => {
      this.loading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (donation) => this.donation = donation,
      error: (response: HttpErrorResponse) => this.error = this.errorMessage(response),
    });
  }

  private receiptErrorMessage(response: HttpErrorResponse): string {
    if (response.status === 0) return 'No se pudo conectar con el servidor. La recepción no fue confirmada.';
    if (response.status === 400) return response.error?.detail || 'Revisa las cantidades y los datos de la recepción.';
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para registrar la recepción.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para registrar recepciones.';
    if (response.status === 404) return 'No encontramos la donación solicitada.';
    if (response.status === 409) return response.error?.detail || 'La donación ya no admite recepciones.';
    return 'No se pudo registrar la recepción. Inténtalo nuevamente.';
  }

  private errorMessage(response: HttpErrorResponse): string {
    if (response.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    if (response.status === 400) return 'El identificador de la donación no es válido.';
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para consultar el detalle administrativo.';
    if (response.status === 404) return 'No encontramos la donación solicitada.';
    return 'No se pudo cargar el detalle de la donación.';
  }
}
