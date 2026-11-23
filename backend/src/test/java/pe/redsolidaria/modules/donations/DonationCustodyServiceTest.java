package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.redsolidaria.modules.donations.api.dto.CustodyDtos.*;
import pe.redsolidaria.modules.donations.application.DonationCustodyService;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.DonationCustodyRepository;
import pe.redsolidaria.modules.donations.persistence.DonationCustodyRepository.Row;
import pe.redsolidaria.modules.resources.domain.*;

class DonationCustodyServiceTest {
    DonationCustodyRepository repository=mock(DonationCustodyRepository.class);
    DonationCustodyService service=new DonationCustodyService(repository);
    Donation donation=mock(Donation.class);
    DonationLine line=mock(DonationLine.class);
    Place collection=new Place("ACOPIO","Acopio Piura","Dirección institucional acopio");
    Place distribution=new Place("DISTRIBUCION","Distribución Piura","Dirección institucional distribución");
    LocalDateTime start=LocalDateTime.of(2026,11,12,10,0);
    @BeforeEach void setup() {
        var resource=mock(pe.redsolidaria.modules.resources.domain.Resource.class); var unit=mock(MeasurementUnit.class);
        when(resource.getNombre()).thenReturn("Arroz"); when(resource.getUnidadMedida()).thenReturn(unit);
        when(unit.getCodigo()).thenReturn("KG"); when(line.getRecurso()).thenReturn(resource);
        when(line.getId()).thenReturn(11L); when(line.getCantidadRecibidaAcopio()).thenReturn(q("10"));
        when(line.getCantidadDonada()).thenReturn(q("10"));
        when(donation.getId()).thenReturn(1L); when(donation.getDetalles()).thenReturn(List.of(line));
        when(donation.getFechaRegistro()).thenReturn(start);
    }
    Row row(String type,int minute,String operation,BigDecimal sent,BigDecimal received,String zone) {
        Place origin=type.equals("SALIDA_TRASLADO") || type.equals("RECEPCION_DISTRIBUCION") ? collection : null;
        Place destination=type.equals("RECEPCION_ACOPIO") ? collection
            : type.equals("SALIDA_TRASLADO") || type.equals("RECEPCION_DISTRIBUCION") ? distribution : null;
        return new Row(11,new Event(type,start.plusMinutes(minute),type,origin,destination,zone,sent,received),
            operation,5L,"PERSONA_PRIVADA","NOTA_PRIVADA",!type.equals("RECEPCION_ACOPIO"));
    }
    pe.redsolidaria.modules.donations.api.dto.CustodyDtos.Resource read(Row... rows) {
        when(repository.events(1)).thenReturn(List.of(rows));
        return service.read(donation,List.of()).situacion().recursos().getFirst();
    }
    @Test void plannedCenterNeverBecomesConfirmedCustodyWithoutEvidence() {
        when(line.getCantidadRecibidaAcopio()).thenReturn(BigDecimal.ZERO);
        assertNull(read().ultimaCustodiaConfirmada());
        assertEquals("PENDIENTE_DE_VERIFICACION",read().estadoLogistico());
        assertTrue(read().posiciones().isEmpty());
    }
    @Test void positiveHistoricalQuantityDoesNotInventAReceiptDateOrCenter() {
        var result=read();
        assertNull(result.ultimaCustodiaConfirmada()); assertNull(result.ultimaConfirmacion());
        assertEquals("CUSTODIA_NO_DETERMINADA",result.posiciones().getFirst().estado());
    }
    @Test void transitKeepsLastConfirmedOriginAndDoesNotCreditDestination() {
        var result=read(row("SALIDA_TRASLADO",2,"t",q("10"),null,null),row("RECEPCION_ACOPIO",1,"r",null,q("10"),null));
        assertEquals(collection,result.ultimaCustodiaConfirmada());
        assertEquals("EN_TRANSITO",result.estadoLogistico());
        assertEquals(distribution,result.posiciones().getFirst().centro());
        assertEquals("RECEPCION_ACOPIO",result.movimientos().getFirst().evento());
        assertEquals(0,result.posiciones().stream().filter(p -> p.estado().equals("EN_DISTRIBUCION")).count());
    }
    @Test void receiptMovesCustodyAndDifferenceIsNotInventedAtEitherCenter() {
        var result=read(row("SALIDA_TRASLADO",2,"t",q("10"),null,null),row("RECEPCION_DISTRIBUCION",3,"t",q("10"),q("8"),null));
        assertEquals(distribution,result.ultimaCustodiaConfirmada());
        assertEquals(start.plusMinutes(3),result.ultimaConfirmacion());
        assertEquals(q("8"),result.posiciones().getFirst().cantidad()); assertEquals(1,result.posiciones().size());
    }
    @Test void assignmentDoesNotBecomeDeliveryAndOnlyActualDeliveryDebitsPhysicalCustody() {
        var departure=row("SALIDA_TRASLADO",2,"t",q("10"),null,null);
        var arrival=row("RECEPCION_DISTRIBUCION",3,"t",q("10"),q("10"),null);
        var assignment=row("ASIGNACION_RECURSOS",4,"a",null,q("6"),"Zona norte");
        var reserved=read(departure,arrival,assignment);
        assertEquals(q("10"),reserved.posiciones().getFirst().cantidad());
        assertFalse(reserved.posiciones().stream().anyMatch(p -> p.estado().equals("ENTREGADA")));
        assertEquals(distribution,reserved.movimientos().getLast().origen());
        assertEquals("ASIGNADA",reserved.estadoLogistico());
        assertEquals(q("4"),reserved.cantidades().disponibleDistribucion());
        assertEquals(q("6"),reserved.cantidades().asignada());
        assertEquals(BigDecimal.ZERO,reserved.cantidades().entregada());
        assertEquals("Zona norte",reserved.asignaciones().getFirst().zona());
        var delivered=read(departure,arrival,assignment,row("ENTREGA_POBLACION",5,"e",null,q("4"),"Zona norte"));
        assertEquals(q("6"),delivered.posiciones().getFirst().cantidad());
        assertEquals("Zona norte",delivered.posiciones().getLast().zona());
        assertEquals(q("4"),delivered.posiciones().getLast().cantidad());
        assertEquals("ENTREGA_PARCIAL",delivered.estadoLogistico());
        assertEquals(q("4"),delivered.cantidades().entregada());
        assertEquals(q("4"),delivered.cantidades().disponibleDistribucion());
        assertEquals(q("6"),delivered.cantidades().enDistribucion());
    }
    @Test void partialDispatchKeepsRemainingCollectionStockSeparateFromTransit() {
        var result=read(row("RECEPCION_ACOPIO",1,"r",null,q("10"),null),row("SALIDA_TRASLADO",2,"t",q("4"),null,null));
        assertEquals("CUSTODIA_DISTRIBUIDA",result.estadoLogistico());
        assertEquals(q("6"),result.posiciones().getFirst().cantidad());
        assertEquals(q("4"),result.posiciones().getLast().cantidad());
        assertEquals(q("4"),result.cantidades().enTransito());
        assertEquals(q("10"),result.cantidades().recibidaConfirmada());
    }
    @Test void independentResourcesDoNotInheritAnotherResourcesTransit() {
        var other=mock(DonationLine.class); when(other.getId()).thenReturn(12L);
        var sharedResource=line.getRecurso();
        when(other.getRecurso()).thenReturn(sharedResource); when(other.getCantidadRecibidaAcopio()).thenReturn(BigDecimal.ZERO);
        when(donation.getDetalles()).thenReturn(List.of(line,other));
        when(repository.events(1)).thenReturn(List.of(row("SALIDA_TRASLADO",2,"t",q("10"),null,null)));
        var resources=service.read(donation,List.of()).situacion().recursos();
        assertEquals("EN_TRANSITO",resources.getFirst().estadoLogistico());
        assertEquals("PENDIENTE_DE_VERIFICACION",resources.getLast().estadoLogistico());
        assertTrue(resources.getLast().movimientos().isEmpty());
    }
    static BigDecimal q(String value) { return new BigDecimal(value); }

    @Test void administrativeRawBalancesExposePositiveZeroAndNegativeWithoutChangingOperationalValues() {
        for (var entry : List.of(new String[]{"4", "6", "6"}, new String[]{"10", "0", "0"}, new String[]{"12", "-2", "0"})) {
            when(repository.events(1)).thenReturn(List.of(row("RECEPCION_ACOPIO",1,"r",null,q("10"),null),
                    row("SALIDA_TRASLADO",2,"t",q(entry[0]),null,null)));
            var result=service.read(donation,List.of());
            var balance=result.saldosBrutos().stream().filter(b -> b.tipo().equals("DISPONIBLE_ACOPIO")).findFirst().orElseThrow();
            assertEquals(0,balance.saldoBruto().compareTo(q(entry[1])));
            assertEquals("Arroz",balance.recurso()); assertEquals("Acopio Piura",balance.centro());
            var acopio=result.situacion().recursos().getFirst().posiciones().stream()
                    .filter(p -> p.estado().equals("EN_ACOPIO")).map(Position::cantidad).findFirst().orElse(BigDecimal.ZERO);
            assertEquals(0,acopio.compareTo(q(entry[2])));
        }
    }
    @Test void negativeDistributionRawBalancesRemainAdministrative() {
        when(repository.events(1)).thenReturn(List.of(row("SALIDA_TRASLADO",2,"t",q("4"),null,null),
            row("RECEPCION_DISTRIBUCION",3,"t",q("4"),q("4"),null),
            row("ASIGNACION_RECURSOS",4,"a",null,q("6"),"Zona"),
            row("ENTREGA_POBLACION",5,"e",null,q("7"),"Zona")));
        var result=service.read(donation,List.of());
        var balances=result.saldosBrutos();
        var availability=balances.stream().filter(b -> b.tipo().equals("DISPONIBLE_DISTRIBUCION")).findFirst().orElseThrow();
        var physical=balances.stream().filter(b -> b.tipo().equals("CUSTODIA_DISTRIBUCION")).findFirst().orElseThrow();
        assertEquals(0,availability.saldoBruto().compareTo(q("-2"))); assertEquals("Distribución Piura",availability.centro());
        assertEquals(0,physical.saldoBruto().compareTo(q("-3")));
        assertEquals(0,result.situacion().recursos().getFirst().cantidades().disponibleDistribucion().signum());
        assertFalse(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result.situacion()).contains("saldosBrutos"));
    }

    @Test void confirmedReceiptIdentifiesCollectionCenterWithoutUsingGlobalDonationState() {
        when(donation.getEstado()).thenReturn("REGISTRADA");
        var result=read(row("RECEPCION_ACOPIO",1,"r",null,q("10"),null));
        assertEquals("EN_ACOPIO",result.estadoLogistico());
        assertEquals(collection,result.ultimaCustodiaConfirmada());
        assertEquals(q("10"),result.cantidades().declarada());
        assertTrue(service.read(donation,List.of()).situacion().recepcionAcopioConfirmada());
    }
    @Test void repeatedProjectionRowsDoNotMultiplyTheSameOperation() {
        var departure=row("SALIDA_TRASLADO",2,"t",q("4"),null,null);
        var result=read(departure,departure);
        assertEquals(q("4"),result.cantidades().enTransito());
        assertEquals(q("6"),result.posiciones().getFirst().cantidad());
        assertEquals(1,result.movimientos().size());
    }
    @Test void fullDeliveryInSeveralRecordsToOneZoneIsOneDeliveredLocation() {
        var result=read(row("SALIDA_TRASLADO",2,"t",q("10"),null,null),
            row("RECEPCION_DISTRIBUCION",3,"t",q("10"),q("10"),null),
            row("ASIGNACION_RECURSOS",4,"a",null,q("10"),"Tácala - Sector B"),
            row("ENTREGA_POBLACION",5,"e1",null,q("4"),"Tácala - Sector B"),
            row("ENTREGA_POBLACION",6,"e2",null,q("6"),"Tácala - Sector B"));
        assertEquals("ENTREGADA",result.estadoLogistico());
        assertEquals(1,result.posiciones().size());
        assertEquals(q("10"),result.cantidades().entregada());
        assertEquals(BigDecimal.ZERO,result.cantidades().enDistribucion());
        assertEquals("Tácala - Sector B",result.ultimaCustodiaConfirmada().nombre());
    }
    @Test void distinctDeliveryDetailsWithinOneOperationAreNotDiscardedAsDuplicates() {
        var first=row("ENTREGA_POBLACION",5,"delivery",null,q("4"),"Zona A");
        var second=row("ENTREGA_POBLACION",5,"delivery",null,q("6"),"Zona B");
        var result=read(row("SALIDA_TRASLADO",2,"t",q("10"),null,null),
            row("RECEPCION_DISTRIBUCION",3,"t",q("10"),q("10"),null),
            new Row(11,first.event(),"delivery",5L,"Actor","Notes",true,"detail-1"),
            new Row(11,second.event(),"delivery",5L,"Actor","Notes",true,"detail-2"));
        assertEquals(q("10"),result.cantidades().entregada());
        assertEquals(4,result.movimientos().size());
        assertEquals(2,result.asignaciones().size());
        assertEquals("ENTREGADA",result.estadoLogistico());
    }
}
