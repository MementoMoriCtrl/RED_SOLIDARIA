export interface AdministrativeUser {
  id: number;
  nombre: string;
  apellido: string;
  correo: string;
  estado: string;
  rolId: number;
  rolCodigo: string;
  rolNombre: string;
  creadoEn: string | null;
  actualizadoEn: string | null;
  readonly administradorMaestro: boolean;
}

export interface CreateUserRequest {
  rolId: number;
  nombre: string;
  apellido: string;
  correo: string;
  password: string;
  estado: string;
}

export interface UpdateUserRequest { nombre: string; apellido: string; correo: string; }
export interface ChangeUserStateRequest { estado: string; }
export interface ChangeUserRoleRequest { rolId: number; }

export interface AdministrativeRole {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  creadoEn: string | null;
  permisos: string[];
}

export interface CreateRoleRequest { codigo: string; nombre: string; descripcion: string | null; }
export interface UpdateRoleRequest { nombre: string; descripcion: string | null; }
export interface ReplaceRolePermissionsRequest { permisoIds: number[]; }

export interface AdministrativePermission {
  id: number;
  codigo: string;
  descripcion: string | null;
  creadoEn: string | null;
}

export interface CreatePermissionRequest { codigo: string; descripcion: string | null; }
