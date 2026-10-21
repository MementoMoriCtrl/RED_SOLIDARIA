import { Component, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BasicAuthService } from './basic-auth.service';

@Component({
  selector: 'app-organization-login-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './organization-login-page.component.html',
})
export class OrganizationLoginPageComponent {
  private readonly basicAuth = inject(BasicAuthService);
  private readonly http = inject(HttpClient);

  readonly authState = this.basicAuth.state;
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
    this.http.get('/api/auth/validate').subscribe({
      next: () => this.basicAuth.markAuthenticated(),
      error: (response: HttpErrorResponse) => {
        if (response.status === 401) {
          this.basicAuth.rejectCredentials();
        } else if (response.status === 403) {
          this.basicAuth.markForbidden();
        } else {
          this.basicAuth.markConnectionError();
        }
      },
    });
  }

  cerrarSesion(): void {
    this.basicAuth.logout();
    this.correo = '';
    this.contrasena = '';
    this.error = null;
  }
}
