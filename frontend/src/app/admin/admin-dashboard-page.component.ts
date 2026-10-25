import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthenticatedProfile, BasicAuthService } from '../auth/basic-auth.service';
import { AppShellComponent } from '../shared/app-shell.component';

@Component({
  selector: 'app-admin-dashboard-page',
  standalone: true,
  imports: [RouterLink, AppShellComponent],
  templateUrl: './admin-dashboard-page.component.html',
  styleUrl: './admin-dashboard-page.component.css',
})
export class AdminDashboardPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(BasicAuthService);
  private readonly router = inject(Router);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly profile = this.auth.profile;
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    this.http.get<AuthenticatedProfile>('/api/auth/me').pipe(finalize(() => {
      this.loading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (profile) => {
        this.auth.markAuthenticated(profile);
        if (!this.auth.hasPermission('ACCESO_ADMINISTRAR')) {
          this.error = 'Tu cuenta ya no tiene permiso para acceder al panel administrativo.';
          void this.router.navigateByUrl('/acceso-organizaciones');
        }
      },
      error: (response: HttpErrorResponse) => {
        if (response.status === 401) {
          this.auth.rejectCredentials();
          void this.router.navigateByUrl('/acceso-organizaciones');
        } else if (response.status === 403) {
          this.auth.markForbidden();
          this.error = 'Tu cuenta no tiene permiso para acceder al panel administrativo.';
        } else {
          this.error = response.status === 0
            ? 'No se pudo conectar con el servidor. Verifica tu conexión e inténtalo de nuevo.'
            : 'No se pudo verificar tu acceso al panel. Inténtalo de nuevo.';
        }
      },
    });
  }

}
