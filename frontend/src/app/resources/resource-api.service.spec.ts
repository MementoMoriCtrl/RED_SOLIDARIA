import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ResourceApiService } from './resource-api.service';

describe('ResourceApiService', () => {
  let api: ResourceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ResourceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads resources and measurement units from their API endpoints', () => {
    api.resources().subscribe();
    api.measurementUnits().subscribe();
    http.expectOne('/api/recursos').flush([]);
    http.expectOne('/api/unidades-medida').flush([]);
  });

  it('creates, updates, and changes resource state using the REST routes', () => {
    const request = { nombre: 'Agua', categoria: 'Bebidas', descripcion: null, unidadMedidaId: 2 };
    api.create(request).subscribe();
    http.expectOne({ method: 'POST', url: '/api/recursos' }).flush({});
    api.update(4, request).subscribe();
    http.expectOne({ method: 'PUT', url: '/api/recursos/4' }).flush({});
    api.changeState(4, { estado: 'INACTIVO' }).subscribe();
    http.expectOne({ method: 'PATCH', url: '/api/recursos/4/estado' }).flush({});
  });
});
