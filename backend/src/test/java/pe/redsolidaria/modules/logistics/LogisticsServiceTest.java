package pe.redsolidaria.modules.logistics;

import static pe.redsolidaria.modules.logistics.LogisticsDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.resources.domain.*;
import tools.jackson.databind.json.JsonMapper;

class LogisticsServiceTest {
    LogisticsRepository repository=mock(LogisticsRepository.class);
    DonationRepository donations=mock(DonationRepository.class);
    DonationLineRepository lines=mock(DonationLineRepository.class);
    DeliveryLineRepository deliveries=mock(DeliveryLineRepository.class);
    UserRepository users=mock(UserRepository.class);
    AuditRecordRepository audit=mock(AuditRecordRepository.class);
    DonationMovementRepository movements=mock(DonationMovementRepository.class);
    EntityManager em=mock(EntityManager.class);
    DonationLine line=mock(DonationLine.class);
    Donation donation=mock(Donation.class);
    UserAccount actor=mock(UserAccount.class);
    LogisticsService service=new LogisticsService(repository,donations,lines,deliveries,users,audit,movements,em,JsonMapper.builder().build());
    Map<String,Transfer> persisted=new HashMap<>();
    static BigDecimal q(String value) { return new BigDecimal(value); }
    @BeforeEach void setup() {
        when(lines.findById(10L)).thenReturn(Optional.of(line)); when(line.getId()).thenReturn(10L);
        when(line.getDonacion()).thenReturn(donation); when(donation.getId()).thenReturn(20L);
        when(donations.findByIdForUpdate(20L)).thenReturn(Optional.of(donation));
        when(donation.getEstado()).thenReturn("RECIBIDA"); when(line.getCantidadRecibidaAcopio()).thenReturn(q("100"));
        var center=mock(CollectionCenter.class); when(center.getId()).thenReturn(1L); when(donation.getCentroAcopio()).thenReturn(center);
        var resource=mock(Resource.class); var unit=mock(MeasurementUnit.class);
        when(line.getRecurso()).thenReturn(resource); when(resource.getUnidadMedida()).thenReturn(unit); when(unit.getId()).thenReturn(3L);
        when(actor.getId()).thenReturn(9L); when(users.findByCorreo("admin@test")).thenReturn(Optional.of(actor));
        when(repository.centers()).thenReturn(List.of(new Center(2L,"DistribuciÃ³n",null,null)));
        when(repository.sent(10L)).thenReturn(BigDecimal.ZERO); when(deliveries.historicalAssigned(10L)).thenReturn(BigDecimal.ZERO);
        when(repository.transfer(anyString())).thenAnswer(call -> Optional.ofNullable(persisted.get(call.getArgument(0))));
        doAnswer(call -> {
            Dispatch request=call.getArgument(0); String fingerprint=call.getArgument(4);
            persisted.put(request.operacionId().toString(), transfer(request.operacionId().toString(),"EN_TRANSITO",null,null,fingerprint,null)); return null;
        }).when(repository).dispatch(any(),anyLong(),anyLong(),anyLong(),anyString());
        doAnswer(call -> {
            String id=call.getArgument(0); Receive request=call.getArgument(1); String fingerprint=call.getArgument(3); String state=call.getArgument(4);
            persisted.put(id, transfer(id,state,request.cantidad(),request.operacionId().toString(),persisted.get(id).huellaSalida(),fingerprint)); return null;
        }).when(repository).receive(anyString(),any(),anyLong(),anyString(),anyString());
    }
    Transfer transfer(String id,String state,BigDecimal received,String operation,String sentHash,String receivedHash) {
        return new Transfer(id,10L,1L,2L,"Acopio","DistribuciÃ³n","Arroz","KG",q("80"),received,state,null,null,9L,
            received==null ? null : 9L,null,null,operation,sentHash,receivedHash);
    }
    Dispatch request() { return new Dispatch(UUID.randomUUID(),10L,2L,q("80"),null); }
    @Test void locksSourceAndDispatchesWithoutReceivingStockAtDestination() {
        var result=service.dispatch(request(),"ADMIN@test"); assertEquals("EN_TRANSITO",result.estado()); assertNull(result.cantidadRecibida());
        var order=inOrder(donations,em,repository,audit);
        order.verify(donations).findByIdForUpdate(20L); order.verify(em).refresh(line);
        order.verify(repository).dispatch(any(),eq(1L),eq(3L),eq(9L),anyString()); order.verify(audit).saveAndFlush(any());
        verify(repository,never()).receive(anyString(),any(),anyLong(),anyString(),anyString());
    }
    @Test void durableReplayAfterRecreatingServiceDoesNotDispatchTwice() {
        var request=request(); service.dispatch(request,"admin@test");
        var restarted=new LogisticsService(repository,donations,lines,deliveries,users,audit,movements,em,JsonMapper.builder().build());
        restarted.dispatch(new Dispatch(request.operacionId(),10L,2L,q("80.000"),""),"admin@test");
        verify(repository,times(1)).dispatch(any(),anyLong(),anyLong(),anyLong(),anyString()); verify(audit,times(1)).saveAndFlush(any());
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> restarted.dispatch(new Dispatch(request.operacionId(),10L,2L,q("70"),null),"admin@test")).getStatusCode().value());
    }
    @Test void protectsStockInTransitAndHistoricalReservations() {
        when(repository.sent(10L)).thenReturn(q("30"));
        assertThrows(ResponseStatusException.class,() -> service.dispatch(request(),"admin@test"));
        when(repository.sent(10L)).thenReturn(q("0")); when(deliveries.historicalAssigned(10L)).thenReturn(q("21"));
        assertThrows(ResponseStatusException.class,() -> service.dispatch(request(),"admin@test"));
        verify(repository,never()).dispatch(any(),anyLong(),anyLong(),anyLong(),anyString()); verifyNoInteractions(audit,movements);
    }
    @Test void confirmsDifferenceOnceAndRejectsChangedReplayOrExcess() {
        var sent=service.dispatch(request(),"admin@test");
        var excess=new Receive(UUID.randomUUID(),q("81"),null);
        assertThrows(ResponseStatusException.class,() -> service.receive(sent.operacionId(),excess,"admin@test"));
        assertThrows(ResponseStatusException.class,() -> service.receive(sent.operacionId(),new Receive(UUID.randomUUID(),q("75"),null),"admin@test"));
        var receive=new Receive(UUID.randomUUID(),q("75"),"Faltan 5 kg");
        assertEquals("RECIBIDO_CON_DIFERENCIA",service.receive(sent.operacionId(),receive,"admin@test").estado());
        service.receive(sent.operacionId(),receive,"admin@test");
        verify(repository,times(1)).receive(anyString(),any(),anyLong(),anyString(),anyString());
        assertThrows(ResponseStatusException.class,() -> service.receive(sent.operacionId(),new Receive(UUID.randomUUID(),q("75"),"Faltan 5 kg"),"admin@test"));
    }
    @Test void keepsALotInOneDistributionCenterToPreserveReservationLocation() {
        when(repository.hasOtherDestination(10L,2L)).thenReturn(true);
        assertThrows(ResponseStatusException.class,() -> service.dispatch(request(),"admin@test"));
        verifyNoInteractions(audit,movements);
    }
    @Test void refusesToInventACollectionCenterForHistoricalDonations() {
        when(donation.getCentroAcopio()).thenReturn(null);
        assertThrows(ResponseStatusException.class,() -> service.dispatch(request(),"admin@test")); verifyNoInteractions(audit,movements);
    }
}
