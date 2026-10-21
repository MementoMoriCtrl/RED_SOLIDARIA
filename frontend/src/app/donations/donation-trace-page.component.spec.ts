import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../app.routes';
import { DonationTracePageComponent } from './donation-trace-page.component';
import { DonationTrace } from './donation-trace.model';

describe('DonationTracePageComponent', () => {
  let fixture: ComponentFixture<DonationTracePageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DonationTracePageComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(DonationTracePageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('allows a visitor without login to search by tracking code', () => {
    expect(fixture.nativeElement.querySelector('#tracking-code')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#login-username')).toBeNull();
    fixture.componentInstance.codigo = 'RS-TRACE-2';
    fixture.componentInstance.consultar();
    const request = http.expectOne('/api/donaciones/RS-TRACE-2');
    expect(request.request.method).toBe('GET');
    request.flush({
      codigoTrazabilidad: 'RS-TRACE-2', fechaRegistro: '2026-04-02T10:00:00', fechaIngreso: null,
      estado: 'REGISTRADA', centroAcopio: null, detalles: [], movimientos: [],
    });
  });

  it('renders the public donation, details and movement history without personal fields', () => {
    fixture.componentInstance.codigo = 'RS-TRACE-2';
    fixture.componentInstance.consultar();

    const response: DonationTrace = {
      codigoTrazabilidad: 'RS-TRACE-2',
      fechaRegistro: '2026-04-02T10:00:00',
      fechaIngreso: null,
      estado: 'REGISTRADA',
      centroAcopio: { nombre: 'Centro de acopio', ubicacion: 'Miraflores' },
      detalles: [{
        recursoNombre: 'Arroz',
        cantidadDonada: 12.5,
        cantidadRecibidaAcopio: null,
        unidadCodigo: 'KG',
        unidadNombre: 'Kilogramo',
        unidadSimbolo: 'kg',
      }],
      movimientos: [{
        estadoAnterior: null,
        estadoNuevo: 'REGISTRADA',
        fechaHora: '2026-04-02T10:00:00',
        ubicacion: 'Miraflores',
      }],
    };
    http.expectOne('/api/donaciones/RS-TRACE-2').flush(response);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('RS-TRACE-2');
    expect(page.textContent).toContain('Centro de acopio');
    expect(page.textContent).toContain('Arroz');
    expect(page.textContent).toContain('REGISTRADA');
    expect(page.textContent).not.toContain('null');
    expect(page.textContent).not.toContain('undefined');
  });

  it('shows not found for an unknown public tracking code', () => {
    fixture.componentInstance.codigo = 'RS-MISSING';
    fixture.componentInstance.consultar();
    http.expectOne('/api/donaciones/RS-MISSING').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No encontramos una donación');
  });

  it('links to the separate organization access route', () => {
    const link = fixture.nativeElement.querySelector('a.org-login-link') as HTMLAnchorElement;
    expect(link.textContent).toContain('Acceso para organizaciones');
    expect(link.getAttribute('href')).toBe('/acceso-organizaciones');
  });
});
