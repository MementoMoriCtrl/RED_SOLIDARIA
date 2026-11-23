import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AccessApiService } from './access-api.service';
import { SessionAuthService } from '../auth/session-auth.service';
import { AdministrativePermission, AdministrativeRole, AdministrativeUser } from './access.model';

type Pending = { kind: 'role'; id: number; nombre: string; descripcion: string | null }
  | { kind: 'permissions'; id: number; ids: number[] };

@Component({
  selector: 'app-role-management-page', standalone: true,
  imports: [AppShellComponent, FormsModule, ReactiveFormsModule],
  templateUrl: './role-management-page.component.html',
  styleUrls: ['./user-management-page.component.css', './role-management-page.component.css'],
})
export class RoleManagementPageComponent implements OnInit {
  private readonly api = inject(AccessApiService);
  private readonly fb = inject(FormBuilder);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly auth = inject(SessionAuthService);
  readonly essential = new Set(['ACCESO_ADMINISTRAR', 'DONACION_REGISTRAR']);
  roles: AdministrativeRole[] = []; permissions: AdministrativePermission[] = []; users: AdministrativeUser[] = [];
  loading = true; detailLoading = false; saving = false;
  error: string | null = null; formError: string | null = null; success: string | null = null;
  search = ''; selected: AdministrativeRole | null = null; selectedIds = new Set<number>();
  mode: 'create' | 'edit' | null = null; permissionFormOpen = false; pending: Pending | null = null;
  readonly roleForm = this.fb.nonNullable.group({ codigo: ['', [Validators.required, Validators.maxLength(50)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]], descripcion: ['', Validators.maxLength(255)] });
  readonly permissionForm = this.fb.nonNullable.group({ codigo: ['', [Validators.required, Validators.maxLength(80)]],
    descripcion: ['', Validators.maxLength(255)] });

  ngOnInit(): void { this.load(); }
  get filteredRoles(): AdministrativeRole[] {
    const q = this.search.trim().toLocaleLowerCase();
    return this.roles.filter(role => !q || `${role.nombre} ${role.codigo}`.toLocaleLowerCase().includes(q));
  }
  get masterRole(): boolean { return !!this.selected && this.users.some(user => user.administradorMaestro && user.rolId === this.selected!.id); }
  get isMaster(): boolean { return this.auth.hasPermission('ADMIN_MAESTRO') && this.auth.hasPermission('ACCESO_ADMINISTRAR') && this.auth.hasPermission('DONACION_REGISTRAR'); }
  get privilegedRole(): boolean { return !!this.selected?.permisos.includes('ACCESO_ADMINISTRAR'); }
  get canEditSelected(): boolean { return !!this.selected && (this.isMaster || !this.privilegedRole); }
  get missingCodes(): string[] {
    if (!this.selected) return [];
    return this.selected.permisos.filter(code => !this.permissions.some(permission => permission.codigo === code));
  }
  get cannotReplace(): boolean {
    return !this.canEditSelected || this.missingCodes.length > 0 || !!this.selected?.permisos.some(code => this.isReserved(code));
  }
  isReserved(code: string): boolean { return code.trim().toUpperCase() === 'ADMIN_MAESTRO'; }
  isLocked(permission: AdministrativePermission): boolean {
    return this.isReserved(permission.codigo) || (this.masterRole && this.essential.has(permission.codigo))
      || (permission.codigo === 'ACCESO_ADMINISTRAR' && !this.isMaster);
  }
  load(): void {
    this.loading = true; this.error = null; this.selected = null; this.pending = null;
    forkJoin({ roles: this.api.roles(), permissions: this.api.permissions(), users: this.api.users() })
      .pipe(finalize(() => { this.loading = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => { this.roles = result.roles; this.permissions = result.permissions; this.users = result.users; },
        error: (error: HttpErrorResponse) => { this.error = this.message(error); } });
  }
  details(role: AdministrativeRole): void {
    if (this.saving || this.detailLoading) return;
    this.detailLoading = true; this.error = null; this.selected = null; this.mode = null; this.pending = null;
    this.api.role(role.id).pipe(finalize(() => { this.detailLoading = false; this.cd.markForCheck(); }))
      .subscribe({ next: result => { this.selected = result; this.selectedIds = new Set(this.permissions.filter(p => result.permisos.includes(p.codigo)).map(p => p.id)); },
        error: (error: HttpErrorResponse) => { this.error = this.message(error); } });
  }
  createRole(): void { if (this.saving) return; this.mode = 'create'; this.pending = null; this.formError = null; this.success = null; this.roleForm.reset(); }
  editRole(): void {
    if (!this.canEditSelected || !this.selected || this.saving) return;
    this.mode = 'edit'; this.pending = null; this.formError = null; this.success = null;
    this.roleForm.setValue({ codigo: this.selected.codigo, nombre: this.selected.nombre, descripcion: this.selected.descripcion ?? '' });
  }
  saveRole(): void {
    if (!this.mode || this.saving || this.pending) return;
    this.roleForm.markAllAsTouched(); const { codigo, nombre, descripcion } = this.roleForm.getRawValue();
    if (this.roleForm.invalid || !nombre.trim() || (this.mode === 'create' && !codigo.trim())) { this.formError = 'Completa los campos obligatorios.'; return; }
    this.formError = null;
    if (this.mode === 'edit' && this.selected) {
      this.pending = { kind: 'role', id: this.selected.id, nombre: nombre.trim(), descripcion: descripcion.trim() || null }; return;
    }
    this.saving = true;
    this.api.createRole({ codigo: codigo.trim(), nombre: nombre.trim(), descripcion: descripcion.trim() || null })
      .pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: role => { this.upsert(role); this.mode = null; this.success = 'Rol creado correctamente.'; },
        error: (error: HttpErrorResponse) => { this.formError = this.message(error); } });
  }
  togglePermission(permission: AdministrativePermission, checked: boolean): void {
    if (this.saving || this.pending || this.cannotReplace || this.isLocked(permission)) return;
    const next = new Set(this.selectedIds); checked ? next.add(permission.id) : next.delete(permission.id); this.selectedIds = next;
  }
  requestPermissions(): void {
    if (!this.selected || this.saving || this.pending || this.cannotReplace) return;
    const selected = this.permissions.filter(permission => this.selectedIds.has(permission.id));
    if (selected.some(permission => this.isReserved(permission.codigo))) { this.error = 'ADMIN_MAESTRO es una autoridad reservada.'; return; }
    if (this.masterRole && [...this.essential].some(code => !selected.some(permission => permission.codigo === code))) {
      this.error = 'El rol del Maestro debe conservar sus permisos esenciales.'; return;
    }
    if (!this.isMaster && selected.some(permission => permission.codigo === 'ACCESO_ADMINISTRAR')) {
      this.error = 'Solo el Administrador Maestro puede conceder acceso administrativo.'; return;
    }
    this.error = null; this.pending = { kind: 'permissions', id: this.selected.id, ids: selected.map(permission => permission.id) };
  }
  confirm(): void {
    const pending = this.pending;
    if (!pending || this.saving || !this.selected || this.selected.id !== pending.id || !this.canEditSelected) return;
    if (pending.kind === 'permissions') {
      const chosen = this.permissions.filter(permission => pending.ids.includes(permission.id));
      if (this.cannotReplace || chosen.length !== pending.ids.length || chosen.some(p => this.isReserved(p.codigo))
        || (this.masterRole && [...this.essential].some(code => !chosen.some(p => p.codigo === code)))
        || (!this.isMaster && chosen.some(p => p.codigo === 'ACCESO_ADMINISTRAR'))) {
        this.pending = null; this.error = 'Los permisos seleccionados ya no cumplen las restricciones. Actualiza la lista.'; return;
      }
    }
    this.saving = true;
    const operation = pending.kind === 'role' ? this.api.updateRole(pending.id, { nombre: pending.nombre, descripcion: pending.descripcion })
      : this.api.replaceRolePermissions(pending.id, { permisoIds: pending.ids });
    operation.pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); })).subscribe({
      next: role => { this.upsert(role); this.selected = role; this.selectedIds = new Set(this.permissions.filter(p => role.permisos.includes(p.codigo)).map(p => p.id));
        this.pending = null; this.mode = null; this.success = 'Cambios guardados correctamente.'; },
      error: (error: HttpErrorResponse) => { this.pending = null; this.error = this.message(error); },
    });
  }
  createPermission(): void {
    if (this.saving) return;
    this.permissionForm.markAllAsTouched(); const { codigo, descripcion } = this.permissionForm.getRawValue();
    if (this.permissionForm.invalid || !codigo.trim()) { this.formError = 'Introduce un código válido.'; return; }
    if (this.isReserved(codigo)) { this.formError = 'ADMIN_MAESTRO es una autoridad reservada y no puede crearse.'; return; }
    this.saving = true; this.formError = null;
    this.api.createPermission({ codigo: codigo.trim(), descripcion: descripcion.trim() || null })
      .pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); }))
      .subscribe({ next: permission => { this.permissions = [...this.permissions, permission]; this.permissionForm.reset();
        this.permissionFormOpen = false; this.success = 'Permiso creado correctamente.'; },
        error: (error: HttpErrorResponse) => { this.formError = this.message(error); } });
  }
  cancelForm(): void { if (!this.saving) { this.mode = null; this.permissionFormOpen = false; this.formError = null; } }
  cancelConfirmation(): void { if (!this.saving) this.pending = null; }
  private upsert(role: AdministrativeRole): void { this.roles = [...this.roles.filter(item => item.id !== role.id), role].sort((a, b) => a.id - b.id); }
  private message(error: HttpErrorResponse): string {
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    if (error.status === 400) return 'Revisa los datos enviados; el servidor los rechazó.';
    if (error.status === 401) return 'La sesión no es válida. Inicia sesión nuevamente.';
    if (error.status === 403) return 'No tienes permiso para realizar esta operación o el permiso está protegido.';
    if (error.status === 404) return 'El rol o permiso ya no está disponible. Actualiza la lista.';
    if (error.status === 409) return 'Existe un conflicto con el código o con las restricciones del rol.';
    return 'No se pudo completar la operación. Inténtalo de nuevo.';
  }
}
