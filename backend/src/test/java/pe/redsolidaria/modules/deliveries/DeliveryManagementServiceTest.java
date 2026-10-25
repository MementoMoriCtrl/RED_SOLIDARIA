package pe.redsolidaria.modules.deliveries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.deliveries.api.dto.DeliveryDtos.SaveDeliveryRequest;
import pe.redsolidaria.modules.deliveries.application.DeliveryManagementService;
import pe.redsolidaria.modules.deliveries.domain.*;
import pe.redsolidaria.modules.deliveries.persistence.*;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.emergencies.domain.*;
import pe.redsolidaria.modules.needs.domain.Need;
import pe.redsolidaria.modules.needs.persistence.NeedRepository;
import pe.redsolidaria.modules.resources.domain.Resource;
import tools.jackson.databind.json.JsonMapper;

class DeliveryManagementServiceTest {
    NeedRepository needs = mock(NeedRepository.class);
    DonationRepository donations = mock(DonationRepository.class);
    DonationLineRepository sources = mock(DonationLineRepository.class);
    DeliveryRepository deliveries = mock(DeliveryRepository.class);
    DeliveryLineRepository lines = mock(DeliveryLineRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditRecordRepository audit = mock(AuditRecordRepository.class);
    EntityManager em = mock(EntityManager.class);
    Donation donation = mock(Donation.class);
    DonationLine source = mock(DonationLine.class);
    Need need = mock(Need.class);
    Emergency emergency = mock(Emergency.class);
    AffectedZone zone = mock(AffectedZone.class);
    Resource resource = mock(Resource.class);
    DeliveryManagementService service = new DeliveryManagementService(needs, donations, sources, deliveries,
            lines, users, audit, em, JsonMapper.builder().build());
    static BigDecimal q(String value) { return new BigDecimal(value); }
    SaveDeliveryRequest request(String assigned, String delivered) { return new SaveDeliveryRequest(q(assigned), q(delivered)); }

    @BeforeEach void setup() {
        when(sources.findById(10L)).thenReturn(Optional.of(source));
        when(source.getDonacion()).thenReturn(donation);
        when(donation.getId()).thenReturn(2L);
        when(donations.findByIdForUpdate(2L)).thenReturn(Optional.of(donation));
        when(needs.findByIdForUpdate(1L)).thenReturn(Optional.of(need));
        when(source.getRecurso()).thenReturn(resource);
        when(need.getRecurso()).thenReturn(resource);
        when(resource.getId()).thenReturn(3L);
        when(resource.getEstado()).thenReturn("ACTIVO");
        when(donation.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(4L);
        when(need.getZona()).thenReturn(zone);
        when(zone.getEmergencia()).thenReturn(emergency);
        when(need.getCantidadRequerida()).thenReturn(q("5"));
        when(need.getEstado()).thenReturn("PENDIENTE");
        when(donation.getEstado()).thenReturn("REGISTRADA");
        when(source.getCantidadRecibidaAcopio()).thenReturn(q("10"));
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(mock(UserAccount.class)));
        when(lines.assignedToPair(1L, 10L)).thenReturn(q("0"));
        when(lines.deliveredToPair(1L, 10L)).thenReturn(q("0"));
        when(lines.assignedToDonation(10L)).thenReturn(q("0"));
        when(lines.assignedToNeed(1L)).thenReturn(q("0"));
        var delivery = mock(Delivery.class);
        when(delivery.getId()).thenReturn(20L);
        when(deliveries.saveAndFlush(any())).thenReturn(delivery);
    }

    @Test void repeatedTotalsDoNotWriteTwiceAndAuditContainsDeltas() {
        when(lines.assignedToPair(1L, 10L)).thenReturn(q("0"), q("4"));
        when(lines.deliveredToPair(1L, 10L)).thenReturn(q("0"), q("2"));
        assertFalse(service.save(1, 10, "admin@example.test", request("4", "2")).repetida());
        assertTrue(service.save(1, 10, "admin@example.test", request("4.000", "2.000")).repetida());
        verify(deliveries, times(1)).saveAndFlush(any());
        verify(lines, times(1)).saveAndFlush(any());
        var record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit, times(1)).saveAndFlush(record.capture());
        assertTrue(record.getValue().getDatosNuevos().contains("\"incrementoEntregado\":2"));
        var order = inOrder(donations, needs, em, deliveries, lines, audit);
        order.verify(donations).findByIdForUpdate(2L);
        order.verify(needs).findByIdForUpdate(1L);
        order.verify(em).refresh(source);
        order.verify(deliveries).saveAndFlush(any());
        order.verify(lines).saveAndFlush(any());
        order.verify(audit).saveAndFlush(any());
    }

    @Test void deliveringPreviouslyAssignedStockDoesNotReserveItAgain() {
        when(lines.assignedToPair(1L, 10L)).thenReturn(q("4"));
        when(lines.assignedToDonation(10L)).thenReturn(q("4"));
        when(lines.assignedToNeed(1L)).thenReturn(q("4"));
        service.save(1, 10, "admin@example.test", request("4", "3"));
        var detail = ArgumentCaptor.forClass(DeliveryLine.class);
        verify(lines).saveAndFlush(detail.capture());
        assertEquals(0, detail.getValue().getCantidadAsignada().signum());
        assertEquals(q("3"), detail.getValue().getCantidadEntregada());
    }

    @Test void rejectsAllocationAlreadyConsumedByOtherNeeds() {
        when(lines.assignedToDonation(10L)).thenReturn(q("9"));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.save(1, 10, "admin@example.test", request("2", "0"))).getStatusCode().value());
        verifyNoInteractions(deliveries, audit);
    }

    @Test void rejectsOverfillingNeedAcrossDifferentDonations() {
        when(lines.assignedToNeed(1L)).thenReturn(q("4"));
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("2", "0")));
        verifyNoInteractions(deliveries, audit);
    }

    @Test void neverTreatsAPledgeAsReceivedStock() {
        when(source.getCantidadRecibidaAcopio()).thenReturn(q("0"));
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("1", "0")));
        verifyNoInteractions(deliveries, audit);
    }

    @Test void rejectsDonationWithoutMatchingEmergency() {
        when(donation.getEmergencia()).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("1", "0")));
        verifyNoInteractions(deliveries, audit);
    }

    @Test void rejectsMismatchedResource() {
        var other = mock(Resource.class);
        when(other.getId()).thenReturn(99L);
        when(source.getRecurso()).thenReturn(other);
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("1", "0")));
        verifyNoInteractions(deliveries, audit);
    }

    @Test void rejectsReturnedDonationAndStaleDecreasingTotals() {
        when(donation.getEstado()).thenReturn("DEVUELTA");
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("1", "0")));
        when(donation.getEstado()).thenReturn("RECIBIDA");
        when(lines.assignedToPair(1L, 10L)).thenReturn(q("4"));
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("3", "0")));
        verifyNoInteractions(deliveries, audit);
    }

    @Test void rejectsInvalidQuantitiesBeforeAccessingPersistence() {
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("1", "2")));
        assertThrows(ResponseStatusException.class, () -> service.save(1, 10, "admin@example.test", request("0.0001", "0")));
        verifyNoInteractions(sources, deliveries, audit);
    }
}
