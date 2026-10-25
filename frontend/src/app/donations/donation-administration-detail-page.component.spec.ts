import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { BasicAuthService } from '../auth/basic-auth.service';
import { DonationAdministrationDetailPageComponent } from './donation-administration-detail-page.component';

describe('DonationAdministrationDetailPageComponent', () => {
  let fixture: ComponentFixture<DonationAdministrationDetailPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
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
    const auth = TestBed.inject(BasicAuthService);
    auth.provideCredentials('admin', 'memory-only');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@example.test', rolCodigo: 'ADMIN', rolNombre: 'Admin', permisos: ['ACCESO_ADMINISTRAR'] });
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

  it('submits a partial receipt, stops saving, and reloads the donation after success', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    await fixture.whenStable();
    fixture.componentInstance.setReceiptAmount(9, '2.5');
    fixture.componentInstance.receiptLocation = 'Centro Norte';
    fixture.componentInstance.submitReceipt();
    expect(fixture.componentInstance.savingReceipt).toBe(true);
    const post = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.detalles).toEqual([{ recursoId: 9, cantidad: 2.5 }]);
    post.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    await fixture.whenStable();
    expect(fixture.componentInstance.savingReceipt).toBe(false);
    expect(fixture.componentInstance.receiptSuccess).toContain('correctamente');
    expect(fixture.componentInstance.donation?.detalles[0].cantidadRecibidaAcopio).toBe(2.5);
  });

  it('stops saving and displays a validation error when a receipt fails', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    await fixture.whenStable();
    fixture.componentInstance.setReceiptAmount(9, '12');
    fixture.componentInstance.submitReceipt();
    http.expectNone('/api/donaciones/administracion/42/recepciones');
    await fixture.whenStable();
    expect(fixture.componentInstance.savingReceipt).toBe(false);
    expect(fixture.componentInstance.receiptError).toContain('cantidad supera');
  });

  it('retries an unconfirmed receipt with the same durable identity after recreating the page', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    fixture.componentInstance.setReceiptAmount(9, '2.5');
    fixture.componentInstance.submitReceipt();
    const first = http.expectOne('/api/donaciones/administracion/42/recepciones');
    const payload = first.request.body;
    expect(payload.operacionId).toMatch(/^[0-9a-f-]{36}$/);
    first.flush({}, { status: 500, statusText: 'Server Error' });
    fixture.destroy();
    fixture = TestBed.createComponent(DonationAdministrationDetailPageComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    fixture.componentInstance.submitReceipt();
    const retry = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(retry.request.body).toEqual(payload);
    retry.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    expect(fixture.componentInstance.pendingReceipt).toBeNull();
    fixture.componentInstance.setReceiptAmount(9, '1');
    fixture.componentInstance.submitReceipt();
    const later = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(later.request.body.operacionId).not.toBe(payload.operacionId);
    later.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(3.5));
    await fixture.whenStable();
  });

  function donationResponse(received: number) {
    return {
      id: 42, codigoTrazabilidad: 'RS-42', fechaRegistro: '2026-04-02T10:00:00', fechaIngreso: null,
      estado: 'REGISTRADA', observaciones: null, donante: { id: 4, nombre: 'Donante' },
      emergenciaId: null, emergenciaNombre: null, centroAcopio: null,
      detalles: [{ recursoId: 9, recursoNombre: 'Arroz Blanco', cantidadDonada: 10, cantidadRecibidaAcopio: received,
        unidadCodigo: 'KG', unidadNombre: 'Kilogramo', unidadSimbolo: 'kg' }], movimientos: [],
    };
  }
});
