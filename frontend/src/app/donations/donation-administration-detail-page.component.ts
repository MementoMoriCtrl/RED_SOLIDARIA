import { VerificationProgress, ReceiptHistoryEntry } from './donation-admin.model';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AdministrativeDonationDetail, RegisterDonationReceiptRequest } from './donation-admin.model';
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
  receipts: ReceiptHistoryEntry[] = [];
  verification: VerificationProgress[] = []; verificationLoaded = false; loadingVerification = false;
  examinedAmounts: Record<number,string> = {}; rejectedAmounts: Record<number,string> = {}; surplusAmounts: Record<number,string> = {};
  closing: Record<number,boolean> = {};
  decisionNotes: Record<string,string> = {};
  pendingDecision: { uuid: string; request: { operacionId: string; aprobar: boolean; observaciones: string } } | null = null;
  loadVerification(): void {
    if (!this.donation || this.loadingVerification) return;
    this.loadingVerification = true;
    this.api.verification(this.donation.id).pipe(finalize(() => { this.loadingVerification = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: rows => { this.verification = rows; this.verificationLoaded = true; }, error: () => this.receiptError = 'No se pudo consultar la verificación.' });
    this.loadReceiptHistory();
  }
  get independentVerifier(): boolean { return this.verification.length > 0 && this.verification.every(row => row.puedeVerificar); }
  get isMasterAdministrator(): boolean { return this.auth.hasPermission('ADMIN_MAESTRO'); }
  verificationBalance(row: VerificationProgress): number {
    return row.cerrada ? 0 : Math.max(0, Math.round((row.declarada - row.aceptada) * 1000) / 1000);
  }
  private hasReceiptInput(resourceId: number): boolean {
    return !!this.closing[resourceId] || [this.examinedAmounts, this.receiptAmounts, this.rejectedAmounts, this.surplusAmounts]
      .some(amounts => (amounts[resourceId] ?? '').trim() !== '');
  }
  hasSurplus(entry: ReceiptHistoryEntry): boolean { return !entry.decision && entry.recepcion.detalles.some(line => (line.excedente ?? 0) > 0); }
  decide(entry: ReceiptHistoryEntry, approve: boolean): void {
    if (this.savingReceipt || this.pendingDecision || !this.donation || !this.auth.hasPermission('ACCESO_ADMINISTRAR')) return;
    const notes = this.decisionNotes[entry.operacionId]?.trim();
    if (!notes) { this.receiptError = 'Justifica la decisión del excedente.'; return; }
    if (notes.length > 2000) { this.receiptError = 'La justificación no puede superar 2000 caracteres.'; return; }
    const pending = { uuid: entry.operacionId, request: { operacionId: crypto.randomUUID(), aprobar: approve, observaciones: notes } };
    try { sessionStorage.setItem(this.receiptStorageKey + '.excedente', JSON.stringify(pending)); }
    catch { this.receiptError = 'No se pudo conservar la decisión antes de enviarla.'; return; }
    this.pendingDecision = pending; this.retryDecision();
  }
  retryDecision(): void {
    if (!this.pendingDecision || !this.donation || this.savingReceipt) return;
    this.savingReceipt = true; const pending = this.pendingDecision;
    this.api.decideSurplus(this.donation.id, pending.uuid, pending.request).pipe(finalize(() => { this.savingReceipt = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: () => { sessionStorage.removeItem(this.receiptStorageKey + '.excedente'); this.pendingDecision = null;
          this.receiptSuccess = 'Decisión confirmada. Solo el excedente aprobado genera existencias.'; this.loadDonation(this.donation!.id); this.loadVerification(); },
        error: (error: HttpErrorResponse) => { if ([400,404,409].includes(error.status)) { sessionStorage.removeItem(this.receiptStorageKey + '.excedente'); this.pendingDecision = null; }
          this.receiptError = this.operationErrorMessage(error, 'decisión de excedente'); } });
  }

  receiptPage = 0; loadingHistory = false;
  loadReceiptHistory(page = 0): void {
    if (!this.donation || this.loadingHistory) return;
    this.loadingHistory = true; this.receiptPage = page;
    this.api.receiptHistory(this.donation.id, page).pipe(finalize(() => { this.loadingHistory = false; this.changeDetector.markForCheck(); }))
      .subscribe({ next: history => this.receipts = history, error: () => this.receiptError = 'No se pudo consultar el historial de recepciones.' });
  }
  receiptCenterId: number | null = null;
  receiptLocation = '';
  receiptObservations = '';
  readonly receiptAmounts: Record<number, string> = {};
  pendingReceipt: RegisterDonationReceiptRequest | null = null;

  private get receiptStorageKey(): string {
    return `red-solidaria.receipt.${this.auth.profile()?.correo}.${this.route.snapshot.paramMap.get('id')}`;
  }

  get canReceive(): boolean {
    return this.auth.hasPermission('ACCESO_ADMINISTRAR') && (!!this.pendingReceipt || (this.donation?.estado === 'REGISTRADA' && (!this.verificationLoaded || this.verification.some(row => !row.cerrada))
      && !!this.donation.detalles.some(line => line.cantidadRecibidaAcopio < line.cantidadDonada)));
  }

  setReceiptAmount(resourceId: number, value: string): void {
    if (!this.savingReceipt && !this.pendingReceipt) this.receiptAmounts[resourceId] = value;
  }

  submitReceipt(): void {
    if (!this.donation || !this.canReceive || this.savingReceipt) return;
    if (!this.pendingReceipt && this.verificationLoaded && !this.independentVerifier) {
      this.receiptError = 'El verificador debe ser distinto del registrador.'; return;
    }
    const detalles: RegisterDonationReceiptRequest['detalles'] = this.verificationLoaded
      ? this.verification.filter(row => !row.cerrada && this.hasReceiptInput(row.recursoId))
        .map(row => ({ recursoId: row.recursoId, cantidad: Number(this.receiptAmounts[row.recursoId] || 0),
          examinada: Number(this.examinedAmounts[row.recursoId] || 0), noAceptada: Number(this.rejectedAmounts[row.recursoId] || 0),
          excedente: Number(this.surplusAmounts[row.recursoId] || 0), cerrar: !!this.closing[row.recursoId] }))
      : Object.entries(this.receiptAmounts)
      .filter(([, value]) => value.trim() !== '')
      .map(([recursoId, cantidad]) => ({ recursoId: Number(recursoId), cantidad: Number(cantidad) }));
    if (!this.pendingReceipt && !this.verificationLoaded && (!detalles.length || detalles.some(line => !Number.isFinite(line.cantidad) || line.cantidad <= 0 || !/^\d+(\.\d{1,3})?$/.test(String(this.receiptAmounts[line.recursoId]).trim())))) {
      this.receiptError = 'Ingresa una cantidad positiva con hasta 3 decimales para al menos un recurso.';
      return;
    }
    if (!this.pendingReceipt && this.verificationLoaded && (!this.independentVerifier || !detalles.length || detalles.some(line => {
      const values = [line.cantidad, line.examinada!, line.noAceptada!, line.excedente!];
      const rawValues = [this.receiptAmounts, this.examinedAmounts, this.rejectedAmounts, this.surplusAmounts]
        .map(amounts => (amounts[line.recursoId] ?? '').trim());
      return rawValues.some(value => value !== '' && !/^\d+(\.\d{1,3})?$/.test(value))
        || values.some(value => !Number.isFinite(value) || value < 0 || value > 99999999999.999)
        || Math.round(line.examinada! * 1000) !== Math.round(line.cantidad * 1000) + Math.round(line.noAceptada! * 1000) + Math.round(line.excedente! * 1000)
        || (line.examinada === 0 && !line.cerrar);
    }))) { this.receiptError = 'Revisa la verificación: examinada = aceptada + no aceptada + excedente; el verificador debe ser distinto.'; return; }
    this.receiptError = null;
    if (!this.pendingReceipt && this.verificationLoaded) {
      for (const line of detalles) {
        const row = this.verification.find(item => item.recursoId === line.recursoId)!;
        const accepted = Math.round(line.cantidad * 1000);
        const surplus = Math.round(line.excedente! * 1000);
        const remaining = Math.max(0, Math.round(row.declarada * 1000) - Math.round(row.aceptada * 1000));
        if (accepted !== Math.min(accepted + surplus, remaining)) {
          this.receiptError = 'La cantidad aceptada supera lo declarado pendiente o el excedente está mal separado. Acepta dentro del saldo y solicita autorización para el excedente.'; return;
        }
        if ((line.noAceptada! > 0 || surplus > 0 || (line.cerrar && accepted !== remaining)) && !this.receiptObservations.trim()) {
          this.receiptError = 'Justifica en observaciones el material no aceptado, los excedentes y los cierres con diferencia.'; return;
        }
      }
    }
    if (!this.pendingReceipt && (detalles.some(line => {
      const stored = this.donation!.detalles.find(item => item.recursoId === line.recursoId);
      return !stored || (!this.verificationLoaded && line.cantidad > stored.cantidadDonada - stored.cantidadRecibidaAcopio);
    }) || this.receiptLocation.length > 200 || new TextEncoder().encode(this.receiptObservations).length > 65535)) {
      this.receiptError = 'La cantidad supera lo pendiente o los datos exceden el límite permitido.'; return;
    }
    if (!this.pendingReceipt && !this.donation.centroAcopio && (!this.receiptCenterId || !Number.isSafeInteger(this.receiptCenterId))) {
      this.receiptError = 'Indica el ID del centro de acopio donde se recibe físicamente la donación.'; return;
    }
    this.receiptSuccess = null;
    const request = this.pendingReceipt ?? { ...(!this.donation.centroAcopio ? { centroAcopioId: this.receiptCenterId! } : {}), operacionId: crypto.randomUUID(), detalles,
      ubicacion: this.receiptLocation, observaciones: this.receiptObservations };
    try { sessionStorage.setItem(this.receiptStorageKey, JSON.stringify(request)); }
    catch { this.receiptError = 'No se pudo conservar el identificador de recepción. Habilita el almacenamiento de sesión antes de enviar.'; return; }
    this.pendingReceipt = request;
    this.savingReceipt = true;
    this.api.registerReceipt(this.donation.id, request).pipe(
      finalize(() => { this.savingReceipt = false; this.changeDetector.markForCheck(); }),
    ).subscribe({
      next: () => {
        sessionStorage.removeItem(this.receiptStorageKey); this.pendingReceipt = null;
        this.receiptSuccess = 'La recepción se registró correctamente.';
        Object.keys(this.receiptAmounts).forEach(resourceId => this.receiptAmounts[Number(resourceId)] = '');
        this.examinedAmounts = {}; this.rejectedAmounts = {}; this.surplusAmounts = {}; this.closing = {};
        this.loadDonation(this.donation!.id);
        if (this.verificationLoaded) this.loadVerification();
      },
      error: (response: HttpErrorResponse) => {
        if ([400,404,409].includes(response.status)) { sessionStorage.removeItem(this.receiptStorageKey); this.pendingReceipt = null; }
        this.receiptError = this.receiptErrorMessage(response);
      },
    });
  }

  ngOnInit(): void {
    try { this.pendingDecision = JSON.parse(sessionStorage.getItem(this.receiptStorageKey + '.excedente') ?? 'null'); }
    catch { this.receiptError = 'No se pudo recuperar la decisión pendiente.'; }
    try { this.pendingReceipt = JSON.parse(sessionStorage.getItem(this.receiptStorageKey) ?? 'null'); }
    catch { this.receiptError = 'No se pudo recuperar la recepción pendiente.'; }
    if (this.pendingReceipt) {
      this.pendingReceipt.detalles.forEach(line => { this.receiptAmounts[line.recursoId] = String(line.cantidad);
        if (line.examinada !== undefined) this.examinedAmounts[line.recursoId] = String(line.examinada);
        this.rejectedAmounts[line.recursoId] = String(line.noAceptada ?? 0); this.surplusAmounts[line.recursoId] = String(line.excedente ?? 0);
        this.closing[line.recursoId] = !!line.cerrar;
      });
      this.receiptCenterId = this.pendingReceipt.centroAcopioId ?? null;
      this.receiptLocation = this.pendingReceipt.ubicacion; this.receiptObservations = this.pendingReceipt.observaciones;
    }
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
    return this.operationErrorMessage(response, 'recepción');
  }

  private operationErrorMessage(response: HttpErrorResponse, operation: string): string {
    if (response.status === 400) return `Datos inválidos (400): ${response.error?.detail || 'Revisa cantidades y justificaciones.'}`;
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para continuar. Se conserva la solicitud pendiente.';
    if (response.status === 403) return `Sin permiso (403) para esta ${operation}. Se conserva la solicitud pendiente; comprueba tu acceso antes de reintentar.`;
    if (response.status === 404) return 'No encontramos la donación o recepción solicitada (404).';
    if (response.status === 409) return `Conflicto (409): ${response.error?.detail || 'El estado, saldo o UUID ya no admite esta operación. Consulta el historial.'}`;
    return `Respuesta incierta: no se confirmó la ${operation}. Se conservan UUID y contenido; reintenta la misma solicitud.`;
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
