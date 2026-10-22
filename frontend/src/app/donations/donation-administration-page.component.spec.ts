import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { routes } from '../app.routes';
import { DonationAdministrationPageComponent } from './donation-administration-page.component';

describe('DonationAdministrationPageComponent', () => {
  let fixture: ComponentFixture<DonationAdministrationPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DonationAdministrationPageComponent],
      providers: [provideRouter(routes), provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(DonationAdministrationPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
  });

  afterEach(() => http.verify());

  it('ends loading and renders administrative donation rows', async () => {
    const request = http.expectOne('/api/donaciones/administracion/listado');
    expect(fixture.componentInstance.loading).toBe(true);
    request.flush([{
      id: 3, codigoTrazabilidad: 'RS-3', fechaRegistro: '2026-04-02T10:00:00', estado: 'REGISTRADA',
      donante: { id: 2, nombre: 'Donante' }, emergenciaId: null, emergenciaNombre: null,
      centroAcopio: { id: 1, nombre: 'Centro', ubicacion: 'Surco' },
    }]);
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('RS-3');
    expect(fixture.nativeElement.textContent).toContain('REGISTRADA');
    expect(fixture.nativeElement.querySelector('a.detail-link')?.getAttribute('href')).toBe('/admin/donaciones/3');
  });

  it('shows an empty state when the filtered result has no donations', async () => {
    http.expectOne('/api/donaciones/administracion/listado').flush([]);
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No hay donaciones que coincidan');
  });

  it('sends the selected filters and reports server errors', async () => {
    http.expectOne('/api/donaciones/administracion/listado').flush([]);
    await fixture.whenStable();
    fixture.componentInstance.codigo = 'RS-FILTER';
    fixture.componentInstance.estado = 'RECIBIDA';
    fixture.componentInstance.load();
    const request = http.expectOne((candidate) => candidate.url === '/api/donaciones/administracion/listado'
      && candidate.params.get('codigo') === 'RS-FILTER' && candidate.params.get('estado') === 'RECIBIDA');
    request.flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando donaciones');
    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar el listado');
  });
});
