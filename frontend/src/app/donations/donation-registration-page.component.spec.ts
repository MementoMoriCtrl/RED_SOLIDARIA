import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BasicAuthService } from '../auth/basic-auth.service';
import { DonationRegistrationPageComponent } from './donation-registration-page.component';

describe('DonationRegistrationPageComponent', () => {
  let fixture: ComponentFixture<DonationRegistrationPageComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DonationRegistrationPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { donationPage: 'registration' } } } }],
    }).compileComponents();
    const auth = TestBed.inject(BasicAuthService);
    auth.provideCredentials('fixture', 'in-memory');
    auth.markAuthenticated({ nombre: 'Fixture', apellido: 'Test', correo: 'fixture@test', rolCodigo: 'TEST', rolNombre: 'Test',
      permisos: ['DONACION_REGISTRAR', 'ACCESO_ADMINISTRAR'] });
    fixture = TestBed.createComponent(DonationRegistrationPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.autoDetectChanges();
    http.expectOne('/api/recursos').flush([{ id: 3, nombre: 'Arroz', estado: 'ACTIVO', unidadMedida: { codigo: 'KG' } }]);
    for (const kind of ['donantes', 'centros', 'emergencias']) {
      http.expectOne(`/api/donaciones/catalogos/${kind}?texto=&page=0`).flush({ content: [{ id: 1, nombre: 'Fixture', ubicacion: null }], page: 0, totalPages: 2 });
    }
    await fixture.whenStable();
  });
  afterEach(() => { vi.useRealTimers(); http.verify(); });
  async function openAdmin(): Promise<void> {
    fixture.destroy();
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    fixture = TestBed.createComponent(DonationRegistrationPageComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/donaciones/catalogos/donantes?texto=&page=0')
      .flush({ content: [{ id: 1, nombre: 'Donante inicial' }], page: 0, totalPages: 2 });
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=0')
      .flush({ content: [{ id: 2, nombre: 'Centro inicial', ubicacion: 'Lima' }], page: 0, totalPages: 2 });
    http.expectOne('/api/unidades-medida').flush([{ id: 1, nombre: 'Kilogramo', codigo: 'KG', simbolo: 'kg' }]);
    await fixture.whenStable();
  }
  async function clickTab(tab: string): Promise<void> {
    fixture.nativeElement.querySelector(`#tab-${tab}`).click();
    await fixture.whenStable();
  }
  function enter(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector);
    input.value = value; input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  it('requires a destination and unique resources, then posts to the existing donation endpoint', async () => {
    const component = fixture.componentInstance;
    component.donorId = 1; component.lines = [{ recursoId: 3, cantidad: 2.5 }];
    component.register();
    http.expectNone('/api/donaciones');
    component.emergencyId = 1;
    component.lines.push({ recursoId: 3, cantidad: 1 });
    component.register(); http.expectNone('/api/donaciones');
    component.lines.pop(); component.register(); component.register();
    const request = http.expectOne('/api/donaciones');
    expect(request.request.body).toEqual({ donanteId: 1, emergenciaId: 1, centroAcopioId: null,
      ubicacion: '', observaciones: '', detalles: [{ recursoId: 3, cantidad: 2.5 }] });
    request.flush({ id: 5, codigoTrazabilidad: 'RS-FIXTURE' });
    await fixture.whenStable();
    expect(component.saving).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Aún no se ha recibido ni entregado');
  });
  it('loads another catalog page without dropping previous choices', async () => {
    fixture.componentInstance.search('donantes', true);
    http.expectOne('/api/donaciones/catalogos/donantes?texto=&page=1').flush({ content: [{ id: 2, nombre: 'Other' }], page: 1, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.componentInstance.catalogs.donantes.content.map(item => item.id)).toEqual([1, 2]);
    fixture.componentInstance.donorId = 2;
    fixture.componentInstance.searchText.donantes = 'Nuevo filtro';
    fixture.componentInstance.search('donantes', true);
    http.expectOne('/api/donaciones/catalogos/donantes?texto=Nuevo%20filtro&page=0')
      .flush({ content: [], page: 0, totalPages: 0 });
    await fixture.whenStable();
    expect(fixture.componentInstance.donorId).toBeNull();
    expect(fixture.componentInstance.catalogs.donantes.content).toEqual([]);
  });
  it('adds a donor and selects the persisted catalog record', async () => {
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    const component = fixture.componentInstance;
    component.catalogName = 'Nuevo donante'; component.createCatalog();
    const request = http.expectOne('/api/admin/catalogos-donaciones/donantes');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 9, nombre: 'Nuevo donante', ubicacion: null });
    await fixture.whenStable(); expect(component.donorId).toBe(9);
  });
  it('blocks blind retries after an unconfirmed registration', async () => {
    const component = fixture.componentInstance;
    component.donorId = 1; component.emergencyId = 1; component.lines = [{ recursoId: 3, cantidad: 1 }];
    component.register();
    http.expectOne('/api/donaciones').flush({}, { status: 500, statusText: 'Server Error' });
    component.register(); http.expectNone('/api/donaciones');
    await fixture.whenStable(); expect(component.registrationUnconfirmed).toBe(true);
  });
  it('creates a unit without changing existing resource units', async () => {
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    const component = fixture.componentInstance;
    component.unitCode = 'KG'; component.unitName = 'Kilogramo'; component.unitSymbol = 'kg'; component.createUnit();
    const request = http.expectOne('/api/admin/catalogos-donaciones/unidades');
    expect(request.request.body).toEqual({ codigo: 'KG', nombre: 'Kilogramo', simbolo: 'kg' });
    request.flush({ id: 7, codigo: 'KG', nombre: 'Kilogramo', simbolo: 'kg' });
    http.expectOne('/api/unidades-medida').flush([{ id: 7, codigo: 'KG', nombre: 'Kilogramo', simbolo: 'kg' }]);
    await fixture.whenStable(); expect(component.success).toContain('Unidad registrada');
  });
  it('shows only registration and a link to catalog administration for an authorized administrator', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Registro de donaciones');
    expect(fixture.nativeElement.querySelector('form #select-donantes')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="unitCode"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/admin/catalogos-donaciones"]')).not.toBeNull();
    fixture.componentInstance.catalogName = 'Donante';
    fixture.componentInstance.createCatalog();
    fixture.componentInstance.unitCode = 'KG'; fixture.componentInstance.unitName = 'Kilogramo';
    fixture.componentInstance.createUnit();
    http.expectNone(request => request.method === 'POST');
  });
  it('keeps catalog administration separate even when the user can also register donations', async () => {
    fixture.destroy();
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    fixture = TestBed.createComponent(DonationRegistrationPageComponent);
    fixture.autoDetectChanges();
    http.expectNone('/api/recursos');
    for (const kind of ['donantes', 'centros']) {
      http.expectOne(`/api/donaciones/catalogos/${kind}?texto=&page=0`)
        .flush({ content: [{ id: 1, nombre: 'Fixture', ubicacion: null }], page: 0, totalPages: 2 });
    }
    http.expectOne('/api/unidades-medida').flush([]);
    http.expectNone('/api/donaciones/catalogos/emergencias?texto=&page=0');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Catálogos de donaciones');
    expect(fixture.nativeElement.querySelector('select[name="donor"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="unitCode"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.panel ul li').length).toBe(1);
    expect([...fixture.nativeElement.querySelectorAll('[role="tab"]')].map((tab: any) => tab.textContent.trim()))
      .toEqual(['Donantes', 'Centros de acopio', 'Unidades de medida']);
    const component = fixture.componentInstance;
    component.donorId = 1; component.emergencyId = 1; component.lines = [{ recursoId: 3, cantidad: 1 }];
    component.register(); http.expectNone('/api/donaciones');
    component.load(); http.expectNone('/api/recursos');
    for (const kind of ['donantes', 'centros']) {
      http.expectOne(`/api/donaciones/catalogos/${kind}?texto=&page=0`).flush({ content: [], page: 0, totalPages: 0 });
    }
    http.expectOne('/api/unidades-medida').flush([]);
  });
  it('hides administrative actions and blocks catalog writes without the administrative permission', () => {
    TestBed.inject(BasicAuthService).markAuthenticated({ nombre: 'Org', apellido: 'Test', correo: 'org@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['DONACION_REGISTRAR'] });
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="unitCode"]')).toBeNull();
    fixture.componentInstance.catalogName = 'Donante'; fixture.componentInstance.createCatalog();
    fixture.componentInstance.unitCode = 'KG'; fixture.componentInstance.unitName = 'Kilogramo';
    fixture.componentInstance.createUnit();
    http.expectNone(request => request.method === 'POST');
  });
  it('preserves center creation for administrators without registration permission', async () => {
    TestBed.inject(BasicAuthService).markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['ACCESO_ADMINISTRAR'] });
    TestBed.inject(ActivatedRoute).snapshot.data['donationPage'] = 'catalogs';
    fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select[name="donor"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]')).not.toBeNull();
    // The sidebar and page must both respect the independent registration permission.
    expect(fixture.nativeElement.querySelector('a[href="/donaciones/registro"]')).toBeNull();
    const component = fixture.componentInstance;
    component.catalogKind = 'centros'; component.catalogName = 'Centro Norte';
    component.catalogLocation = 'Lima'; component.catalogDescription = 'Acopio';
    component.createCatalog();
    const request = http.expectOne('/api/admin/catalogos-donaciones/centros');
    expect(request.request.body).toEqual({ nombre: 'Centro Norte', ubicacion: 'Lima', descripcion: 'Acopio' });
    request.flush({ id: 9, nombre: 'Centro Norte', ubicacion: 'Lima' });
    await fixture.whenStable();
    component.selectTab('centros');
    fixture.changeDetectorRef.markForCheck();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Centro Norte');
  });
  it.each(['donantes', 'centros', 'emergencias'] as const)('filters and selects %s inside the donation form', async kind => {
    expect(fixture.nativeElement.querySelector('#catalog-search-title')).toBeNull();
    enter(`#filter-${kind}`, 'Nuevo');
    await new Promise(resolve => setTimeout(resolve, 350));
    const request = http.expectOne(`/api/donaciones/catalogos/${kind}?texto=Nuevo&page=0`);
    expect(fixture.nativeElement.querySelector('form .donation-resource-row select')).not.toBeNull();
    request.flush({ content: [{ id: 8, nombre: 'Nuevo registro', ubicacion: 'Lima' }], page: 0, totalPages: 1 });
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector(`#select-${kind}`);
    expect(select.options[1].textContent).toContain('Nuevo registro · ID 8 — Lima');
    select.selectedIndex = 1; select.dispatchEvent(new Event('change', { bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.selectedId(kind)).toBe(8);
  });
  it('debounces typing, cancels stale requests and prevents registering while searching', async () => {
    const component = fixture.componentInstance;
    component.donorId = 1; component.centerId = 1; component.lines = [{ recursoId: 3, cantidad: 1 }];
    vi.useFakeTimers();
    component.filterSelector('donantes', 'Viejo');
    vi.advanceTimersByTime(300);
    const stale = http.expectOne('/api/donaciones/catalogos/donantes?texto=Viejo&page=0');
    component.filterSelector('donantes', 'N');
    component.filterSelector('donantes', 'Nuevo');
    expect(stale.cancelled).toBe(true);
    component.register(); http.expectNone('/api/donaciones');
    http.expectNone('/api/donaciones/catalogos/donantes?texto=N&page=0');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/donaciones/catalogos/donantes?texto=Nuevo&page=0')
      .flush({ content: [{ id: 9, nombre: 'Nuevo' }], page: 0, totalPages: 1 });
    vi.useRealTimers();
    await fixture.whenStable();
    expect(component.catalogs.donantes.content.map(item => item.id)).toEqual([9]);
    expect(component.donorId).toBeNull();
    component.selectCatalog('donantes', 9); component.register();
    const post = http.expectOne('/api/donaciones');
    expect(post.request.body.centroAcopioId).toBe(1);
    expect(post.request.body.emergenciaId).toBeNull();
    post.flush({ id: 7, codigoTrazabilidad: 'RS-7' });
  });
  it('keeps a selected destination when more results are loaded and stops at the last page', async () => {
    const component = fixture.componentInstance;
    component.selectCatalog('centros', 1);
    component.search('centros', true);
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=1')
      .flush({ content: [{ id: 1, nombre: 'Fixture' }, { id: 2, nombre: 'Otro centro' }], page: 1, totalPages: 2 });
    await fixture.whenStable();
    expect(component.centerId).toBe(1);
    expect(component.catalogs.centros.content.map(item => item.id)).toEqual([1, 2]);
    component.search('centros', true); http.expectNone(request => request.url.includes('/catalogos/centros'));
  });
  it('shows selector empty and error states without destroying the form or its draft', async () => {
    const component = fixture.componentInstance;
    component.location = 'Ubicación pendiente'; component.lines.push({ recursoId: null, cantidad: null });
    component.search('donantes');
    http.expectOne('/api/donaciones/catalogos/donantes?texto=&page=0').flush({ content: [], page: 0, totalPages: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#state-donantes').textContent).toContain('No hay resultados');
    component.search('centros');
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=0').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#state-centros').textContent).toContain('no tiene permiso');
    expect(fixture.nativeElement.querySelector('input[name="location"]').value).toBe('Ubicación pendiente');
    expect(fixture.nativeElement.querySelectorAll('.donation-resource-row').length).toBe(2);
  });
  it('only exposes catalog and emergency management helpers to authorized administrators', async () => {
    TestBed.inject(BasicAuthService).markAuthenticated({ nombre: 'Org', apellido: 'Test', correo: 'org@test',
      rolCodigo: 'TEST', rolNombre: 'Test', permisos: ['DONACION_REGISTRAR'] });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-selector a')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/admin/catalogos-donaciones"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.session-button').length).toBe(2);
  });
  it('shows exactly the selected catalog and retains all three unsent form drafts', async () => {
    await openAdmin();
    enter('input[name="catalogName"]', 'Donante pendiente');
    await clickTab('centros');
    expect(fixture.nativeElement.querySelector('#catalog-list-title').textContent).toBe('Centros de acopio');
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Centro inicial');
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).not.toContain('Donante inicial');
    enter('input[name="catalogName"]', 'Centro pendiente'); enter('input[name="catalogLocation"]', 'Lima');
    enter('textarea[name="catalogDescription"]', 'Descripción pendiente');
    await clickTab('unidades');
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Kilogramo');
    enter('input[name="unitCode"]', 'LT'); enter('input[name="unitName"]', 'Litro'); enter('input[name="unitSymbol"]', 'l');
    await clickTab('donantes');
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]').value).toBe('Donante pendiente');
    await clickTab('centros');
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]').value).toBe('Centro pendiente');
    expect(fixture.nativeElement.querySelector('input[name="catalogLocation"]').value).toBe('Lima');
    expect(fixture.nativeElement.querySelector('textarea[name="catalogDescription"]').value).toBe('Descripción pendiente');
    await clickTab('unidades');
    expect(fixture.nativeElement.querySelector('input[name="unitCode"]').value).toBe('LT');
    expect(fixture.nativeElement.querySelector('input[name="unitName"]').value).toBe('Litro');
    expect(fixture.nativeElement.querySelector('input[name="unitSymbol"]').value).toBe('l');
    http.expectNone(request => request.url.includes('/catalogos/emergencias'));
  });
  it('supports keyboard tab navigation and preserves accessible focus', async () => {
    await openAdmin();
    const tab = fixture.nativeElement.querySelector('#tab-donantes');
    tab.focus(); tab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeTab).toBe('centros');
    expect(document.activeElement?.id).toBe('tab-centros');
    expect(fixture.nativeElement.querySelector('#tab-centros').getAttribute('aria-selected')).toBe('true');
    expect(fixture.nativeElement.querySelector('#catalog-panel').getAttribute('aria-labelledby')).toBe('tab-centros');
  });
  it('filters units locally by name, code and symbol and refreshes the list after creation', async () => {
    await openAdmin(); await clickTab('unidades');
    for (const text of ['Kilo', 'KG', 'kg']) {
      enter('input[name="unitSearch"]', text); await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Kilogramo');
    }
    enter('input[name="unitSearch"]', 'No existe'); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-empty').textContent).toContain('No hay unidades');
    enter('input[name="unitCode"]', 'LT'); enter('input[name="unitName"]', 'Litro');
    fixture.nativeElement.querySelector('#create-unit-title').parentElement.parentElement.querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const post = http.expectOne('/api/admin/catalogos-donaciones/unidades');
    expect(post.request.body).toEqual({ codigo: 'LT', nombre: 'Litro', simbolo: '' });
    post.flush({ id: 2, codigo: 'LT', nombre: 'Litro', simbolo: '' });
    http.expectOne('/api/unidades-medida').flush([{ id: 2, codigo: 'LT', nombre: 'Litro', simbolo: '' }]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Litro');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Unidad registrada');
  });
  it('preserves admin search, pagination, empty and error states in the active tab', async () => {
    await openAdmin();
    fixture.componentInstance.search('donantes', true);
    http.expectOne('/api/donaciones/catalogos/donantes?texto=&page=1')
      .flush({ content: [{ id: 3, nombre: 'Donante segunda página' }], page: 1, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.donation-results li').length).toBe(2);
    await clickTab('centros');
    fixture.componentInstance.search('centros');
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=0').flush({ content: [], page: 0, totalPages: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-empty').textContent).toContain('No hay resultados');
    fixture.componentInstance.search('centros');
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=0').flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('No se pudo completar');
  });
  it('keeps drafts after a rejected creation and makes a successful creation visible after a search error', async () => {
    await openAdmin(); await clickTab('centros');
    enter('input[name="catalogName"]', 'Centro pendiente'); enter('input[name="catalogLocation"]', 'Lima');
    fixture.componentInstance.createCatalog();
    http.expectOne('/api/admin/catalogos-donaciones/centros').flush({}, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]').value).toBe('Centro pendiente');
    expect(fixture.nativeElement.querySelector('input[name="catalogLocation"]').value).toBe('Lima');
    fixture.componentInstance.search('centros');
    http.expectOne('/api/donaciones/catalogos/centros?texto=&page=0').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.componentInstance.createCatalog();
    http.expectOne('/api/admin/catalogos-donaciones/centros').flush({ id: 3, nombre: 'Centro pendiente', ubicacion: 'Lima' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Centro pendiente');
    expect(fixture.nativeElement.querySelector('input[name="catalogName"]').value).toBe('');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Registro añadido');
  });
  it('shows unit refresh errors, allows retry and keeps other catalogs usable', async () => {
    await openAdmin(); await clickTab('unidades');
    fixture.componentInstance.reloadUnits();
    http.expectOne('/api/unidades-medida').flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('No se pudo completar');
    await clickTab('donantes');
    expect(fixture.nativeElement.querySelector('.donation-results').textContent).toContain('Donante inicial');
    await clickTab('unidades');
    fixture.componentInstance.reloadUnits();
    http.expectOne('/api/unidades-medida').flush([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.donation-empty').textContent).toContain('No hay unidades');
  });
});
