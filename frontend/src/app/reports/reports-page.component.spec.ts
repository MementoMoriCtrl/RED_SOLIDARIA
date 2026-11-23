import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ReportsPageComponent } from './reports-page.component';

describe('ReportsPageComponent', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReportsPageComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('shows event quantities separately and paginates with date filters', () => {
    const fixture = TestBed.createComponent(ReportsPageComponent); fixture.detectChanges();
    http.expectOne('/api/admin/reportes/entregas?page=0&size=20').flush({ content: [{ id: 1, fecha: '2026-01-01T10:00:00',
      emergencia: 'Norte', zona: 'Zona', necesidadId: 1, recurso: 'Agua', unidad: 'L', codigoDonacion: 'RS-1',
      cantidadAsignada: 2, cantidadEntregada: 1 }], page: 0, size: 20, totalElements: 21, totalPages: 2, last: false });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Agua (L)');
    expect(fixture.nativeElement.textContent).toContain('incrementos');
    fixture.componentInstance.from = '2026-01-01'; fixture.componentInstance.to = '2026-01-31';
    fixture.componentInstance.load(1);
    const next = http.expectOne('/api/admin/reportes/entregas?page=1&size=20&fechaDesde=2026-01-01&fechaHasta=2026-01-31');
    next.flush({ content: [], page: 1, size: 20, totalElements: 21, totalPages: 2, last: true });
    expect(fixture.componentInstance.result.page).toBe(1);
  });
  it('rejects reversed dates without a request', () => {
    const fixture = TestBed.createComponent(ReportsPageComponent); fixture.detectChanges();
    http.expectOne('/api/admin/reportes/entregas?page=0&size=20').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true });
    fixture.componentInstance.from = '2026-02-01'; fixture.componentInstance.to = '2026-01-01'; fixture.componentInstance.load();
    expect(fixture.componentInstance.error).toContain('fecha inicial');
    http.expectNone('/api/admin/reportes/entregas?page=0&size=20&fechaDesde=2026-02-01&fechaHasta=2026-01-01');
  });
});
