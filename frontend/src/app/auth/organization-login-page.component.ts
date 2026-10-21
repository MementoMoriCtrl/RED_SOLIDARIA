import { Component, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthenticatedProfile, BasicAuthService } from './basic-auth.service';

@Component({
  selector: 'app-organization-login-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './organization-login-page.component.html',
})
export class OrganizationLoginPageComponent {
  private readonly basicAuth = inject(BasicAuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly authState = this.basicAuth.state;
  basicAuthHasAdmin(): boolean { return this.basicAuth.hasPermission('ACCESO_ADMINISTRAR'); }
  correo = '';
  contrasena = '';
  error: string | null = null;

  continuar(): void {
    if (!this.basicAuth.provideCredentials(this.correo, this.contrasena)) {
      this.error = 'Ingresa el correo y la contraseña para continuar.';
      return;
    }
    this.contrasena = '';
    this.error = null;
    this.http.get<void>('/api/auth/validate').subscribe({
      next: () => this.http.get<AuthenticatedProfile>('/api/auth/me').subscribe({
        next: (profile) => {
          this.basicAuth.markAuthenticated(profile);
          if (this.basicAuth.hasPermission('ACCESO_ADMINISTRAR')) void this.router.navigateByUrl('/admin');
        },
        error: (response: HttpErrorResponse) => this.handleAuthError(response),
      }),
      error: (response: HttpErrorResponse) => this.handleAuthError(response),
    });
  }

  cerrarSesion(): void {
    this.basicAuth.logout();
    this.correo = '';
    this.contrasena = '';
    this.error = null;
  }

  private handleAuthError(response: HttpErrorResponse): void {
    if (response.status === 401) this.basicAuth.rejectCredentials();
    else if (response.status === 403) this.basicAuth.markForbidden();
    else this.basicAuth.markConnectionError();
  }
}
