import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { EmergencyBalanceComponent } from './emergency-balance.component';

describe('EmergencyBalanceComponent', () => {
  let fixture: ComponentFixture<EmergencyBalanceComponent>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EmergencyBalanceComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(EmergencyBalanceComponent); http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('emergencyId', 1); fixture.autoDetectChanges();
  });
  afterEach(() => http.verify());
  it('keeps resource quantities and units in separate rows', async () => {
    const amounts = { requerida: 10, recibidaAcopio: 7, asignada: 4, entregada: 1, disponible: 3,
      pendienteAsignar: 6, pendienteEntregar: 9, faltanteAbastecimiento: 3 };
    http.expectOne('/api/admin/emergencias/1/balance').flush([
      { ...amounts, recursoId: 1, recursoNombre: 'Agua', unidadCodigo: 'L' },
      { ...amounts, recursoId: 2, recursoNombre: 'Arroz', unidadCodigo: 'KG' },
    ]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('no hay suma entre recursos');
    expect(fixture.componentInstance.loading).toBe(false);
  });
  it('shows empty results and then a permission error when reloading', async () => {
    http.expectOne('/api/admin/emergencias/1/balance').flush([]);
    await fixture.whenStable(); expect(fixture.nativeElement.textContent).toContain('No hay necesidades ni donaciones');
    fixture.componentInstance.load();
    http.expectOne('/api/admin/emergencias/1/balance').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable(); expect(fixture.nativeElement.textContent).toContain('no tiene permiso');
  });
});
