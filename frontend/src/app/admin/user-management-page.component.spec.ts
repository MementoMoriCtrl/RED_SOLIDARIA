import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { UserManagementPageComponent } from './user-management-page.component';
import { AdministrativeRole, AdministrativeUser } from './access.model';
import { SessionAuthService } from '../auth/session-auth.service';

const roles: AdministrativeRole[] = [{ id: 2, codigo: 'OPERADOR', nombre: 'Operador', descripcion: null, creadoEn: null, permisos: [] },
  { id: 3, codigo: 'ADMIN', nombre: 'Administrador', descripcion: null, creadoEn: null, permisos: ['ACCESO_ADMINISTRAR'] }];
const user: AdministrativeUser = { id: 8, nombre: 'Ana', apellido: 'Sol', correo: 'ana@example.test', estado: 'ACTIVO', rolId: 2,
  rolCodigo: 'OPERADOR', rolNombre: 'Operador', creadoEn: null, actualizadoEn: null, administradorMaestro: false };
const master: AdministrativeUser = { ...user, id: 9, nombre: 'Cuenta', correo: 'master@example.test', rolId: 3,
  rolCodigo: 'ADMIN', rolNombre: 'Administrador', administradorMaestro: true };

describe('UserManagementPageComponent', () => {
  let fixture: ComponentFixture<UserManagementPageComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UserManagementPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(UserManagementPageComponent);
    const auth = TestBed.inject(SessionAuthService);
    auth.markAuthenticated({ nombre:'Master', apellido:'Test', correo:'master@example.test', rolCodigo:'ADMIN', rolNombre:'Admin', permisos:['ADMIN_MAESTRO','ACCESO_ADMINISTRAR','DONACION_REGISTRAR'] });
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
    http.expectOne('/api/admin/usuarios').flush([user, master]);
    http.expectOne('/api/admin/roles').flush(roles);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());

  it('renders accounts, protects master controls and filters by name, email, state and role', async () => {
    expect(fixture.nativeElement.textContent).toContain('Administrador Maestro');
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[1].querySelectorAll('button')[1].disabled).toBe(true);
    expect(Array.from(rows[1].querySelectorAll('select')).every((item: unknown) => (item as HTMLSelectElement).disabled)).toBe(true);
    const component = fixture.componentInstance;
    component.search = 'ANA@'; expect(component.filteredUsers.map(x => x.id)).toEqual([8]);
    component.search = 'cuenta'; expect(component.filteredUsers.map(x => x.id)).toEqual([9]);
    component.search = ''; component.roleFilter = '2'; component.stateFilter = 'ACTIVO';
    expect(component.filteredUsers.map(x => x.id)).toEqual([8]);
    component.stateFilter = 'BLOQUEADO'; expect(component.filteredUsers.length).toBe(0);
  });

  it('validates creation and sends the expected fields without exposing the password', () => {
    const component = fixture.componentInstance;
    component.create(); component.save();
    http.expectNone({ method: 'POST', url: '/api/admin/usuarios' });
    expect(component.formError).toContain('obligatorios');
    component.form.setValue({ nombre: ' Ana ', apellido: ' Sol ', correo: 'ANA@example.test', password: 'test-password-123', rolId: 2, estado: 'ACTIVO' });
    component.save();
    const request = http.expectOne({ method: 'POST', url: '/api/admin/usuarios' });
    expect(request.request.body).toEqual({ nombre: 'Ana', apellido: 'Sol', correo: 'ana@example.test',
      password: 'test-password-123', rolId: 2, estado: 'ACTIVO' });
    request.flush(user, { status: 201, statusText: 'Created' });
    expect(component.success).toContain('creado'); expect(component.form.value.password).toBe('');
  });

  it('edits an ordinary user and never attempts to edit the protected account', () => {
    const component = fixture.componentInstance;
    component.edit(master); expect(component.mode).toBeNull();
    component.edit(user); component.form.patchValue({ nombre: 'Ana María' }); component.save();
    const request = http.expectOne({ method: 'PUT', url: '/api/admin/usuarios/8' });
    expect(request.request.body).toEqual({ nombre: 'Ana María', apellido: 'Sol', correo: 'ana@example.test' });
    request.flush({ ...user, nombre: 'Ana María' });
    expect(component.users.find(x => x.id === 8)?.nombre).toBe('Ana María');
  });

  it('hides privileged role options and prevents managing privileged accounts for an ordinary admin', () => {
    TestBed.inject(SessionAuthService).markAuthenticated({ nombre:'Admin', apellido:'Test', correo:'admin@example.test', rolCodigo:'ADMIN', rolNombre:'Admin', permisos:['ACCESO_ADMINISTRAR'] });
    const component=fixture.componentInstance;
    const privileged = { ...user, id: 10, rolId: 3, administradorMaestro: false };
    expect(component.assignableRoles.map(role => role.id)).toEqual([2]);
    expect(component.canManage(privileged)).toBe(false); component.edit(privileged);
    expect(component.mode).toBeNull(); component.requestChange(user, 'rol', '3'); expect(component.pending).toBeNull();
  });

  it('loads detail and confirms state and role changes before sending', () => {
    const component = fixture.componentInstance;
    component.details(user); http.expectOne('/api/admin/usuarios/8').flush(user); expect(component.selected).toEqual(user);
    component.requestChange(user, 'estado', 'INACTIVO');
    http.expectNone('/api/admin/usuarios/8/estado'); component.cancelChange();
    component.requestChange(user, 'estado', 'INACTIVO'); component.confirmChange();
    const state = http.expectOne({ method: 'PATCH', url: '/api/admin/usuarios/8/estado' });
    expect(state.request.body).toEqual({ estado: 'INACTIVO' }); state.flush({ ...user, estado: 'INACTIVO' });
    component.requestChange(user, 'rol', '3'); component.confirmChange();
    const role = http.expectOne({ method: 'PUT', url: '/api/admin/usuarios/8/rol' });
    expect(role.request.body).toEqual({ rolId: 3 }); role.flush({ ...user, rolId: 3 });
    component.requestChange(master, 'estado', 'INACTIVO'); component.requestChange(master, 'rol', '2');
    expect(component.pending).toBeNull(); http.expectNone('/api/admin/usuarios/9/estado'); http.expectNone('/api/admin/usuarios/9/rol');
  });

  it.each([400, 401, 403, 404, 409])('reports HTTP %i while retaining the edit form', status => {
    const component = fixture.componentInstance; component.edit(user); component.save();
    http.expectOne('/api/admin/usuarios/8').flush({}, { status, statusText: 'Error' });
    expect(component.formError).toBeTruthy(); expect(component.mode).toBe('edit'); expect(component.saving).toBe(false);
  });

  it('reports a network error without treating the update as successful', () => {
    const component = fixture.componentInstance; component.edit(user); component.save();
    http.expectOne('/api/admin/usuarios/8').error(new ProgressEvent('error'));
    expect(component.formError).toContain('conectar');
    expect(component.mode).toBe('edit'); expect(component.success).toBeNull();
  });
});
