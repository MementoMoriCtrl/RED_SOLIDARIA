import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from '../auth/basic-auth.service';
import { AppShellComponent } from './app-shell.component';

@Component({ standalone: true, template: '' })
class EmptyPageComponent {}

describe('AppShellComponent', () => {
  let auth: BasicAuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideRouter([
        { path: 'admin', component: EmptyPageComponent },
        { path: 'admin/donaciones', component: EmptyPageComponent },
        { path: 'admin/donaciones/:id', component: EmptyPageComponent },
        { path: 'recursos', component: EmptyPageComponent },
        { path: 'trazabilidad', component: EmptyPageComponent },
      ])],
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

    for (const path of ['/admin', '/admin/donaciones', '/recursos', '/trazabilidad']) {
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
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/recursos"]').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.side-nav a[href="/trazabilidad"]').length).toBe(1);
  });

  it('keeps only the public trace link when signed out', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.side-nav a').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.side-nav a').getAttribute('href')).toBe('/trazabilidad');
  });
});
