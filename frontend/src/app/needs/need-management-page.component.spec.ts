import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { NeedManagementPageComponent } from './need-management-page.component';
import { NeedItem, DonationSource } from './need.model';

const endpoint = '/api/admin/emergencias/1/zonas/2/necesidades';
const need: NeedItem = { id: 4, zonaId: 2, recursoId: 3, recursoNombre: 'Arroz', unidad: 'KG',
  prioridad: 'ALTA', estado: 'PENDIENTE', requerida: 10, asignada: 4, entregada: 1, pendienteAsignar: 6, pendienteEntregar: 9 };
const source: DonationSource = { detalleDonacionId: 5, codigoTrazabilidad: 'RS-TEST', estado: 'REGISTRADA',
  recibidaAcopio: 7, asignadaDonacion: 5, disponible: 2, asignadaNecesidad: 4, entregadaNecesidad: 1, habilitada: true };
const page = { content: [need], totalElements: 1, totalPages: 1, page: 0 };

describe('NeedManagementPageComponent', () => {
  let fixture: ComponentFixture<NeedManagementPageComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NeedManagementPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1', zoneId: '2' }) } } }],
    }).compileComponents();
    fixture = TestBed.createComponent(NeedManagementPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
  });
  afterEach(() => http.verify());
  function flushInitial(empty = false) {
    http.expectOne('/api/recursos').flush([{ id: 3, nombre: 'Arroz', estado: 'ACTIVO', unidadMedida: { codigo: 'KG' } }]);
    http.expectOne('/api/admin/emergencias/1').flush({ nombre: 'Emergencia de prueba', zonas: [{ id: 2, nombre: 'Zona de prueba' }] });
    http.expectOne(`${endpoint}?page=0`).flush(empty ? { ...page, content: [], totalElements: 0, totalPages: 0 } : page);
  }
  function selectSource() {
    fixture.componentInstance.select(need);
    http.expectOne('/api/admin/necesidades/4/donaciones?page=0').flush({ content: [source], totalElements: 1, totalPages: 1, page: 0 });
    fixture.componentInstance.selectSource(source);
  }

  it('renders balances and the actual emergency and zone after loading', async () => {
    expect(fixture.componentInstance.loading).toBe(true);
    flushInitial();
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Emergencia de prueba / Zona de prueba');
    expect(fixture.nativeElement.textContent).toContain('Una recepción en acopio no es una entrega');
    expect(fixture.componentInstance.needs.content[0].pendienteEntregar).toBe(9);
  });

  it('shows the empty state', async () => {
    flushInitial(true);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay necesidades registradas');
  });

  it('validates decimal precision and submits a need under the selected zone', async () => {
    flushInitial();
    const component = fixture.componentInstance;
    component.resourceId = 3; component.quantity = 0.0001; component.priority = 'ALTA';
    component.create();
    http.expectNone(request => request.method === 'POST');
    component.quantity = 2.5;
    component.create();
    const request = http.expectOne(endpoint);
    expect(request.request.body).toEqual({ recursoId: 3, cantidad: 2.5, prioridad: 'ALTA', observaciones: null });
    request.flush(need);
    http.expectOne(`${endpoint}?page=0`).flush(page);
    await fixture.whenStable();
    expect(component.saving).toBe(false);
    expect(component.success).toBe('Necesidad registrada.');
  });

  it('uses received stock separately and sends cumulative delivery totals only once while saving', async () => {
    flushInitial(); selectSource();
    const component = fixture.componentInstance;
    expect(component.assigned).toBe(4);
    expect(component.delivered).toBe(1);
    component.delivered = 3;
    component.saveDelivery(); component.saveDelivery();
    const request = http.expectOne('/api/admin/necesidades/4/donaciones/5/entrega');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ totalAsignado: 4, totalEntregado: 3 });
    request.flush({ entregaId: 9, totalAsignado: 4, totalEntregado: 3, repetida: false });
    http.expectOne(`${endpoint}?page=0`).flush({ ...page, content: [{ ...need, entregada: 3, pendienteEntregar: 7 }] });
    await fixture.whenStable();
    expect(component.needs.content[0].pendienteEntregar).toBe(7);
    expect(component.source).toBeNull();
  });

  it('rejects delivered totals above assigned without sending a request', async () => {
    flushInitial(); selectSource();
    fixture.componentInstance.delivered = 8;
    fixture.componentInstance.saveDelivery();
    http.expectNone(request => request.method === 'PUT');
    await fixture.whenStable();
    expect(fixture.componentInstance.error).toContain('entregado no supera asignado');
  });

  it('preserves totals after an unconfirmed response and can resend the exact same totals', async () => {
    flushInitial(); selectSource();
    const component = fixture.componentInstance;
    component.delivered = 2;
    component.saveDelivery();
    const first = http.expectOne('/api/admin/necesidades/4/donaciones/5/entrega');
    const payload = first.request.body;
    first.flush({}, { status: 500, statusText: 'Server Error' });
    expect(component.saving).toBe(false);
    component.saveDelivery();
    const retry = http.expectOne('/api/admin/necesidades/4/donaciones/5/entrega');
    expect(retry.request.body).toEqual(payload);
    retry.flush({ entregaId: null, totalAsignado: 4, totalEntregado: 2, repetida: true });
    http.expectOne(`${endpoint}?page=0`).flush(page);
    await fixture.whenStable();
    expect(component.success).toContain('no se duplicó');
  });

  it('ends source loading and displays an authorization error', async () => {
    flushInitial();
    fixture.componentInstance.select(need);
    http.expectOne('/api/admin/necesidades/4/donaciones?page=0').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(fixture.componentInstance.loadingSources).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Tu cuenta no tiene permiso');
  });
});
