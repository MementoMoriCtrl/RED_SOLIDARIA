import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuditApiService } from './audit-api.service';

describe('AuditApiService', () => {
  let service: AuditApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuditApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('requests server-side paging with supplied filters', () => {
    service.search({ accion: 'RECEPCION_DONACION', entidad: 'DONACION', usuario: ' Ana ',
      desde: '2026-05-01T00:00:00', hasta: '2026-05-02T23:59:59' }, 1, 10).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/auditoria');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('accion')).toBe('RECEPCION_DONACION');
    expect(request.request.params.get('usuario')).toBe('Ana');
    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0, last: true });
  });
});
