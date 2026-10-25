import { Component, Input, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { BasicAuthService } from '../auth/basic-auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
})
export class AppShellComponent {
  private readonly auth = inject(BasicAuthService);
  private readonly router = inject(Router);

  get isAuthenticated(): boolean { return this.auth.state() === 'authenticated'; }

  cerrarSesion(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/acceso-organizaciones', { replaceUrl: true });
  }

  @Input() breadcrumb = '';
  @Input() footerText = 'Red Solidaria';

  get showAdminLink(): boolean {
    return this.auth.hasPermission('ACCESO_ADMINISTRAR');
  }

  get showResourceLink(): boolean {
    return this.auth.state() === 'authenticated';
  }
  get showDonationRegistrationLink(): boolean { return this.auth.hasPermission('DONACION_REGISTRAR'); }
}
