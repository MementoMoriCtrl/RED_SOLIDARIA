import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AdministrativeDonationDetail } from './donation-admin.model';
import { DonationAdminService } from './donation-admin.service';

@Component({
  selector: 'app-donation-administration-detail-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, AppShellComponent],
  templateUrl: './donation-administration-detail-page.component.html',
  styleUrl: './donation-administration-page.component.css',
})
export class DonationAdministrationDetailPageComponent implements OnInit {
  private readonly api = inject(DonationAdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);

  donation: AdministrativeDonationDetail | null = null;
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.loading = false;
      this.error = 'El identificador de la donación no es válido.';
      return;
    }
    this.api.findById(id).pipe(finalize(() => {
      this.loading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (donation) => this.donation = donation,
      error: (response: HttpErrorResponse) => this.error = this.errorMessage(response),
    });
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
