import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AdministrativeDonationListItem } from './donation-admin.model';
import { DonationAdminService } from './donation-admin.service';

@Component({
  selector: 'app-donation-administration-page',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, AppShellComponent],
  templateUrl: './donation-administration-page.component.html',
  styleUrl: './donation-administration-page.component.css',
})
export class DonationAdministrationPageComponent implements OnInit {
  private readonly api = inject(DonationAdminService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  donations: AdministrativeDonationListItem[] = [];
  codigo = '';
  estado = '';
  loading = true;
  error: string | null = null;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = null;
    this.api.list({ codigo: this.codigo, estado: this.estado }).pipe(finalize(() => {
      this.loading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (donations) => this.donations = donations,
      error: (response: HttpErrorResponse) => this.error = this.errorMessage(response),
    });
  }

  private errorMessage(response: HttpErrorResponse): string {
    if (response.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    if (response.status === 400) return 'Revisa los filtros enviados e inténtalo de nuevo.';
    if (response.status === 401) return 'Tu sesión no está validada. Inicia sesión para continuar.';
    if (response.status === 403) return 'Tu cuenta no tiene permiso para consultar las donaciones administrativas.';
    return 'No se pudo cargar el listado de donaciones.';
  }
}
