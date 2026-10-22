import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { DonationAdministrationDetailPageComponent } from './donation-administration-detail-page.component';

describe('DonationAdministrationDetailPageComponent', () => {
  let fixture: ComponentFixture<DonationAdministrationDetailPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DonationAdministrationDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '42' } } } },
        provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DonationAdministrationDetailPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
  });

  afterEach(() => http.verify());

  it('loads the private donation detail and movement history', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush({
      id: 42, codigoTrazabilidad: 'RS-42', fechaRegistro: '2026-04-02T10:00:00', fechaIngreso: null,
      estado: 'REGISTRADA', observaciones: 'Ingreso inicial', donante: { id: 4, nombre: 'Donante reservado' },
      emergenciaId: 5, emergenciaNombre: 'Emergencia Norte', centroAcopio: null,
      detalles: [{ recursoId: 9, recursoNombre: 'Arroz Blanco', cantidadDonada: 10, cantidadRecibidaAcopio: 0,
        unidadCodigo: 'KG', unidadNombre: 'Kilogramo', unidadSimbolo: 'kg' }],
      movimientos: [{ estadoAnterior: null, estadoNuevo: 'REGISTRADA', fechaHora: '2026-04-02T10:00:00',
        ubicacion: 'Zona Norte', observaciones: null, responsableNombre: 'Ana Admin' }],
    });
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Donante reservado');
    expect(fixture.nativeElement.textContent).toContain('Arroz Blanco');
    expect(fixture.nativeElement.textContent).toContain('Ana Admin');
    expect(fixture.nativeElement.textContent).toContain('Ingreso inicial');
  });

  it('ends loading and displays not found errors', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush({}, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando detalle');
    expect(fixture.nativeElement.textContent).toContain('No encontramos la donación solicitada');
  });
});
