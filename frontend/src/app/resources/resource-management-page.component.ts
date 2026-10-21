import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { MeasurementUnit, ResourceItem, SaveResourceRequest } from './resource.model';
import { ResourceApiService } from './resource-api.service';
import { BasicAuthService } from '../auth/basic-auth.service';

interface ResourceForm {
  id: number | null;
  nombre: string;
  categoria: string;
  descripcion: string;
  unidadMedidaId: number | null;
}

@Component({
  selector: 'app-resource-management-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './resource-management-page.component.html',
  styleUrl: './resource-management-page.component.css',
})
export class ResourceManagementPageComponent implements OnInit {
  private readonly api = inject(ResourceApiService);
  private readonly auth = inject(BasicAuthService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly isAdmin = () => this.auth.hasPermission('ACCESO_ADMINISTRAR');

  resources: ResourceItem[] = [];
  units: MeasurementUnit[] = [];
  loading = true;
  saving = false;
  changingStateId: number | null = null;
  searchText = '';
  selectedCategory = '';
  error: string | null = null;
  success: string | null = null;
  formError: string | null = null;
  formOpen = false;
  form: ResourceForm = this.emptyForm();

  get categories(): string[] {
    return [...new Set(this.resources.map((resource) => resource.categoria))].sort((a, b) => a.localeCompare(b));
  }

  get filteredResources(): ResourceItem[] {
    const search = this.searchText.trim().toLocaleLowerCase();
    return this.resources.filter((resource) => {
      const nameMatches = !search || resource.nombre.toLocaleLowerCase().includes(search);
      const categoryMatches = !this.selectedCategory || resource.categoria === this.selectedCategory;
      return nameMatches && categoryMatches;
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    forkJoin({ resources: this.api.resources(), units: this.api.measurementUnits() }).pipe(
      finalize(() => {
        this.loading = false;
        this.changeDetector.markForCheck();
      }),
    ).subscribe({
      next: ({ resources, units }) => {
        this.resources = resources;
        this.units = units;
      },
      error: (error: HttpErrorResponse) => {
        this.error = this.errorMessage(error, 'No se pudo cargar el catálogo de recursos.');
      },
    });
  }

  nuevo(): void {
    this.form = this.emptyForm();
    this.formError = null;
    this.success = null;
    this.formOpen = true;
  }

  editar(resource: ResourceItem): void {
    this.form = {
      id: resource.id,
      nombre: resource.nombre,
      categoria: resource.categoria,
      descripcion: resource.descripcion ?? '',
      unidadMedidaId: resource.unidadMedida.id,
    };
    this.formError = null;
    this.success = null;
    this.formOpen = true;
  }

  cancelar(): void {
    this.formOpen = false;
    this.formError = null;
  }

  guardar(): void {
    this.formError = this.validateForm();
    if (this.formError) return;

    const request: SaveResourceRequest = {
      nombre: this.form.nombre.trim(),
      categoria: this.form.categoria.trim(),
      descripcion: this.form.descripcion.trim() || null,
      unidadMedidaId: this.form.unidadMedidaId!,
    };
    this.saving = true;
    this.success = null;
    const save = this.form.id === null ? this.api.create(request) : this.api.update(this.form.id, request);
    save.pipe(finalize(() => {
      this.saving = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (saved) => {
        this.resources = [...this.resources.filter((item) => item.id !== saved.id), saved]
          .sort((a, b) => a.nombre.localeCompare(b.nombre));
        this.formOpen = false;
        this.success = this.form.id === null ? 'Recurso registrado correctamente.' : 'Recurso actualizado correctamente.';
      },
      error: (error: HttpErrorResponse) => {
        this.formError = this.errorMessage(error, 'No se pudo guardar el recurso.');
      },
    });
  }

  toggleState(resource: ResourceItem): void {
    if (this.changingStateId !== null) return;
    this.changingStateId = resource.id;
    this.error = null;
    this.success = null;
    const estado = resource.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    this.api.changeState(resource.id, { estado }).pipe(finalize(() => {
      this.changingStateId = null;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (updated) => {
        this.resources = this.resources.map((item) => item.id === updated.id ? updated : item);
        this.success = estado === 'ACTIVO' ? 'Recurso activado.' : 'Recurso desactivado.';
      },
      error: (error: HttpErrorResponse) => {
        this.error = this.errorMessage(error, 'No se pudo cambiar el estado del recurso.');
      },
    });
  }

  private validateForm(): string | null {
    const name = this.form.nombre.trim();
    const category = this.form.categoria.trim();
    if (!name || !category || this.form.unidadMedidaId === null) {
      return 'Completa el nombre, la categoría y la unidad de medida.';
    }
    if (name.length > 100 || category.length > 100) {
      return 'El nombre y la categoría admiten como máximo 100 caracteres.';
    }
    if (new TextEncoder().encode(this.form.descripcion).length > 65_535) {
      return 'La descripción supera la capacidad admitida por la base de datos.';
    }
    if (!this.units.some((unit) => unit.id === this.form.unidadMedidaId)) {
      return 'Selecciona una unidad de medida disponible.';
    }
    return null;
  }

  private errorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) return 'No se pudo conectar con el servidor. Verifica tu conexión e inténtalo de nuevo.';
    if (error.status === 401) return 'Inicia sesión en Acceso para organizaciones para consultar el catálogo.';
    if (error.status === 403) return 'Tu cuenta no tiene permiso para administrar recursos.';
    if (error.status === 404) return 'El recurso o la unidad de medida seleccionada ya no está disponible.';
    if (error.status === 400) return 'Revisa los campos: el servidor rechazó los datos enviados.';
    return fallback;
  }

  private emptyForm(): ResourceForm {
    return { id: null, nombre: '', categoria: '', descripcion: '', unidadMedidaId: null };
  }
}
