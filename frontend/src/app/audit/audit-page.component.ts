import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AuditApiService } from './audit-api.service';
import { AuditEvent, AuditEventPage } from './audit.model';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [AppShellComponent, FormsModule, DatePipe],
  templateUrl: './audit-page.component.html',
  styleUrl: './audit-page.component.css',
})
export class AuditPageComponent implements OnInit {
  private readonly api = inject(AuditApiService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  events: AuditEvent[] = [];
  page = 0;
  size = 20;
  totalElements = 0;
  totalPages = 0;
  last = true;
  loading = true;
  error: string | null = null;
  action = '';
  entity = '';
  recordId = '';
  user = '';
  fromDate = '';
  toDate = '';

  ngOnInit(): void { this.load(0); }

  load(page = this.page): void {
    this.page = page;
    this.loading = true;
    this.error = null;
    this.api.search({ accion: this.action, entidad: this.entity, idRegistro: this.recordId, usuario: this.user,
      desde: this.fromDate ? `${this.fromDate}T00:00:00` : undefined,
      hasta: this.toDate ? `${this.toDate}T23:59:59.999999999` : undefined }, page, this.size)
      .pipe(finalize(() => { this.loading = false; this.changeDetector.markForCheck(); }))
      .subscribe({
        next: (result: AuditEventPage) => {
          this.events = result.content;
          this.page = result.page;
          this.size = result.size;
          this.totalElements = result.totalElements;
          this.totalPages = result.totalPages;
          this.last = result.last;
        },
        error: (response: HttpErrorResponse) => { this.error = this.errorMessage(response); },
      });
  }

  get firstResult(): number { return this.totalElements === 0 ? 0 : this.page * this.size + 1; }
  get lastResult(): number { return Math.min((this.page + 1) * this.size, this.totalElements); }

  private errorMessage(response: HttpErrorResponse): string {
    if (response.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    if (response.status === 400) return 'Revisa los filtros y el rango de fechas.';
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para consultar la auditoría.';
    return 'No se pudo cargar el registro de auditoría.';
  }
}
