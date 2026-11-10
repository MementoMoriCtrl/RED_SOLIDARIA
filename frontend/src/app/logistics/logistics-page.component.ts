import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { BasicAuthService } from '../auth/basic-auth.service';
import { AppShellComponent } from '../shared/app-shell.component';
import { DispatchRequest, DistributionCenter, LogisticsApiService, LogisticsStock, ReceiveRequest, Transfer } from './logistics-api.service';
type Pending = { kind: 'dispatch'; request: DispatchRequest } | { kind: 'receive'; id: string; request: ReceiveRequest };
@Component({
  selector: 'app-logistics-page', standalone: true,
  imports: [AppShellComponent, FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './logistics-page.component.html', styleUrl: '../needs/need-management-page.component.css',
})
export class LogisticsPageComponent implements OnInit {
  private readonly api = inject(LogisticsApiService);
  private readonly auth = inject(BasicAuthService);
  private readonly cd = inject(ChangeDetectorRef);
  centers: DistributionCenter[] = []; stock: LogisticsStock[] = []; transfers: Transfer[] = [];
  loading = false; saving = false; error: string | null = null; success: string | null = null;
  stockPage = 0; historyPage = 0;
  source: LogisticsStock | null = null; destination: number | null = null; quantity: number | null = null; notes = '';
  selectedTransfer: Transfer | null = null; received: number | null = null; receiptNotes = '';
  pending: Pending | null = null;
  private get storageKey() { return `red-solidaria.logistics.${this.auth.profile()?.correo}`; }
  ngOnInit() {
    try { this.pending = JSON.parse(sessionStorage.getItem(this.storageKey) ?? 'null'); }
    catch { this.error = 'No se pudo recuperar la operación pendiente. Verifica el historial antes de registrar otra.'; }
    this.load();
  }
  load() {
    if (this.saving) return;
    this.loading = true;
    forkJoin({ centers: this.api.centers(), stock: this.api.stock(this.stockPage), history: this.api.history(this.historyPage) })
      .pipe(finalize(() => { this.loading = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => { this.centers = result.centers; this.stock = result.stock; this.transfers = result.history; },
        error: (error: HttpErrorResponse) => this.error = this.message(error) });
  }
  selectSource(source: LogisticsStock) { this.source = source; this.quantity = null; this.notes = ''; this.selectedTransfer = null; }
  selectTransfer(transfer: Transfer) { this.selectedTransfer = transfer; this.received = transfer.cantidadEnviada; this.receiptNotes = ''; this.source = null; }
  dispatch() {
    if (this.pending || this.saving || !this.source || !this.destination || !this.valid(this.quantity, true)) return;
    this.prepare({ kind: 'dispatch', request: { operacionId: crypto.randomUUID(), detalleDonacionId: this.source.detalleDonacionId,
      centroDistribucionId: this.destination, cantidad: this.quantity!, observaciones: this.notes.trim() } });
  }
  receive() {
    if (this.pending || this.saving || !this.selectedTransfer || !this.valid(this.received)) return;
    if (this.received! > this.selectedTransfer.cantidadEnviada ||
        (this.received !== this.selectedTransfer.cantidadEnviada && !this.receiptNotes.trim())) {
      this.error = 'La recepción no puede superar lo enviado. Documenta cualquier diferencia.'; return;
    }
    this.prepare({ kind: 'receive', id: this.selectedTransfer.operacionId,
      request: { operacionId: crypto.randomUUID(), cantidad: this.received!, observaciones: this.receiptNotes.trim() } });
  }
  private prepare(pending: Pending) {
    try { sessionStorage.setItem(this.storageKey, JSON.stringify(pending)); }
    catch { this.error = 'Habilita el almacenamiento de sesión para conservar el UUID antes de enviar.'; return; }
    this.pending = pending; this.retry();
  }
  retry() {
    if (!this.pending || this.saving) return;
    const pending = this.pending;
    this.saving = true; this.error = this.success = null;
    const call = pending.kind === 'dispatch' ? this.api.dispatch(pending.request) : this.api.receive(pending.id, pending.request);
    call.pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); })).subscribe({
      next: () => { sessionStorage.removeItem(this.storageKey); this.pending = null; this.source = null; this.selectedTransfer = null;
        this.success = 'Operación confirmada. Existencias actualizadas.'; this.saving = false; this.load(); },
      error: (error: HttpErrorResponse) => {
        if (error.status === 400 || error.status === 404 || error.status === 409) { sessionStorage.removeItem(this.storageKey); this.pending = null; }
        this.error = this.message(error);
      },
    });
  }
  private valid(value: number | null, positive = false) {
    return value !== null && Number.isFinite(value) && value >= (positive ? .001 : 0)
      && value <= 99999999999.999 && /^\d+(\.\d{1,3})?$/.test(String(value));
  }
  private message(error: HttpErrorResponse) {
    if (error.status === 403) return 'Tu cuenta no tiene permiso para gestionar logística.';
    if (error.status === 401) return 'Inicia sesión para continuar.';
    if (error.status === 409) return 'El saldo, estado o UUID no admite esta operación. Revisa el historial antes de reintentar.';
    if (error.status === 400) return 'Revisa cantidades, campos obligatorios y límites de texto.';
    return 'No se confirmó la operación. Conserva el UUID pendiente y reintenta con los mismos datos.';
  }
}
