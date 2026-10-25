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
    const requestPayload = { ...payload, operacionId: 'f9eec73a-d766-4683-9094-e7cb6253aa30' };
    service.registerReceipt(22, requestPayload).subscribe();
    const request = http.expectOne('/api/donaciones/administracion/22/recepciones');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestPayload);
    request.flush({ donacionId: 22, estado: 'REGISTRADA' });
  });
});
