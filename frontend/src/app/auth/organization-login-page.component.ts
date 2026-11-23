import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionAuthService } from './session-auth.service';
import { AppShellComponent } from '../shared/app-shell.component';

@Component({ selector: 'app-organization-login-page', standalone: true, imports: [FormsModule, AppShellComponent],
  templateUrl: './organization-login-page.component.html' })
export class OrganizationLoginPageComponent {
  private readonly auth = inject(SessionAuthService);
  private readonly router = inject(Router);
  readonly authState = this.auth.state;
  hasAdmin(): boolean { return this.auth.hasPermission('ACCESO_ADMINISTRAR'); }
  correo = '';
  contrasena = '';
  error: string | null = null;

  continuar(): void {
    if (!this.correo.trim() || !this.contrasena) { this.error = 'Ingresa el correo y la contraseña para continuar.'; return; }
    const password = this.contrasena;
    this.contrasena = '';
    this.error = null;
    this.auth.login(this.correo, password).subscribe({
      next: () => { if (this.hasAdmin()) void this.router.navigateByUrl('/admin'); },
      error: () => {},
    });
  }
  cerrarSesion(): void {
    this.error = null;
    this.auth.logout().subscribe({ error: () => { this.error = 'No se pudo completar el cierre de sesión o renovar la protección de seguridad.'; } });
    this.correo = ''; this.contrasena = '';
  }
}
