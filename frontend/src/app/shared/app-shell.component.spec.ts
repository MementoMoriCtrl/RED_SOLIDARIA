import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../app.routes';
import { BasicAuthService } from '../auth/basic-auth.service';
import { AppShellComponent } from './app-shell.component';

@Component({ standalone: true, template: '' })
class EmptyPageComponent {}

describe('AppShellComponent', () => {
  let auth: BasicAuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      // Keep the application's real paths and guards; isolate page rendering from API calls.
      providers: [provideRouter(routes.map(({ loadComponent, ...route }) => ({
        ...route, ...(loadComponent ? { component: EmptyPageComponent } : {}),
      })))],
    });
    auth = TestBed.inject(BasicAuthService);
    auth.logout();
  });

  it('renders each admin navigation link once and marks the active route', async () => {
    auth.provideCredentials('admin@example.test', 'test-password');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'User', correo: 'admin@example.test',
      rolCodigo: 'ADMINISTRADOR', rolNombre: 'Administrador', permisos: ['ACCESO_ADMINISTRAR'] });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/admin/donaciones/3');
    fixture.detectChanges();

    for (const path of ['/admin', '/admin/donaciones', '/admin/auditoria', '/admin/emergencias', '/recursos', '/trazabilidad']) {
      expect(fixture.nativeElement.querySelectorAll(`.side-nav a[href="${path}"]`).length).toBe(1);
    }
    const active = fixture.nativeElement.querySelector('.side-nav a[href="/admin/donaciones"]');
    expect(active.classList.contains('active')).toBe(true);
    expect(active.getAttribute('aria-current')).toBe('page');
  });

  it('does not show administrative navigation to an authenticated non-admin', () => {
    auth.provideCredentials('org@example.test', 'test-password');
    auth.markAuthenticated({ nombre: 'Org', apellido: 'User', correo: 'org@example.test',
      rolCodigo: 'ORGANIZACION', rolNombre: 'Organización', permisos: ['DONACION_REGISTRAR'] });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/admin"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/admin/donaciones"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/admin/auditoria"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/admin/emergencias"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/admin/catalogos-donaciones"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/donaciones/registro"]').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/recursos"]').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/trazabilidad"]').length).toBe(1);
  });

  it('keeps only the public trace link when signed out', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.side-nav a').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.side-nav a').getAttribute('href')).toBe('/trazabilidad');
    expect(fixture.nativeElement.querySelectorAll('.session-button').length).toBe(0);
  });
  it('marks only the matching donation entry active for users with both permissions', async () => {
    auth.provideCredentials('admin@test', 'test-password');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['DONACION_REGISTRAR', 'ACCESO_ADMINISTRAR'] });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    for (const path of ['/donaciones/registro', '/admin/catalogos-donaciones']) {
      await TestBed.inject(Router).navigateByUrl(path);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll(`.side-nav a[href="${path}"]`).length).toBe(1);
      const active = fixture.nativeElement.querySelectorAll('.side-nav a.active');
      expect(active.length).toBe(1);
      expect(active[0].getAttribute('href')).toBe(path);
      expect(active[0].getAttribute('aria-current')).toBe('page');
    }
  });
  it.each([
    { button: '.header-session-button', path: '/donaciones/registro' },
    { button: '.sidebar-session-button', path: '/admin/catalogos-donaciones' },
  ])('logs out through $button, clears authentication, and rejects protected routes', async ({ button, path }) => {
    auth.provideCredentials('admin@test', 'test-password');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['ACCESO_ADMINISTRAR', 'DONACION_REGISTRAR'] });
    const router = TestBed.inject(Router);
    await router.navigateByUrl(path);
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.page-header .session-button').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.sidebar-footer .session-button').length).toBe(1);
    const logout = vi.spyOn(auth, 'logout');
    const sharedLogout = vi.spyOn(fixture.componentInstance, 'cerrarSesion');
    fixture.nativeElement.querySelector(button).click();
    await fixture.whenStable();
    expect(sharedLogout).toHaveBeenCalledOnce();
    expect(logout).toHaveBeenCalledOnce();
    expect(auth.state()).toBe('signed-out');
    expect(auth.hasCredentials()).toBe(false);
    expect(auth.authorizationHeader()).toBeNull();
    expect(auth.username()).toBeNull();
    expect(auth.profile()).toBeNull();
    expect(auth.hasPermission('ACCESO_ADMINISTRAR')).toBe(false);
    expect(auth.hasPermission('DONACION_REGISTRAR')).toBe(false);
    expect(router.url).toBe('/acceso-organizaciones');
    expect(fixture.nativeElement.querySelectorAll('.session-button').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a').length).toBe(1);
    for (const protectedRoute of routes.filter(route => route.canActivate?.length)) {
      const url = '/' + protectedRoute.path!.replace(':id', '1').replace(':zoneId', '1');
      await router.navigateByUrl(url);
      expect(router.url).toBe('/acceso-organizaciones');
    }
  });
  it('shows both logout actions to an authenticated organization without administrative permission', () => {
    auth.provideCredentials('org@test', 'test-password');
    auth.markAuthenticated({ nombre: 'Org', apellido: 'Test', correo: 'org@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['DONACION_REGISTRAR'] });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.session-button').length).toBe(2);
  });
});
