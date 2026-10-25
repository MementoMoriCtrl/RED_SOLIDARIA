package pe.redsolidaria.modules.needs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.emergencies.domain.*;
import pe.redsolidaria.modules.emergencies.persistence.AffectedZoneRepository;
import pe.redsolidaria.modules.needs.api.dto.NeedDtos.CreateNeedRequest;
import pe.redsolidaria.modules.needs.application.NeedManagementService;
import pe.redsolidaria.modules.needs.domain.Need;
import pe.redsolidaria.modules.needs.persistence.NeedRepository;
import pe.redsolidaria.modules.resources.domain.*;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;
import tools.jackson.databind.json.JsonMapper;

class NeedManagementServiceTest {
    NeedRepository needs = mock(NeedRepository.class);
    AffectedZoneRepository zones = mock(AffectedZoneRepository.class);
    ResourceRepository resources = mock(ResourceRepository.class);
    DonationLineRepository donations = mock(DonationLineRepository.class);
    DeliveryLineRepository lines = mock(DeliveryLineRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditRecordRepository audit = mock(AuditRecordRepository.class);
    NeedManagementService service = new NeedManagementService(needs, zones, resources, donations, lines, users, audit, JsonMapper.builder().build());

    @Test void registersNeedWithAuditAndKeepsReceivedSeparateFromDelivered() {
        var zone = mock(AffectedZone.class);
        var resource = mock(Resource.class);
        var unit = mock(MeasurementUnit.class);
        when(zones.findByIdAndEmergencia_Id(2L, 1L)).thenReturn(Optional.of(zone));
        when(zone.getId()).thenReturn(2L);
        when(resources.findById(3L)).thenReturn(Optional.of(resource));
        when(resource.getEstado()).thenReturn("ACTIVO");
        when(resource.getId()).thenReturn(3L);
        when(resource.getUnidadMedida()).thenReturn(unit);
        when(unit.getCodigo()).thenReturn("KG");
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        when(needs.saveAndFlush(any())).thenAnswer(call -> {
            Need need = spy((Need) call.getArgument(0));
            when(need.getId()).thenReturn(4L);
            return need;
        });
        when(lines.assignedToNeed(4L)).thenReturn(BigDecimal.ZERO);
        when(lines.deliveredToNeed(4L)).thenReturn(BigDecimal.ZERO);
        var response = service.create(1, 2, "ADMIN", new CreateNeedRequest(3L, new BigDecimal("12.500"), "ALTA", null));
        assertEquals(new BigDecimal("12.500"), response.pendienteEntregar());
        assertEquals(BigDecimal.ZERO, response.entregada());
        var record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).saveAndFlush(record.capture());
        assertEquals("CREACION_NECESIDAD", record.getValue().getAccion());
        assertTrue(record.getValue().getDatosNuevos().contains("\"zonaId\":2"));
    }

    @Test void computesSharedAvailabilityFromPartialReceiptMinusAllAssignments() {
        var need = mock(Need.class);
        var zone = mock(AffectedZone.class);
        var emergency = mock(Emergency.class);
        var resource = mock(Resource.class);
        var source = mock(DonationLine.class);
        var donation = mock(Donation.class);
        when(needs.findById(4L)).thenReturn(Optional.of(need));
        when(need.getZona()).thenReturn(zone);
        when(zone.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(1L);
        when(need.getRecurso()).thenReturn(resource);
        when(need.getEstado()).thenReturn("PENDIENTE");
        when(resource.getId()).thenReturn(3L);
        when(resource.getEstado()).thenReturn("ACTIVO");
        when(donations.findSources(1L, 3L, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(source)));
        when(source.getId()).thenReturn(5L);
        when(source.getDonacion()).thenReturn(donation);
        when(source.getCantidadRecibidaAcopio()).thenReturn(new BigDecimal("7"));
        when(donation.getEstado()).thenReturn("REGISTRADA");
        when(lines.assignedToDonation(5L)).thenReturn(new BigDecimal("5"));
        when(lines.assignedToPair(4L, 5L)).thenReturn(new BigDecimal("3"));
        when(lines.deliveredToPair(4L, 5L)).thenReturn(BigDecimal.ONE);
        var response = service.sources(4, 0).content().getFirst();
        assertEquals(new BigDecimal("2"), response.disponible());
        assertEquals(BigDecimal.ONE, response.entregadaNecesidad());
        assertTrue(response.habilitada());
    }

    @Test void rejectsWrongZoneAndInvalidQuantityWithoutWriting() {
        assertThrows(ResponseStatusException.class, () -> service.list(1, 99, 0));
        assertThrows(ResponseStatusException.class, () -> service.create(1, 2, "admin",
                new CreateNeedRequest(3L, new BigDecimal("0.0001"), "ALTA", null)));
        verifyNoInteractions(needs, resources, audit);
    }
}
