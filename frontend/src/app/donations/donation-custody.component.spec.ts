import { TestBed } from '@angular/core/testing';
import { describe, it, expect } from 'vitest';
import { DonationCustodyComponent } from './donation-custody.component';
import { PublicCustody } from './donation-custody.model';

describe('DonationCustodyComponent', () => {
  const collection = { tipo: 'ACOPIO', nombre: 'Acopio Piura', direccionInstitucional: 'Dirección institucional acopio' };
  const distribution = { tipo: 'DISTRIBUCION', nombre: 'Distribución Piura', direccionInstitucional: 'Dirección institucional distribución' };
  const resource: PublicCustody['recursos'][number] = {
    recurso: 'Arroz', unidad: 'KG', estadoLogistico: 'EN_TRANSITO', ultimaCustodiaConfirmada: collection,
    ultimaConfirmacion: '2026-11-12T10:00:00', ultimaActualizacion: '2026-11-12T11:00:00',
    posiciones: [{ estado: 'EN_TRANSITO', centro: distribution, zona: null, cantidad: 10 }],
    movimientos: [{ evento: 'SALIDA_TRASLADO', fechaHora: '2026-11-12T11:00:00', estado: 'EN_TRANSITO',
      origen: collection, destino: distribution, zona: null, cantidadEnviada: 10, cantidadRecibida: null }],
    aclaracion: 'Sin seguimiento GPS. La asignación no es entrega.',
  };
  async function render(data: PublicCustody) {
    await TestBed.configureTestingModule({ imports: [DonationCustodyComponent] }).compileComponents();
    const fixture = TestBed.createComponent(DonationCustodyComponent);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('donationState', 'REGISTRADA');
    fixture.detectChanges();
    return fixture;
  }
  it('shows transit with last confirmed custody and intended destination without claiming arrival', async () => {
    const fixture = await render({ presentacion: 'Dirección exacta protegida por privacidad', emergencia: 'Inundación', recursos: [resource] });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Ubicación y estado de tu donación');
    expect(text).toContain('Último punto de custodia confirmado: Acopio Piura');
    expect(text).toContain('Destino previsto: Distribución Piura');
    expect(text).toContain('Dirección institucional distribución');
    expect(text).not.toContain('Centro receptor:');
  });
  it('shows pending verification without treating a planned collection center as received', async () => {
    const fixture = await render({ presentacion: 'Dirección exacta protegida por privacidad', emergencia: null,
      recursos: [{ ...resource, estadoLogistico: 'PENDIENTE_DE_VERIFICACION', ultimaCustodiaConfirmada: null,
        ultimaConfirmacion: null, posiciones: [], movimientos: [] }] });
    expect(fixture.nativeElement.textContent).toContain('Pendiente de verificación física');
    expect(fixture.nativeElement.textContent).toContain('Último punto de custodia confirmado: No determinado');
    expect(fixture.nativeElement.querySelectorAll('.custody-timeline li').length).toBe(1);
  });
  it('keeps resource histories separate and labels reservations and actual delivery distinctly', async () => {
    const fixture = await render({ presentacion: 'Dirección protegida', emergencia: null, recursos: [resource, {
      ...resource, recurso: 'Agua', unidad: 'L', estadoLogistico: 'CUSTODIA_DISTRIBUIDA',
      posiciones: [{ estado: 'ENTREGADA', centro: null, zona: 'Zona norte', cantidad: 4 }],
      movimientos: [
        { ...resource.movimientos[0], evento: 'ASIGNACION_RECURSOS', estado: 'ASIGNADA', origen: distribution,
          destino: null, zona: 'Zona norte', cantidadEnviada: null, cantidadRecibida: 6 },
        { ...resource.movimientos[0], evento: 'ENTREGA_POBLACION', estado: 'ENTREGADA', origen: distribution,
          destino: null, zona: 'Zona norte', cantidadEnviada: null, cantidadRecibida: 4 },
      ],
    }] });
    const cards = fixture.nativeElement.querySelectorAll('.custody-resource');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).not.toContain('Entrega confirmada a la población');
    expect(cards[1].textContent).toContain('Asignada (reserva): 6 L');
    expect(cards[1].textContent).toContain('Entregada a la población: 4 L');
    expect(cards[1].textContent).toContain('Zona norte');
  });
  it('shows quantities and a reserved destination without claiming physical delivery', async () => {
    const fixture = await render({ presentacion: 'Dirección protegida', emergencia: null, recursos: [{ ...resource,
      estadoLogistico: 'ASIGNADA', ultimaCustodiaConfirmada: distribution,
      posiciones: [{ estado: 'EN_DISTRIBUCION', centro: distribution, zona: null, cantidad: 10 }],
      cantidades: { declarada: 10, recibidaConfirmada: 10, enTransito: 0, enDistribucion: 10,
        disponibleDistribucion: 4, asignada: 6, entregada: 0 },
      asignaciones: [{ zona: 'A.H. Tácala - Sector B', asignada: 6, entregada: 0 }], movimientos: [],
    }] });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Asignada, pendiente de entrega');
    expect(text).toContain('A.H. Tácala - Sector B');
    expect(text).toContain('Sin asignar en distribución');
    expect(text).toContain('Existe cantidad asignada pendiente de entrega física');
    expect(text).not.toContain('Entrega confirmada a la población');
    expect(fixture.nativeElement.querySelectorAll('.custody-quantities > div').length).toBe(7);
  });
  it('reports an incomplete response distinctly from a donation with no confirmed movements', async () => {
    await TestBed.configureTestingModule({ imports: [DonationCustodyComponent] }).compileComponents();
    const fixture = TestBed.createComponent(DonationCustodyComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La consulta no incluyó el detalle logístico');
    expect(fixture.nativeElement.textContent).not.toContain('Información de custodia no disponible');
  });
});
