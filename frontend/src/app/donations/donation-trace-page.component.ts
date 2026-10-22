import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { DonationTrace } from './donation-trace.model';
import { DonationTraceService } from './donation-trace.service';
import { AppShellComponent } from '../shared/app-shell.component';

@Component({
  selector: 'app-donation-trace-page',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, AppShellComponent],
  templateUrl: './donation-trace-page.component.html',
})
export class DonationTracePageComponent {
  private readonly donationTrace = inject(DonationTraceService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  codigo = '';
  cargando = false;
  error: string | null = null;
  resultado: DonationTrace | null = null;

  consultar(): void {
    const code = this.codigo.trim();
    this.error = null;
    this.resultado = null;

    if (!code) {
      this.error = 'Ingresa un código de trazabilidad para continuar.';
      return;
    }

    this.cargando = true;
    this.donationTrace.findByTrackingCode(code)
      .pipe(finalize(() => {
        this.cargando = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: (trace) => this.resultado = trace,
        error: (response: HttpErrorResponse) => this.error = this.errorFor(response),
      });
  }

  private errorFor(response: HttpErrorResponse): string {
    if (response.status === 404) {
      return 'No encontramos una donación con ese código. Revísalo e inténtalo nuevamente.';
    }
    if (response.status === 401) {
      return 'El servicio público de trazabilidad rechazó la solicitud.';
    }
    if (response.status === 403) {
      return 'No es posible consultar esta donación en este momento.';
    }
    if (response.status === 0) {
      return 'No se pudo conectar con el backend. Comprueba que esté disponible e inténtalo nuevamente.';
    }
    return 'Ocurrió un error al consultar la donación. Inténtalo nuevamente más tarde.';
  }
}
