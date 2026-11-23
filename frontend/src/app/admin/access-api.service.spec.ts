import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AccessApiService } from './access-api.service';
import { AdministrativePermission, AdministrativeRole, AdministrativeUser } from './access.model';

describe('AccessApiService', () => {
  let api: AccessApiService;
  let http: HttpTestingController;
  const user: AdministrativeUser = { id: 1, nombre: 'Ana', apellido: 'Prueba', correo: 'ana@example.test',
    estado: 'ACTIVO', rolId: 2, rolCodigo: 'OPERADOR', rolNombre: 'Operador', creadoEn: null,
    actualizadoEn: null, administradorMaestro: false };
  const role: AdministrativeRole = { id: 2, codigo: 'OPERADOR', nombre: 'Operador', descripcion: null,
    creadoEn: null, permisos: ['DONACION_REGISTRAR'] };
  const permission: AdministrativePermission = { id: 3, codigo: 'DONACION_REGISTRAR', descripcion: null, creadoEn: null };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AccessApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('reads users, roles and permissions using the exact routes and typed responses', () => {
    api.users().subscribe(value => expect(value).toEqual([user]));
    http.expectOne({ method: 'GET', url: '/api/admin/usuarios' }).flush([user]);
    api.user(1).subscribe(value => expect(value).toEqual(user));
    http.expectOne({ method: 'GET', url: '/api/admin/usuarios/1' }).flush(user);
    api.roles().subscribe(value => expect(value).toEqual([role]));
    http.expectOne({ method: 'GET', url: '/api/admin/roles' }).flush([role]);
    api.role(2).subscribe(value => expect(value).toEqual(role));
    http.expectOne({ method: 'GET', url: '/api/admin/roles/2' }).flush(role);
    api.permissions().subscribe(value => expect(value).toEqual([permission]));
    http.expectOne({ method: 'GET', url: '/api/admin/permisos' }).flush([permission]);
  });

  it('sends only user request fields, excluding the master indicator even when supplied at runtime', () => {
    const create = { rolId: 2, nombre: 'Ana', apellido: 'Prueba', correo: 'ana@example.test',
      password: 'password-de-prueba', estado: 'ACTIVO', administradorMaestro: true };
    api.createUser(create).subscribe(value => expect(value).toEqual(user));
    const posted = http.expectOne({ method: 'POST', url: '/api/admin/usuarios' });
    expect(posted.request.body).toEqual({ rolId: 2, nombre: 'Ana', apellido: 'Prueba', correo: 'ana@example.test',
      password: 'password-de-prueba', estado: 'ACTIVO' });
    posted.flush(user, { status: 201, statusText: 'Created' });

    const update = { ...user, administradorMaestro: true };
    api.updateUser(1, update).subscribe();
    const updated = http.expectOne({ method: 'PUT', url: '/api/admin/usuarios/1' });
    expect(updated.request.body).toEqual({ nombre: 'Ana', apellido: 'Prueba', correo: 'ana@example.test' });
    updated.flush(user);
    api.changeUserState(1, { estado: 'INACTIVO' }).subscribe();
    const state = http.expectOne({ method: 'PATCH', url: '/api/admin/usuarios/1/estado' });
    expect(state.request.body).toEqual({ estado: 'INACTIVO' }); state.flush(user);
    api.changeUserRole(1, { rolId: 2 }).subscribe();
    const assigned = http.expectOne({ method: 'PUT', url: '/api/admin/usuarios/1/rol' });
    expect(assigned.request.body).toEqual({ rolId: 2 }); assigned.flush(user);
  });

  it('creates and edits roles, replaces permissions and creates permissions', () => {
    api.createRole({ codigo: 'OPERADOR', nombre: 'Operador', descripcion: null }).subscribe();
    const created = http.expectOne({ method: 'POST', url: '/api/admin/roles' });
    expect(created.request.body).toEqual({ codigo: 'OPERADOR', nombre: 'Operador', descripcion: null }); created.flush(role);
    api.updateRole(2, { nombre: 'Coordinador', descripcion: null }).subscribe();
    const edited = http.expectOne({ method: 'PUT', url: '/api/admin/roles/2' });
    expect(edited.request.body).toEqual({ nombre: 'Coordinador', descripcion: null }); edited.flush(role);
    api.replaceRolePermissions(2, { permisoIds: [3] }).subscribe();
    const assigned = http.expectOne({ method: 'PUT', url: '/api/admin/roles/2/permisos' });
    expect(assigned.request.body).toEqual({ permisoIds: [3] }); assigned.flush(role);
    api.createPermission({ codigo: 'DONACION_REGISTRAR', descripcion: null }).subscribe();
    const createdPermission = http.expectOne({ method: 'POST', url: '/api/admin/permisos' });
    expect(createdPermission.request.body).toEqual({ codigo: 'DONACION_REGISTRAR', descripcion: null });
    createdPermission.flush(permission);
  });

  it.each([400, 401, 403, 404, 409, 500])('propagates HTTP %i with its response for the UI', status => {
    let received: HttpErrorResponse | undefined;
    api.user(1).subscribe({ error: (error: HttpErrorResponse) => { received = error; } });
    http.expectOne('/api/admin/usuarios/1').flush({ detail: 'Operación rechazada' }, { status, statusText: 'Error' });
    expect(received?.status).toBe(status);
    expect(received?.error).toEqual({ detail: 'Operación rechazada' });
  });
});
