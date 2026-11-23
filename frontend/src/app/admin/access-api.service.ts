import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdministrativePermission, AdministrativeRole, AdministrativeUser,
  ChangeUserRoleRequest, ChangeUserStateRequest, CreatePermissionRequest,
  CreateRoleRequest, CreateUserRequest, ReplaceRolePermissionsRequest,
  UpdateRoleRequest, UpdateUserRequest,
} from './access.model';

@Injectable({ providedIn: 'root' })
export class AccessApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/admin';

  users(): Observable<AdministrativeUser[]> { return this.http.get<AdministrativeUser[]>(`${this.base}/usuarios`); }
  user(id: number): Observable<AdministrativeUser> { return this.http.get<AdministrativeUser>(`${this.base}/usuarios/${id}`); }
  createUser(request: CreateUserRequest): Observable<AdministrativeUser> {
    const { rolId, nombre, apellido, correo, password, estado } = request;
    return this.http.post<AdministrativeUser>(`${this.base}/usuarios`, { rolId, nombre, apellido, correo, password, estado });
  }
  updateUser(id: number, request: UpdateUserRequest): Observable<AdministrativeUser> {
    const { nombre, apellido, correo } = request;
    return this.http.put<AdministrativeUser>(`${this.base}/usuarios/${id}`, { nombre, apellido, correo });
  }
  changeUserState(id: number, request: ChangeUserStateRequest): Observable<AdministrativeUser> {
    return this.http.patch<AdministrativeUser>(`${this.base}/usuarios/${id}/estado`, { estado: request.estado });
  }
  changeUserRole(id: number, request: ChangeUserRoleRequest): Observable<AdministrativeUser> {
    return this.http.put<AdministrativeUser>(`${this.base}/usuarios/${id}/rol`, { rolId: request.rolId });
  }

  roles(): Observable<AdministrativeRole[]> { return this.http.get<AdministrativeRole[]>(`${this.base}/roles`); }
  role(id: number): Observable<AdministrativeRole> { return this.http.get<AdministrativeRole>(`${this.base}/roles/${id}`); }
  createRole(request: CreateRoleRequest): Observable<AdministrativeRole> {
    const { codigo, nombre, descripcion } = request;
    return this.http.post<AdministrativeRole>(`${this.base}/roles`, { codigo, nombre, descripcion });
  }
  updateRole(id: number, request: UpdateRoleRequest): Observable<AdministrativeRole> {
    const { nombre, descripcion } = request;
    return this.http.put<AdministrativeRole>(`${this.base}/roles/${id}`, { nombre, descripcion });
  }
  replaceRolePermissions(id: number, request: ReplaceRolePermissionsRequest): Observable<AdministrativeRole> {
    return this.http.put<AdministrativeRole>(`${this.base}/roles/${id}/permisos`, { permisoIds: request.permisoIds });
  }

  permissions(): Observable<AdministrativePermission[]> { return this.http.get<AdministrativePermission[]>(`${this.base}/permisos`); }
  createPermission(request: CreatePermissionRequest): Observable<AdministrativePermission> {
    const { codigo, descripcion } = request;
    return this.http.post<AdministrativePermission>(`${this.base}/permisos`, { codigo, descripcion });
  }
}
