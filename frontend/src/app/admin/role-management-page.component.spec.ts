import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RoleManagementPageComponent } from './role-management-page.component';
import { AdministrativePermission, AdministrativeRole, AdministrativeUser } from './access.model';
import { SessionAuthService } from '../auth/session-auth.service';

const permissions: AdministrativePermission[] = [
  { id: 1, codigo: 'ACCESO_ADMINISTRAR', descripcion: null, creadoEn: null },
  { id: 2, codigo: 'DONACION_REGISTRAR', descripcion: null, creadoEn: null },
  { id: 3, codigo: 'RECURSOS_CONSULTAR', descripcion: 'Consultar recursos', creadoEn: null },
  { id: 4, codigo: 'ADMIN_MAESTRO', descripcion: null, creadoEn: null },
];
const masterRole: AdministrativeRole = { id: 5, codigo: 'ADMIN', nombre: 'Administración', descripcion: null,
  creadoEn: null, permisos: ['ACCESO_ADMINISTRAR', 'DONACION_REGISTRAR'] };
const otherRole: AdministrativeRole = { ...masterRole, id: 6, codigo: 'OPERADOR', nombre: 'Operador', permisos: ['RECURSOS_CONSULTAR'] };
const master: AdministrativeUser = { id: 1, nombre: 'Cuenta', apellido: 'Prueba', correo: 'master@example.test', estado: 'ACTIVO',
  rolId: 5, rolCodigo: 'ADMIN', rolNombre: 'Administración', creadoEn: null, actualizadoEn: null, administradorMaestro: true };

describe('RoleManagementPageComponent', () => {
  let fixture: ComponentFixture<RoleManagementPageComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RoleManagementPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(RoleManagementPageComponent); http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(SessionAuthService);
    auth.markAuthenticated({ nombre:'Master', apellido:'Test', correo:'master@example.test', rolCodigo:'ADMIN', rolNombre:'Admin', permisos:['ADMIN_MAESTRO','ACCESO_ADMINISTRAR','DONACION_REGISTRAR'] });
    fixture.autoDetectChanges();
    http.expectOne('/api/admin/roles').flush([masterRole, otherRole]);
    http.expectOne('/api/admin/permisos').flush(permissions);
    http.expectOne('/api/admin/usuarios').flush([master]);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());
  function select(role = masterRole): void { fixture.componentInstance.details(role); http.expectOne(`/api/admin/roles/${role.id}`).flush(role); }

  it('lists roles and permissions and searches by name or code', () => {
    expect(fixture.nativeElement.textContent).toContain('Administración');
    expect(fixture.nativeElement.textContent).toContain('RECURSOS_CONSULTAR');
    fixture.componentInstance.search = 'oper'; expect(fixture.componentInstance.filteredRoles).toEqual([otherRole]);
  });
  it('creates a role and prevents reserved permission creation', () => {
    const c = fixture.componentInstance; c.createRole(); c.saveRole(); expect(c.formError).toBeTruthy();
    c.roleForm.setValue({ codigo: 'LOG', nombre: 'Logística', descripcion: '' }); c.saveRole();
    const request = http.expectOne({ method: 'POST', url: '/api/admin/roles' });
    expect(request.request.body).toEqual({ codigo: 'LOG', nombre: 'Logística', descripcion: null });
    request.flush({ ...otherRole, id: 7, codigo: 'LOG' });
    c.permissionForm.setValue({ codigo: ' admin_maestro ', descripcion: '' }); c.createPermission();
    expect(c.formError).toContain('reservada'); http.expectNone({ method: 'POST', url: '/api/admin/permisos' });
    c.permissionForm.setValue({ codigo: 'CONSULTAR', descripcion: 'Consulta' }); c.createPermission();
    const permission = http.expectOne({ method: 'POST', url: '/api/admin/permisos' });
    expect(permission.request.body).toEqual({ codigo: 'CONSULTAR', descripcion: 'Consulta' });
    permission.flush({ id: 8, codigo: 'CONSULTAR', descripcion: 'Consulta', creadoEn: null });
  });
  it('edits a role only after confirmation and preserves the immutable code', () => {
    select(); const c = fixture.componentInstance; c.editRole(); c.roleForm.patchValue({ nombre: 'Nuevo' }); c.saveRole();
    http.expectNone({ method: 'PUT', url: '/api/admin/roles/5' }); c.confirm();
    const request = http.expectOne({ method: 'PUT', url: '/api/admin/roles/5' });
    expect(request.request.body).toEqual({ nombre: 'Nuevo', descripcion: null }); request.flush({ ...masterRole, nombre: 'Nuevo' });
    expect(c.selected?.nombre).toBe('Nuevo');
  });
  it('maps codes to IDs and protects master essentials and the reserved authority', () => {
    select(); const c = fixture.componentInstance;
    expect(c.masterRole).toBe(true); expect(c.selectedIds).toEqual(new Set([1, 2]));
    c.togglePermission(permissions[0], false); c.togglePermission(permissions[3], true);
    expect(c.selectedIds).toEqual(new Set([1, 2]));
    c.togglePermission(permissions[2], true); c.requestPermissions();
    http.expectNone({ method: 'PUT', url: '/api/admin/roles/5/permisos' }); c.confirm();
    const request = http.expectOne({ method: 'PUT', url: '/api/admin/roles/5/permisos' });
    expect(request.request.body).toEqual({ permisoIds: [1, 2, 3] });
    request.flush({ ...masterRole, permisos: ['ACCESO_ADMINISTRAR', 'DONACION_REGISTRAR', 'RECURSOS_CONSULTAR'] });
  });
  it('blocks replacement if an assigned code is missing from the catalog', () => {
    select({ ...otherRole, permisos: ['NO_DISPONIBLE'] }); const c = fixture.componentInstance;
    expect(c.cannotReplace).toBe(true); c.requestPermissions(); expect(c.pending).toBeNull();
    http.expectNone({ method: 'PUT', url: '/api/admin/roles/6/permisos' });
  });
  it('does not grant administrative access to a role that does not already have it', () => {
    TestBed.inject(SessionAuthService).markAuthenticated({ nombre:'Admin', apellido:'Test', correo:'admin@example.test', rolCodigo:'ADMIN', rolNombre:'Admin', permisos:['ACCESO_ADMINISTRAR'] });
    select(otherRole); const c = fixture.componentInstance;
    expect(c.isLocked(permissions[0])).toBe(true); c.togglePermission(permissions[0], true);
    expect(c.selectedIds.has(1)).toBe(false);
  });
  it('prevents ordinary administrators from editing privileged roles', () => {
    TestBed.inject(SessionAuthService).markAuthenticated({ nombre:'Admin', apellido:'Test', correo:'admin@example.test', rolCodigo:'ADMIN', rolNombre:'Admin', permisos:['ACCESO_ADMINISTRAR'] });
    select(); const c=fixture.componentInstance; expect(c.canEditSelected).toBe(false); c.editRole(); c.requestPermissions();
    expect(c.mode).toBeNull(); expect(c.pending).toBeNull();
  });
  it('allows a healthy master to grant administrative access to another role', () => {
    select(otherRole); const c=fixture.componentInstance; expect(c.isLocked(permissions[0])).toBe(false);
    c.togglePermission(permissions[0],true); c.requestPermissions(); c.confirm();
    const request=http.expectOne({ method:'PUT',url:'/api/admin/roles/6/permisos' });
    expect(request.request.body).toEqual({ permisoIds:[1,3] }); request.flush({ ...otherRole, permisos:['ACCESO_ADMINISTRAR','RECURSOS_CONSULTAR'] });
  });
  it.each([400, 401, 403, 404, 409])('shows HTTP %i errors from the API', status => {
    select(otherRole); const c = fixture.componentInstance; c.editRole(); c.saveRole(); c.confirm();
    http.expectOne('/api/admin/roles/6').flush({}, { status, statusText: 'Error' });
    expect(c.error).toBeTruthy(); expect(c.saving).toBe(false);
  });
  it('reports a network error without marking a role update successful', () => {
    select(otherRole); const c = fixture.componentInstance; c.editRole(); c.saveRole(); c.confirm();
    http.expectOne('/api/admin/roles/6').error(new ProgressEvent('error'));
    expect(c.error).toContain('conectar'); expect(c.success).toBeNull(); expect(c.saving).toBe(false);
  });
});
