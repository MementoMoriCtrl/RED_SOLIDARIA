import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../app.routes';
import { BasicAuthService } from './basic-auth.service';
import { OrganizationLoginPageComponent } from './organization-login-page.component';

describe('OrganizationLoginPageComponent', () => {
  let fixture: ComponentFixture<OrganizationLoginPageComponent>;
  let auth: BasicAuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrganizationLoginPageComponent],
      providers: [provideRouter(routes)],
    }).compileComponents();
    fixture = TestBed.createComponent(OrganizationLoginPageComponent);
    auth = TestBed.inject(BasicAuthService);
    auth.logout();
    fixture.detectChanges();
  });

  it('keeps submitted credentials pending until a protected backend request validates them', () => {
    fixture.componentInstance.correo = 'org@example.test';
    fixture.componentInstance.contrasena = 'test-password';
    fixture.componentInstance.continuar();
    fixture.detectChanges();

    expect(auth.state()).toBe('pending');
    expect(fixture.nativeElement.textContent).toContain('aún no han sido validadas');
    expect(fixture.nativeElement.textContent).not.toContain('Acceso validado');
    expect(fixture.componentInstance.contrasena).toBe('');
  });

  it('clears in-memory credentials on logout', () => {
    auth.provideCredentials('org@example.test', 'test-password');
    fixture.componentInstance.cerrarSesion();

    expect(auth.state()).toBe('signed-out');
    expect(auth.authorizationHeader()).toBeNull();
  });
});
