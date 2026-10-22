import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DonationAdminService } from './donation-admin.service';

describe('DonationAdminService receipts', () => {
  let service: DonationAdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DonationAdminService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('posts the partial receipt to the protected administrative endpoint', () => {
    const payload = { detalles: [{ recursoId: 8, cantidad: 2.5 }], ubicacion: 'Centro Norte', observaciones: 'Recepción parcial' };
    service.registerReceipt(22, payload).subscribe();
    const request = http.expectOne('/api/donaciones/administracion/22/recepciones');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ donacionId: 22, estado: 'REGISTRADA' });
  });
});
