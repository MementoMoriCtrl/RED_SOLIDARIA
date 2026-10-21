import { Component, inject } from '@angular/core';
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
  }

  cerrarSesion(): void {
    this.basicAuth.logout();
    this.correo = '';
    this.contrasena = '';
    this.error = null;
  }
}
