import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { AppShellComponent } from '../shared/app-shell.component';
import { AccessApiService } from './access-api.service';
import { SessionAuthService } from '../auth/session-auth.service';
import { AdministrativeRole, AdministrativeUser } from './access.model';

@Component({
  selector: 'app-user-management-page', standalone: true,
  imports: [AppShellComponent, FormsModule, ReactiveFormsModule, DatePipe],
  templateUrl: './user-management-page.component.html', styleUrl: './user-management-page.component.css',
})
export class UserManagementPageComponent implements OnInit {
  private readonly api = inject(AccessApiService);
  private readonly fb = inject(FormBuilder);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly auth = inject(SessionAuthService);
  readonly states = ['ACTIVO', 'INACTIVO', 'BLOQUEADO', 'SUSPENDIDO'];
  users: AdministrativeUser[] = [];
  roles: AdministrativeRole[] = [];
  loading = true; saving = false; detailLoading = false;
  error: string | null = null; formError: string | null = null; success: string | null = null;
  search = ''; stateFilter = ''; roleFilter = '';
  selected: AdministrativeUser | null = null;
  editTarget: AdministrativeUser | null = null;
  mode: 'create' | 'edit' | null = null;
  pending: { user: AdministrativeUser; kind: 'estado' | 'rol'; value: string } | null = null;

  readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    apellido: ['', [Validators.required, Validators.maxLength(100)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: [''], rolId: [0], estado: ['ACTIVO'],
  });

  get filteredUsers(): AdministrativeUser[] {
    const search = this.search.trim().toLocaleLowerCase();
    return this.users.filter(user => (!search || `${user.nombre} ${user.apellido} ${user.correo}`.toLocaleLowerCase().includes(search))
      && (!this.stateFilter || user.estado === this.stateFilter)
      && (!this.roleFilter || String(user.rolId) === this.roleFilter));
  }
  get isMaster(): boolean { return this.auth.hasPermission('ADMIN_MAESTRO') && this.auth.hasPermission('ACCESO_ADMINISTRAR') && this.auth.hasPermission('DONACION_REGISTRAR'); }
  get assignableRoles(): AdministrativeRole[] { return this.roles.filter(role => this.isMaster || !role.permisos.includes('ACCESO_ADMINISTRAR')); }
  canManage(user: AdministrativeUser): boolean {
    const role = this.roles.find(item => item.id === user.rolId);
    return !user.administradorMaestro && !!role && (this.isMaster || !role.permisos.includes('ACCESO_ADMINISTRAR'));
  }

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading = true; this.error = null;
    forkJoin({ users: this.api.users(), roles: this.api.roles() }).pipe(finalize(() => {
      this.loading = false; this.cd.markForCheck();
    })).subscribe({ next: result => { this.users = result.users; this.roles = result.roles; },
      error: (e: HttpErrorResponse) => { this.error = this.message(e); } });
  }
  create(): void {
    this.mode = 'create'; this.editTarget = null; this.formError = null; this.success = null;
    this.form.reset({ nombre: '', apellido: '', correo: '', password: '', rolId: 0, estado: 'ACTIVO' });
  }
  edit(user: AdministrativeUser): void {
    if (!this.canManage(user) || this.saving) return;
    this.mode = 'edit'; this.editTarget = user; this.formError = null; this.success = null;
    this.form.reset({ nombre: user.nombre, apellido: user.apellido, correo: user.correo,
      password: '', rolId: user.rolId, estado: user.estado });
  }
  cancelForm(): void { if (!this.saving) { this.mode = null; this.form.reset(); this.formError = null; } }
  save(): void {
    if (!this.mode || this.saving || (this.mode === 'edit' && (!this.editTarget || !this.canManage(this.editTarget)))) return;
    this.form.markAllAsTouched();
    const { nombre, apellido, correo, password, rolId, estado } = this.form.getRawValue();
    const passwordLength = Array.from(password).length;
    if (this.form.invalid || !nombre.trim() || !apellido.trim() || !correo.trim()) {
      this.formError = 'Completa los campos obligatorios con datos válidos.'; return;
    }
    if (this.mode === 'create' && (passwordLength < 12 || passwordLength > 128 || new TextEncoder().encode(password).length > 72
      || !this.assignableRoles.some(role => role.id === Number(rolId)) || !this.states.includes(estado))) {
      this.formError = 'Selecciona un rol y usa una contraseña de 12 a 128 caracteres y máximo 72 bytes UTF-8.'; return;
    }
    const personal = { nombre: nombre.trim(), apellido: apellido.trim(), correo: correo.trim().toLowerCase() };
    const mode = this.mode;
    const operation = mode === 'create' ? this.api.createUser({ ...personal, password, rolId: Number(rolId), estado })
      : this.api.updateUser(this.editTarget!.id, personal);
    this.saving = true; this.formError = null; this.success = null;
    operation.pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); })).subscribe({
      next: saved => { this.upsert(saved); this.mode = null; this.form.reset(); this.success = mode === 'create' ? 'Usuario creado correctamente.' : 'Usuario actualizado correctamente.'; },
      error: (e: HttpErrorResponse) => { this.formError = this.message(e); },
    });
  }
  details(user: AdministrativeUser): void {
    this.detailLoading = true; this.selected = null; this.error = null;
    this.api.user(user.id).pipe(finalize(() => { this.detailLoading = false; this.cd.markForCheck(); })).subscribe({
      next: result => { this.selected = result; }, error: (e: HttpErrorResponse) => { this.error = this.message(e); },
    });
  }
  requestChange(user: AdministrativeUser, kind: 'estado' | 'rol', value: string): void {
    if (!this.canManage(user) || this.saving || !value || (kind === 'estado' && (!this.states.includes(value) || value === user.estado))
      || (kind === 'rol' && (!this.assignableRoles.some(role => String(role.id) === value) || value === String(user.rolId)))) return;
    this.pending = { user, kind, value }; this.error = null; this.success = null;
  }
  confirmChange(): void {
    const pending = this.pending;
    if (!pending || !this.canManage(pending.user) || this.saving || (pending.kind === 'rol' && !this.assignableRoles.some(role => String(role.id) === pending.value))) return;
    this.saving = true;
    const operation = pending.kind === 'estado' ? this.api.changeUserState(pending.user.id, { estado: pending.value })
      : this.api.changeUserRole(pending.user.id, { rolId: Number(pending.value) });
    operation.pipe(finalize(() => { this.saving = false; this.cd.markForCheck(); })).subscribe({
      next: updated => { this.upsert(updated); this.pending = null; this.success = 'Cambio aplicado correctamente.'; },
      error: (e: HttpErrorResponse) => { this.error = this.message(e); this.pending = null; },
    });
  }
  cancelChange(): void { if (!this.saving) this.pending = null; }
  roleName(id: string): string { return this.roles.find(role => String(role.id) === id)?.nombre ?? id; }
  private upsert(user: AdministrativeUser): void {
    this.users = [...this.users.filter(item => item.id !== user.id), user].sort((a, b) => a.id - b.id);
    if (this.selected?.id === user.id) this.selected = user;
  }
  private message(error: HttpErrorResponse): string {
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    if (error.status === 400) return 'Revisa los datos enviados; el servidor los rechazó.';
    if (error.status === 401) return 'La sesión no es válida. Inicia sesión nuevamente.';
    if (error.status === 403) return 'No tienes permiso para realizar esta operación.';
    if (error.status === 404) return 'El usuario o rol ya no está disponible. Actualiza la lista.';
    if (error.status === 409) return 'Existe un conflicto con el correo, el estado o la cuenta protegida.';
    return 'No se pudo completar la operación. Inténtalo de nuevo.';
  }
}
