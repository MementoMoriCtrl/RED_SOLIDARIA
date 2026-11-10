import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BasicAuthService } from '../auth/basic-auth.service';
import { LogisticsPageComponent } from './logistics-page.component';
import { LogisticsStock } from './logistics-api.service';
const stock: LogisticsStock = { detalleDonacionId: 10, codigoTrazabilidad: 'RS-1', centroAcopioId: 1, centroAcopio: 'Acopio Norte',
  recurso: 'Arroz', unidad: 'KG', recibidaAcopio: 100, trasladada: 0, recibidaDistribucion: 0, asignada: 0,
  disponibleAcopio: 100, disponibleDistribucion: 0, enTransito: 0, diferencia: 0, reservaHistoricaSinDistribucion: 0 };
describe('LogisticsPageComponent', () => {
  let fixture: ComponentFixture<LogisticsPageComponent>; let http: HttpTestingController;
  const base = '/api/admin/logistica';
  const key = 'red-solidaria.logistics.admin@test';
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [LogisticsPageComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const auth = TestBed.inject(BasicAuthService);
    auth.provideCredentials('admin@test', 'unused-test-password');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@test', rolCodigo: 'ADMIN', rolNombre: 'Admin', permisos: ['ACCESO_ADMINISTRAR'] });
    http = TestBed.inject(HttpTestingController); fixture = TestBed.createComponent(LogisticsPageComponent);
    fixture.detectChanges(); flushLoad(); await fixture.whenStable();
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });
  function flushLoad() {
    http.expectOne(`${base}/centros-distribucion`).flush([{ id: 2, nombre: 'Distribución', ubicacion: 'Piura', descripcion: null }]);
    http.expectOne(`${base}/existencias?page=0`).flush([stock]);
    http.expectOne(`${base}/traslados?page=0`).flush([]);
  }
  it('separates collection stock from distribution and posts an immutable UUID on retry', async () => {
    expect(fixture.nativeElement.textContent).toContain('Libre distribución');
    const page = fixture.componentInstance; page.selectSource(stock); page.destination = 2; page.quantity = 80; page.dispatch();
    const initial = http.expectOne(`${base}/traslados`); const request = initial.request.body;
    expect(request.detalleDonacionId).toBe(10); expect(request.cantidad).toBe(80); expect(request.operacionId).toMatch(/^[0-9a-f-]{36}$/);
    initial.error(new ProgressEvent('network')); await fixture.whenStable();
    expect(page.pending).not.toBeNull(); expect(JSON.parse(sessionStorage.getItem(key)!).request).toEqual(request);
    page.quantity = 5; page.retry();
    const retry = http.expectOne(`${base}/traslados`); expect(retry.request.body).toEqual(request); retry.flush({ operacionId: request.operacionId });
    flushLoad(); await fixture.whenStable(); expect(page.pending).toBeNull(); expect(sessionStorage.getItem(key)).toBeNull();
  });
  it('recovers the same pending operation when the page is recreated', async () => {
    const page = fixture.componentInstance; page.selectSource(stock); page.destination = 2; page.quantity = 20; page.dispatch();
    const first = http.expectOne(`${base}/traslados`); const request = first.request.body; first.flush({}, { status: 500, statusText: 'Error' });
    fixture.destroy(); fixture = TestBed.createComponent(LogisticsPageComponent); fixture.detectChanges(); flushLoad(); await fixture.whenStable();
    fixture.componentInstance.retry(); const retry = http.expectOne(`${base}/traslados`); expect(retry.request.body).toEqual(request);
    retry.flush({}); flushLoad(); await fixture.whenStable();
  });
  it('requires an explanation for a short receipt and preserves its UUID after connection failure', async () => {
    const page = fixture.componentInstance;
    page.selectTransfer({ operacionId: 'shipment', detalleDonacionId: 10, origen: 'Acopio', destino: 'Distribución', recurso: 'Arroz', unidad: 'KG',
      cantidadEnviada: 80, cantidadRecibida: null, estado: 'EN_TRANSITO', fechaSalida: '', fechaRecepcion: null,
      responsableSalida: 1, responsableRecepcion: null, observaciones: null, observacionesRecepcion: null });
    page.received = 75; page.receive(); http.expectNone(`${base}/traslados/shipment/recepcion`); expect(page.error).toContain('diferencia');
    page.receiptNotes = 'Faltan 5 kg'; page.receive(); const receive = http.expectOne(`${base}/traslados/shipment/recepcion`);
    expect(receive.request.body.cantidad).toBe(75); const body = receive.request.body; receive.error(new ProgressEvent('network')); await fixture.whenStable();
    page.retry(); const retry = http.expectOne(`${base}/traslados/shipment/recepcion`); expect(retry.request.body).toEqual(body);
    retry.flush({}); flushLoad(); await fixture.whenStable();
  });
});
