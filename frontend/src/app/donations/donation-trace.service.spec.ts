import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DonationTrace } from './donation-trace.model';
import { DonationTraceService } from './donation-trace.service';

describe('DonationTraceService', () => {
  let service: DonationTraceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DonationTraceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests a tracking code from the relative donation endpoint without inventing credentials', () => {
    const response: DonationTrace = {
      codigoTrazabilidad: 'RS-TRACE-2',
      fechaRegistro: '2026-04-02T10:00:00',
      fechaIngreso: null,
      estado: 'REGISTRADA',
      centroAcopio: null,
      detalles: [],
      movimientos: [],
    };
    let result: DonationTrace | undefined;

    service.findByTrackingCode(' RS-TRACE-2 ').subscribe((value) => result = value);

    const request = http.expectOne('/api/donaciones/RS-TRACE-2');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(response);
    expect(result).toEqual(response);
  });
});
