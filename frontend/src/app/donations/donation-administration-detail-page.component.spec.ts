import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { basicAuthInterceptor } from '../auth/basic-auth.interceptor';
import { BasicAuthService } from '../auth/basic-auth.service';
import { DonationAdministrationDetailPageComponent } from './donation-administration-detail-page.component';

describe('DonationAdministrationDetailPageComponent', () => {
  let fixture: ComponentFixture<DonationAdministrationDetailPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [DonationAdministrationDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '42' } } } },
        provideHttpClient(withInterceptors([basicAuthInterceptor])), provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DonationAdministrationDetailPageComponent);
    http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(BasicAuthService);
    auth.provideCredentials('admin', 'memory-only');
    auth.markAuthenticated({ nombre: 'Admin', apellido: 'Test', correo: 'admin@example.test', rolCodigo: 'ADMIN', rolNombre: 'Admin', permisos: ['ACCESO_ADMINISTRAR'] });
    fixture.autoDetectChanges();
  });

  afterEach(() => http.verify());

  it('loads the private donation detail and movement history', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush({
      id: 42, codigoTrazabilidad: 'RS-42', fechaRegistro: '2026-04-02T10:00:00', fechaIngreso: null,
      estado: 'REGISTRADA', observaciones: 'Ingreso inicial', donante: { id: 4, nombre: 'Donante reservado' },
      emergenciaId: 5, emergenciaNombre: 'Emergencia Norte', centroAcopio: null,
      detalles: [{ recursoId: 9, recursoNombre: 'Arroz Blanco', cantidadDonada: 10, cantidadRecibidaAcopio: 0,
        unidadCodigo: 'KG', unidadNombre: 'Kilogramo', unidadSimbolo: 'kg' }],
      movimientos: [{ estadoAnterior: null, estadoNuevo: 'REGISTRADA', fechaHora: '2026-04-02T10:00:00',
        ubicacion: 'Zona Norte', observaciones: null, responsableNombre: 'Ana Admin' }],
    });
    await fixture.whenStable();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Donante reservado');
    expect(fixture.nativeElement.textContent).toContain('Arroz Blanco');
    expect(fixture.nativeElement.textContent).toContain('Ana Admin');
    expect(fixture.nativeElement.textContent).toContain('Ingreso inicial');
  });

  it('ends loading and displays not found errors', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush({}, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando detalle');
    expect(fixture.nativeElement.textContent).toContain('No encontramos la donación solicitada');
  });

  it('submits a partial receipt, stops saving, and reloads the donation after success', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    await fixture.whenStable();
    fixture.componentInstance.setReceiptAmount(9, '2.5');
    fixture.componentInstance.receiptLocation = 'Centro Norte';
    fixture.componentInstance.submitReceipt();
    expect(fixture.componentInstance.savingReceipt).toBe(true);
    const post = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.detalles).toEqual([{ recursoId: 9, cantidad: 2.5 }]);
    post.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    await fixture.whenStable();
    expect(fixture.componentInstance.savingReceipt).toBe(false);
    expect(fixture.componentInstance.receiptSuccess).toContain('correctamente');
    expect(fixture.componentInstance.donation?.detalles[0].cantidadRecibidaAcopio).toBe(2.5);
  });

  it('stops saving and displays a validation error when a receipt fails', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    await fixture.whenStable();
    fixture.componentInstance.setReceiptAmount(9, '12');
    fixture.componentInstance.submitReceipt();
    http.expectNone('/api/donaciones/administracion/42/recepciones');
    await fixture.whenStable();
    expect(fixture.componentInstance.savingReceipt).toBe(false);
    expect(fixture.componentInstance.receiptError).toContain('cantidad supera');
  });

  it('retries an unconfirmed receipt with the same durable identity after recreating the page', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    fixture.componentInstance.setReceiptAmount(9, '2.5');
    fixture.componentInstance.submitReceipt();
    const first = http.expectOne('/api/donaciones/administracion/42/recepciones');
    const payload = first.request.body;
    expect(payload.operacionId).toMatch(/^[0-9a-f-]{36}$/);
    first.flush({}, { status: 500, statusText: 'Server Error' });
    fixture.destroy();
    fixture = TestBed.createComponent(DonationAdministrationDetailPageComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    fixture.componentInstance.submitReceipt();
    const retry = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(retry.request.body).toEqual(payload);
    retry.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(2.5));
    expect(fixture.componentInstance.pendingReceipt).toBeNull();
    fixture.componentInstance.setReceiptAmount(9, '1');
    fixture.componentInstance.submitReceipt();
    const later = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(later.request.body.operacionId).not.toBe(payload.operacionId);
    later.flush({ donacionId: 42, estado: 'REGISTRADA' });
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(3.5));
    await fixture.whenStable();
  });

  it('confirms a justified zero accepted closure through the existing receipt endpoint', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0)); await fixture.whenStable();
    const page=fixture.componentInstance; page.loadVerification();
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([progress(true)]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    page.examinedAmounts[9]='10'; page.receiptAmounts[9]='0'; page.rejectedAmounts[9]='10'; page.closing[9]=true;
    page.receiptObservations='Material no apto'; page.submitReceipt();
    const request=http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(request.request.body.detalles).toEqual([{recursoId:9,cantidad:0,examinada:10,noAceptada:10,excedente:0,cerrar:true}]);
    request.flush({}); http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([{...progress(true),cerrada:true,avance:'CERRADA'}]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    await fixture.whenStable(); expect(page.canReceive).toBe(false);
  });
  it('blocks self verification using the server result', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0)); await fixture.whenStable();
    const page=fixture.componentInstance; page.loadVerification();
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([progress(false)]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    page.examinedAmounts[9]='1'; page.receiptAmounts[9]='1'; page.submitReceipt();
    http.expectNone('/api/donaciones/administracion/42/recepciones'); expect(page.receiptError).toContain('verificador');
    await fixture.whenStable(); expect(fixture.nativeElement.textContent).toContain('No puedes verificar tu propia');
  });
  it('retries surplus authorization with the same UUID after network failure', async () => {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(10)); await fixture.whenStable();
    const page=fixture.componentInstance;
    const entry={operacionId:'original',decision:null,decisorId:null,fechaDecision:null,observacionesDecision:null,
      recepcion:{fechaHora:'',ubicacion:'',observaciones:'',responsableId:2,centroAcopioId:1,detalles:[{recursoNombre:'Arroz',cantidadRecibidaOperacion:10,excedente:2}]}};
    page.decisionNotes['original']='Apto y autorizado'; page.decide(entry,true);
    const first=http.expectOne('/api/donaciones/administracion/42/recepciones/original/excedente'); const payload=first.request.body;
    first.error(new ProgressEvent('network')); await fixture.whenStable(); expect(page.pendingDecision).not.toBeNull();
    fixture.destroy(); fixture=TestBed.createComponent(DonationAdministrationDetailPageComponent); fixture.autoDetectChanges();
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(10));
    fixture.componentInstance.retryDecision();
    const retry=http.expectOne('/api/donaciones/administracion/42/recepciones/original/excedente'); expect(retry.request.body).toEqual(payload);
    retry.flush({}); http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(12));
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([{...progress(true),aceptada:12,cerrada:true}]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    await fixture.whenStable(); expect(fixture.componentInstance.pendingDecision).toBeNull();
  });
  async function prepareVerification() {
    http.expectOne('/api/donaciones/administracion/42').flush(donationResponse(0));
    await fixture.whenStable();
    fixture.componentInstance.loadVerification();
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([progress(true)]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    return fixture.componentInstance;
  }

  it('shows closed shortages and approved surplus without a negative pending balance', async () => {
    const page = await prepareVerification();
    page.verification = [
      {...progress(true), aceptada: 12, examinada: 12, excedenteAprobado: 2, cerrada: true, diferencia: 2, avance: 'CERRADA'},
      {...progress(true), recursoId: 10, aceptada: 3, examinada: 5, noAceptada: 2, cerrada: true, diferencia: -7, avance: 'CERRADA'},
      {...progress(true), recursoId: 11, aceptada: 2.125, diferencia: -7.875},
      {...progress(true), recursoId: 12, aceptada: 10, cerrada: true, excedentePendiente: 2, diferencia: 0},
    ];
    fixture.changeDetectorRef.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('.verification-table-wrap tbody tr');
    const cells = (i: number) => Array.from(rows[i].querySelectorAll('td')).map(cell => (cell as HTMLElement).textContent?.trim());
    expect(cells(0)[6]).toBe('2');
    expect(cells(0)[7]).toBe('0');
    expect(cells(0)[9]).toBe('2 (final)');
    expect(cells(1)[7]).toBe('0');
    expect(cells(1)[9]).toBe('-7 (final)');
    expect(cells(2)[7]).toBe('7.875');
    expect(cells(2)[9]).toBe('-7.875 (provisional)');
    expect(cells(3)[9]).toBe('0 (provisional)');
    expect(fixture.nativeElement.textContent).toContain('Examinada desde V4');
  });

  it.each(['receiptAmounts', 'rejectedAmounts', 'surplusAmounts'] as const)(
    'does not silently omit a line with only %s populated', async field => {
      const page = await prepareVerification();
      page[field][9] = '1';
      page.submitReceipt();
      expect(page.receiptError).toContain('examinada = aceptada');
      http.expectNone('/api/donaciones/administracion/42/recepciones');
      expect(page.pendingReceipt).toBeNull();
    });

  it('blocks an incomplete second line instead of submitting only the first', async () => {
    const page = await prepareVerification();
    page.verification.push({...progress(true), recursoId: 10});
    page.examinedAmounts[9] = '1'; page.receiptAmounts[9] = '1';
    page.rejectedAmounts[10] = '2'; page.receiptObservations = 'No apto';
    page.submitReceipt();
    expect(page.receiptError).toContain('examinada = aceptada');
    http.expectNone('/api/donaciones/administracion/42/recepciones');
  });

  it.each(['rejection', 'surplus', 'zero closure'])(
    'requires justification before sending %s', async scenario => {
      const page = await prepareVerification();
      if (scenario === 'rejection') { page.examinedAmounts[9] = '1'; page.rejectedAmounts[9] = '1'; }
      if (scenario === 'surplus') { page.examinedAmounts[9] = '12'; page.receiptAmounts[9] = '10'; page.surplusAmounts[9] = '2'; }
      if (scenario === 'zero closure') page.closing[9] = true;
      page.receiptObservations = '  '; page.submitReceipt();
      expect(page.receiptError).toContain('Justifica');
      http.expectNone('/api/donaciones/administracion/42/recepciones');
    });

  it.each(['1.0001', '1.0000', '-1', 'NaN', '100000000000'])(
    'rejects invalid decimal %s', async value => {
      const page = await prepareVerification();
      page.examinedAmounts[9] = value; page.receiptAmounts[9] = value;
      page.submitReceipt();
      expect(page.receiptError).toContain('Revisa la verificación');
      http.expectNone('/api/donaciones/administracion/42/recepciones');
    });

  it.each([[11, 0], [9, 3]])('rejects invalid accepted/surplus split %s/%s', async (accepted, surplus) => {
    const page = await prepareVerification();
    page.receiptAmounts[9] = String(accepted); page.surplusAmounts[9] = String(surplus);
    page.examinedAmounts[9] = String(accepted + surplus); page.receiptObservations = 'Apto';
    page.submitReceipt();
    expect(page.receiptError).toContain('excedente');
    http.expectNone('/api/donaciones/administracion/42/recepciones');
  });

  it.each([400, 403, 409, 500, 0])('distinguishes receipt error %s and preserves uncertain content', async status => {
    const page = await prepareVerification();
    page.examinedAmounts[9] = '1.125'; page.receiptAmounts[9] = '1.125'; page.submitReceipt();
    const request = http.expectOne('/api/donaciones/administracion/42/recepciones');
    const payload = request.request.body;
    if (status === 0) request.error(new ProgressEvent('network'));
    else request.flush({}, {status, statusText: 'Failure'});
    expect(page.receiptError).toContain(status === 0 || status === 500 ? 'incierta' : String(status));
    const key = 'red-solidaria.receipt.admin@example.test.42';
    if ([400, 409].includes(status)) {
      expect(page.pendingReceipt).toBeNull(); expect(sessionStorage.getItem(key)).toBeNull();
    } else {
      expect(page.pendingReceipt).toEqual(payload);
      expect(JSON.parse(sessionStorage.getItem(key)!)).toEqual(payload);
    }
  });

  it('sends a justified zero examination closure and ignores untouched lines', async () => {
    const page = await prepareVerification();
    page.verification.push({...progress(true), recursoId: 10});
    page.examinedAmounts[10] = ''; page.closing[9] = true; page.receiptObservations = 'Cierre con faltante';
    page.submitReceipt();
    const request = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(request.request.body.detalles).toEqual([{recursoId: 9, cantidad: 0, examinada: 0, noAceptada: 0, excedente: 0, cerrar: true}]);
    request.error(new ProgressEvent('network'));
  });

  it.each([400, 403, 409, 500])('distinguishes surplus decision error %s', async status => {
    const page = await prepareVerification();
    page.pendingDecision = {uuid: 'original', request: {operacionId: crypto.randomUUID(), aprobar: false, observaciones: 'No apto'}};
    const pending = page.pendingDecision;
    page.retryDecision();
    http.expectOne('/api/donaciones/administracion/42/recepciones/original/excedente').flush({}, {status, statusText: 'Failure'});
    expect(page.receiptError).toContain(status === 500 ? 'incierta' : String(status));
    expect(page.pendingDecision).toEqual([400, 409].includes(status) ? null : pending);
  });

  it('submits all valid operation lines with three decimals and surplus awaiting authorization', async () => {
    const page = await prepareVerification();
    page.verification.push({...progress(true), recursoId: 10});
    page.donation!.detalles.push({...page.donation!.detalles[0], recursoId: 10});
    page.examinedAmounts[9] = '12.125'; page.receiptAmounts[9] = '10'; page.surplusAmounts[9] = '2.125';
    page.examinedAmounts[10] = '0.125'; page.rejectedAmounts[10] = '0.125';
    page.receiptObservations = 'Excedente apto y material no aceptado';
    page.submitReceipt();
    const request = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(request.request.body.detalles).toEqual([
      {recursoId: 9, cantidad: 10, examinada: 12.125, noAceptada: 0, excedente: 2.125, cerrar: false},
      {recursoId: 10, cantidad: 0, examinada: 0.125, noAceptada: 0.125, excedente: 0, cerrar: false},
    ]);
    const payload = request.request.body;
    request.error(new ProgressEvent('network'));
    page.examinedAmounts[9] = '99'; page.receiptObservations = 'Edición posterior';
    page.submitReceipt();
    const retry = http.expectOne('/api/donaciones/administracion/42/recepciones');
    expect(retry.request.body).toEqual(payload);
    retry.error(new ProgressEvent('network'));
  });

  it('opens the master self-verification form only when the server permits it and shows real identities', async () => {
    const page = await prepareVerification();
    const auth = TestBed.inject(BasicAuthService);
    auth.markAuthenticated({...auth.profile()!, permisos: ['ACCESO_ADMINISTRAR', 'ADMIN_MAESTRO']});
    page.verification = [progress(true)];
    page.receipts = [{operacionId: 'master-receipt', decision: null, decisorId: null, fechaDecision: null, observacionesDecision: null,
      recepcion: {fechaHora: '2026-11-10T10:00:00', ubicacion: 'Acopio', observaciones: 'Verificado', responsableId: 3,
        registradorId: 3, autoverificacionMaestro: true, centroAcopioId: 1, detalles: []}}];
    fixture.changeDetectorRef.detectChanges();
    expect(page.isMasterAdministrator).toBe(true);
    expect(fixture.nativeElement.querySelector('.receipt-form')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Verificador ID 3');
    expect(fixture.nativeElement.textContent).toContain('Registrador ID 3');
    page.loadVerification();
    http.expectOne('/api/donaciones/administracion/42/verificacion').flush([progress(false)]);
    http.expectOne('/api/donaciones/administracion/42/recepciones?page=0').flush([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.receipt-form')).toBeNull();
    page.examinedAmounts[9] = '1'; page.receiptAmounts[9] = '1'; page.submitReceipt();
    http.expectNone('/api/donaciones/administracion/42/recepciones');
  });

  function progress(canVerify: boolean) { return {recursoId:9,recursoNombre:'Arroz',unidad:'KG',declarada:10,aceptada:0,examinada:0,
    noAceptada:0,excedentePendiente:0,excedenteAprobado:0,cerrada:false,diferencia:-10,avance:'PENDIENTE',puedeVerificar:canVerify}; }

  function donationResponse(received: number) {
    return {
      id: 42, codigoTrazabilidad: 'RS-42', fechaRegistro: '2026-04-02T10:00:00', fechaIngreso: null,
      estado: 'REGISTRADA', observaciones: null, donante: { id: 4, nombre: 'Donante' },
      emergenciaId: null, emergenciaNombre: null, centroAcopio: { id: 1, nombre: 'Acopio Norte', ubicacion: 'Piura' },
      detalles: [{ recursoId: 9, recursoNombre: 'Arroz Blanco', cantidadDonada: 10, cantidadRecibidaAcopio: received,
        unidadCodigo: 'KG', unidadNombre: 'Kilogramo', unidadSimbolo: 'kg' }], movimientos: [],
    };
  }
});
