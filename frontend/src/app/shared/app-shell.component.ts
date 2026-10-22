import { Component, Input, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
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

  @Input() breadcrumb = '';
  @Input() footerText = 'Red Solidaria';

  get showAdminLink(): boolean {
    return this.auth.hasPermission('ACCESO_ADMINISTRAR');
  }

  get showResourceLink(): boolean {
    return this.auth.state() === 'authenticated';
  }
}
