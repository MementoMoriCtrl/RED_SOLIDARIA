import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { routes } from '../app.routes';
import { MeasurementUnit, ResourceItem } from './resource.model';
import { ResourceManagementPageComponent } from './resource-management-page.component';

const units: MeasurementUnit[] = [{ id: 2, codigo: 'L', nombre: 'Litro', simbolo: 'L' }];
const initialResources: ResourceItem[] = [
  { id: 4, nombre: 'Agua', categoria: 'Bebidas', descripcion: 'Agua potable', estado: 'ACTIVO', unidadMedida: units[0] },
  { id: 5, nombre: 'Arroz', categoria: 'Alimentos', descripcion: null, estado: 'ACTIVO', unidadMedida: units[0] },
];

describe('ResourceManagementPageComponent', () => {
  let fixture: ComponentFixture<ResourceManagementPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResourceManagementPageComponent],
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([basicAuthInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ResourceManagementPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
    http.expectOne('/api/recursos').flush(initialResources);
    http.expectOne('/api/unidades-medida').flush(units);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('ends catalog loading and renders the response after requests complete', () => {
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando catÃ¡logo de recursos');
    expect(fixture.nativeElement.textContent).toContain('Arroz');
  });

  it('ends catalog loading and shows an error when a request fails', async () => {
    fixture.componentInstance.load();
    http.expectOne('/api/unidades-medida').flush(units);
    http.expectOne('/api/recursos').flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando catÃ¡logo de recursos');
    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar');
  });

  it('filters the catalog by the existing name and category fields', () => {
    const component = fixture.componentInstance;
    component.searchText = 'agua';
    expect(component.filteredResources.map((resource) => resource.id)).toEqual([4]);
    component.searchText = '';
    component.selectedCategory = 'Alimentos';
    expect(component.filteredResources.map((resource) => resource.id)).toEqual([5]);
  });

  it('validates required fields before sending a create request', () => {
    const component = fixture.componentInstance;
    component.nuevo();
    component.form.categoria = 'Bebidas';
    component.form.unidadMedidaId = 2;
    component.guardar();
    expect(component.formError).toContain('nombre');
    http.expectNone({ method: 'POST', url: '/api/recursos' });
  });

  it('validates the description against the MySQL TEXT byte capacity', () => {
    const component = fixture.componentInstance;
    component.nuevo();
    component.form.nombre = 'Agua';
    component.form.categoria = 'Bebidas';
    component.form.unidadMedidaId = 2;
    component.form.descripcion = 'á'.repeat(32_768);
    component.guardar();
    expect(component.formError).toContain('capacidad admitida');
    http.expectNone({ method: 'POST', url: '/api/recursos' });
  });

  it('creates a resource and updates its state through the backend', async () => {
    const component = fixture.componentInstance;
    component.nuevo();
    component.form.nombre = '  Botiquín  ';
    component.form.categoria = 'Salud';
    component.form.unidadMedidaId = 2;
    component.guardar();
    const body = { id: 6, nombre: 'Botiquín', categoria: 'Salud', descripcion: null, estado: 'ACTIVO', unidadMedida: units[0] };
    const create = http.expectOne('/api/recursos');
    expect(create.request.body).toEqual({ nombre: 'Botiquín', categoria: 'Salud', descripcion: null, unidadMedidaId: 2 });
    create.flush(body);
    await fixture.whenStable();
    expect(component.saving).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Guardando');
    expect(component.success).toContain('registrado');
    expect(component.resources.some((resource) => resource.id === 6)).toBe(true);

    component.toggleState(body);
    const change = http.expectOne('/api/recursos/6/estado');
    expect(change.request.body).toEqual({ estado: 'INACTIVO' });
    change.flush({ ...body, estado: 'INACTIVO' });
    await fixture.whenStable();
    expect(component.changingStateId).toBeNull();
    expect(component.resources.find((resource) => resource.id === 6)?.estado).toBe('INACTIVO');
  });

  it('ends saving and keeps the edit form open with an error when update fails', async () => {
    const component = fixture.componentInstance;
    component.editar(initialResources[1]);
    component.form.nombre = 'Arroz actualizado';
    component.guardar();
    expect(component.saving).toBe(true);
    http.expectOne('/api/recursos/5').flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(component.saving).toBe(false);
    expect(component.formOpen).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Guardando');
    expect(fixture.nativeElement.textContent).toContain('No se pudo guardar el recurso');
  });

  it('reports server authorization errors without changing the catalog', async () => {
    const component = fixture.componentInstance;
    component.toggleState(initialResources[0]);
    http.expectOne('/api/recursos/4/estado').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(component.changingStateId).toBeNull();
    expect(component.error).toContain('no tiene permiso');
    expect(component.resources[0].estado).toBe('ACTIVO');
  });
});
