import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { routes } from '../app.routes';
import { AuditPageComponent } from './audit-page.component';

describe('AuditPageComponent', () => {
  let fixture: ComponentFixture<AuditPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditPageComponent],
      providers: [provideRouter(routes), provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(AuditPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
  });

  afterEach(() => http.verify());

  it('ends loading and renders audit metadata while omitting arbitrary JSON fields', async () => {
    expect(fixture.componentInstance.loading).toBe(true);
    http.expectOne('/api/admin/auditoria?page=0&size=20').flush({
      content: [{ id: 5, fechaHora: '2026-05-02T11:30:00', accion: 'RECEPCION_DONACION',
        entidad: 'DONACION', idRegistro: '22', responsableNombre: 'Ana Ruiz' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, last: true,
    });
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('RECEPCION_DONACION');
    expect(fixture.nativeElement.textContent).toContain('Ana Ruiz');
    expect(fixture.nativeElement.textContent).not.toContain('datosNuevos');
  });

  it('shows an empty state for no events', async () => {
    http.expectOne('/api/admin/auditoria?page=0&size=20').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No hay eventos que coincidan');
  });

  it('sends filters and shows authorization failures after loading ends', async () => {
    http.expectOne('/api/admin/auditoria?page=0&size=20').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true,
    });
    await fixture.whenStable();
    fixture.componentInstance.action = 'RECEPCION_DONACION';
    fixture.componentInstance.entity = 'DONACION';
    fixture.componentInstance.recordId = '22';
    fixture.componentInstance.user = 'Ana';
    fixture.componentInstance.fromDate = '2026-05-01';
    fixture.componentInstance.toDate = '2026-05-02';
    fixture.componentInstance.load(0);
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/auditoria'
      && candidate.params.get('accion') === 'RECEPCION_DONACION'
      && candidate.params.get('entidad') === 'DONACION'
      && candidate.params.get('idRegistro') === '22'
      && candidate.params.get('usuario') === 'Ana'
      && candidate.params.get('desde') === '2026-05-01T00:00:00'
      && candidate.params.get('hasta') === '2026-05-02T23:59:59.999999999');
    request.flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('no tiene permiso');
    expect(fixture.nativeElement.textContent).not.toContain('Cargando eventos');
  });

  it('navigates to the next result page', async () => {
    http.expectOne('/api/admin/auditoria?page=0&size=20').flush({
      content: [{ id: 1, fechaHora: '2026-05-02T11:30:00', accion: 'TEST', entidad: 'DONACION',
        idRegistro: '22', responsableNombre: 'Ana Ruiz' }],
      page: 0, size: 20, totalElements: 21, totalPages: 2, last: false,
    });
    await fixture.whenStable();
    fixture.nativeElement.querySelector('.audit-pagination button:last-child').click();
    const request = http.expectOne('/api/admin/auditoria?page=1&size=20');
    request.flush({ content: [], page: 1, size: 20, totalElements: 21, totalPages: 2, last: true });
    await fixture.whenStable();
    expect(fixture.componentInstance.page).toBe(1);
  });
});
