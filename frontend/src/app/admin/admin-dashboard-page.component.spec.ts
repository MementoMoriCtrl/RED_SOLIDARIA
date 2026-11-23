import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../app.routes';
import { sessionInterceptor } from '../auth/session.interceptor';
import { SessionAuthService } from '../auth/session-auth.service';
import { AdminDashboardPageComponent } from './admin-dashboard-page.component';

describe('AdminDashboardPageComponent', () => {
  let fixture: ComponentFixture<AdminDashboardPageComponent>;
  let http: HttpTestingController;
  let auth: SessionAuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardPageComponent],
      providers: [provideRouter(routes), provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminDashboardPageComponent);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(SessionAuthService);
    auth.markAuthenticated({ nombre: 'Ana', apellido: 'Admin', correo: 'admin@example.test', rolCodigo: 'ADMINISTRADOR',
      rolNombre: 'Administrador', permisos: ['ACCESO_ADMINISTRAR'] });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders available modules after verifying the admin profile', () => {
    const request = http.expectOne('/api/auth/me');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ nombre: 'Ana', apellido: 'Admin', correo: 'admin@example.test', rolCodigo: 'ADMINISTRADOR',
      rolNombre: 'Administrador', permisos: ['ACCESO_ADMINISTRAR'] });
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Bienvenido, Ana');
    expect(fixture.nativeElement.textContent).toContain('Gestión de recursos');
    expect(fixture.nativeElement.textContent).toContain('Gestión de usuarios');
  });

  it.each([false, true])('shows authorized module destinations with registration permission: %s', (canRegister) => {
    http.expectOne('/api/auth/me').flush({ nombre: 'Ana', apellido: 'Admin', correo: 'admin@example.test',
      rolCodigo: 'ADMINISTRADOR', rolNombre: 'Administrador',
      permisos: ['ACCESO_ADMINISTRAR', ...(canRegister ? ['DONACION_REGISTRAR'] : [])] });
    fixture.detectChanges();
    const cards = Array.from(fixture.nativeElement.querySelectorAll('.admin-grid a')) as HTMLAnchorElement[];
    expect(cards.map(card => card.getAttribute('href'))).toEqual([
      '/admin/reportes', '/admin/usuarios', '/admin/roles', ...(canRegister ? ['/donaciones/registro'] : []), '/admin/catalogos-donaciones',
      '/admin/donaciones', '/admin/emergencias', '/recursos', '/admin/auditoria', '/trazabilidad',
    ]);
    expect(fixture.nativeElement.querySelector('.future-card a')).toBeNull();
    const menuPaths = Array.from(fixture.nativeElement.querySelectorAll('.side-nav a'))
      .map(link => (link as HTMLAnchorElement).getAttribute('href'));
    expect(cards.every(card => menuPaths.includes(card.getAttribute('href')))).toBe(true);
  });

  it('reports server errors and ends loading', () => {
    http.expectOne('/api/auth/me').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.componentInstance.error).toContain('No se pudo verificar');
  });

  it('logout clears in-memory credentials and navigates to access', async () => {
    http.expectOne('/api/auth/me').flush({ nombre: 'Ana', apellido: 'Admin', correo: 'admin@example.test',
      rolCodigo: 'ADMINISTRADOR', rolNombre: 'Administrador', permisos: ['ACCESO_ADMINISTRAR'] });
    await fixture.whenStable();
    fixture.nativeElement.querySelector('.header-session-button').click();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'before' });
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'after' });
    await fixture.whenStable();
    expect(auth.state()).toBe('signed-out');
    expect(auth.profile()).toBeNull();
    expect(auth.profile()).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/acceso-organizaciones');
  });
});
