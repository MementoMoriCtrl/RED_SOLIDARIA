import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { EmergencyBalanceComponent } from './emergency-balance.component';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { routes } from '../app.routes';
import { EmergencyManagementPageComponent } from './emergency-management-page.component';

const page = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true };
@Component({ selector: 'app-emergency-balance', standalone: true, template: '' })
class BalanceStub { @Input() emergencyId!: number; }
const detail = { id: 4, nombre: 'Inundación Norte', tipo: 'INUNDACION', descripcion: null,
  fechaInicio: '2026-06-01', fechaFin: null, estado: 'ACTIVA', creadoEn: null, actualizadoEn: null,
  zonas: [{ id: 9, nombre: 'Zona Norte', ubicacion: null, descripcion: null, creadoEn: null }] };

describe('EmergencyManagementPageComponent', () => {
  let fixture: ComponentFixture<EmergencyManagementPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmergencyManagementPageComponent],
      providers: [provideRouter(routes), provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting()],
    }).overrideComponent(EmergencyManagementPageComponent, {
      remove: { imports: [EmergencyBalanceComponent] }, add: { imports: [BalanceStub] },
    }).compileComponents();
    fixture = TestBed.createComponent(EmergencyManagementPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
  });
  afterEach(() => http.verify());

  it('ends loading and displays an empty state', async () => {
    expect(fixture.componentInstance.loading).toBe(true);
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No hay emergencias que coincidan');
  });

  it('creates an emergency with the submitted fields and refreshes the list', async () => {
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.newEmergency();
    component.name = 'Inundación Norte';
    component.type = 'INUNDACION';
    component.startDate = '2026-06-01';
    component.createState = 'ACTIVA';
    component.saveEmergency();
    const request = http.expectOne('/api/admin/emergencias');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ nombre: 'Inundación Norte', tipo: 'INUNDACION', descripcion: null,
      fechaInicio: '2026-06-01', fechaFin: null, estado: 'ACTIVA' });
    request.flush({ id: 4, nombre: 'Inundación Norte', tipo: 'INUNDACION', descripcion: null,
      fechaInicio: '2026-06-01', fechaFin: null, estado: 'ACTIVA', creadoEn: null, actualizadoEn: null, zonas: [] });
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    await fixture.whenStable();
    expect(component.saving).toBe(false);
    expect(component.success).toContain('guardaron correctamente');
  });

  it('ends loading and presents a server error', async () => {
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No se pudo completar');
  });

  it('clears the previous emergency and zone when starting a new emergency', async () => {
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    const component = fixture.componentInstance;
    component.editEmergency(detail);
    http.expectOne('/api/admin/emergencias/4').flush(detail);
    component.editZone(detail.zonas[0]);
    component.newEmergency();
    expect(component.selected).toBeNull();
    expect(component.zoneId).toBeNull();
    expect(component.zoneName).toBe('');
    expect(component.nextState).toBe('');
    component.saveState();
    http.expectNone('/api/admin/emergencias/4/estado');
    await fixture.whenStable();
  });

  it('keeps the active emergency while detail or a mutation is pending and prevents duplicate writes', async () => {
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    const component = fixture.componentInstance;
    component.editEmergency(detail);
    component.newEmergency();
    component.editEmergency({ ...detail, id: 5 });
    http.expectNone('/api/admin/emergencias/5');
    http.expectOne('/api/admin/emergencias/4').flush(detail);
    component.nextState = 'CERRADA';
    component.saveState();
    component.saveState();
    component.newEmergency();
    component.closeForm();
    expect(component.editingId).toBe(4);
    const request = http.expectOne('/api/admin/emergencias/4/estado');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ estado: 'CERRADA' });
    request.flush({ ...detail, estado: 'CERRADA' });
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    await fixture.whenStable();
    expect(component.selected?.estado).toBe('CERRADA');
  });

  it('updates a zone under its emergency and reloads the detail', async () => {
    http.expectOne('/api/admin/emergencias?page=0&size=20').flush(page);
    const component = fixture.componentInstance;
    component.editEmergency(detail);
    http.expectOne('/api/admin/emergencias/4').flush(detail);
    component.editZone(detail.zonas[0]);
    component.zoneName = 'Zona corregida';
    component.saveZone();
    const request = http.expectOne('/api/admin/emergencias/4/zonas/9');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.nombre).toBe('Zona corregida');
    request.flush({ ...detail.zonas[0], nombre: 'Zona corregida' });
    http.expectOne('/api/admin/emergencias/4').flush(detail);
    await fixture.whenStable();
    expect(component.zoneId).toBeNull();
    expect(component.loadingDetail).toBe(false);
  });
});
