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
import pe.redsolidaria.modules.needs.api.dto.NeedDtos.UpdateNeedRequest;
import pe.redsolidaria.modules.needs.api.dto.NeedDtos.TransitionNeedRequest;
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
    pe.redsolidaria.modules.logistics.LogisticsRepository logistics = mock(pe.redsolidaria.modules.logistics.LogisticsRepository.class);
    NeedManagementService service = new NeedManagementService(needs, zones, resources, donations, lines, users, audit, JsonMapper.builder().build(), logistics);

    Need transitionFixture() {
        var zone = mock(AffectedZone.class); var emergency = mock(Emergency.class);
        var resource = mock(Resource.class); var unit = mock(MeasurementUnit.class);
        when(zone.getId()).thenReturn(2L); when(zone.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(1L); when(resource.getUnidadMedida()).thenReturn(unit);
        var need = spy(new Need(zone, resource, BigDecimal.TEN, "ALTA", null));
        when(need.getId()).thenReturn(4L); when(needs.findByIdForUpdate(4L)).thenReturn(Optional.of(need));
        when(lines.assignedToNeed(4L)).thenReturn(BigDecimal.TEN);
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        return need;
    }

    @Test void closesOnlyWhenFullyDeliveredAndAuditsReason() {
        var need = transitionFixture(); var request = new TransitionNeedRequest(" cumplida ");
        when(lines.deliveredToNeed(4L)).thenReturn(new BigDecimal("9"));
        assertThrows(ResponseStatusException.class, () -> service.transition(1, 2, 4, "admin", "CERRADA", request));
        verifyNoInteractions(audit); assertEquals("PENDIENTE", need.getEstado());
        when(lines.deliveredToNeed(4L)).thenReturn(BigDecimal.TEN);
        assertEquals("CERRADA", service.transition(1, 2, 4, "admin", "CERRADA", request).estado());
        var record = ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(record.capture());
        assertEquals("TRANSICION_NECESIDAD", record.getValue().getAccion());
        assertTrue(record.getValue().getDatosAnteriores().contains("PENDIENTE"));
        assertTrue(record.getValue().getDatosNuevos().contains("cumplida"));
        assertThrows(ResponseStatusException.class, () -> service.update(1, 2, 4, "admin", new UpdateNeedRequest(BigDecimal.TEN, "ALTA", null)));
    }

    @Test void cancellationAllowsPartialDeliveryOnlyWithoutPendingReservations() {
        var need = transitionFixture(); var request = new TransitionNeedRequest("Cambio de necesidades");
        when(lines.deliveredToNeed(4L)).thenReturn(new BigDecimal("4"));
        when(lines.sourcesWithPendingReservations(4L)).thenReturn(List.of(5L));
        assertThrows(ResponseStatusException.class, () -> service.transition(1, 2, 4, "admin", "CANCELADA", request));
        verifyNoInteractions(audit); assertEquals("PENDIENTE", need.getEstado());
        when(lines.sourcesWithPendingReservations(4L)).thenReturn(List.of());
        assertEquals("CANCELADA", service.transition(1, 2, 4, "admin", "CANCELADA", request).estado());
        assertThrows(ResponseStatusException.class, () -> service.transition(1, 2, 4, "admin", "CERRADA", request));
        verify(lines, never()).saveAndFlush(any());
    }

    @Test void registersNeedWithAuditAndKeepsReceivedSeparateFromDelivered() {
        var zone = mock(AffectedZone.class);
        var resource = mock(Resource.class);
        var unit = mock(MeasurementUnit.class);
        when(zones.findByIdAndEmergencia_Id(2L, 1L)).thenReturn(Optional.of(zone));
        when(zone.getId()).thenReturn(2L);
        when(resources.findByIdForUpdate(3L)).thenReturn(Optional.of(resource));
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
        var response = service.create(1, 2, "ADMIN", new CreateNeedRequest(3L, new BigDecimal("100.000"), "ALTA", null));
        var order = inOrder(resources, needs);
        order.verify(resources).findByIdForUpdate(3L);
        order.verify(needs).saveAndFlush(any());
        assertEquals(new BigDecimal("100.000"), response.pendienteEntregar());
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
        when(logistics.received(5L)).thenReturn(new BigDecimal("7"));
        when(lines.distributionAssigned(5L)).thenReturn(new BigDecimal("5"));
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

    @Test void updatesOnlyEditableFieldsAndAuditsBeforeAndAfter() {
        var zone = mock(AffectedZone.class); var emergency = mock(Emergency.class);
        var resource = mock(Resource.class); var unit = mock(MeasurementUnit.class);
        when(zone.getId()).thenReturn(2L); when(zone.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(1L); when(resource.getId()).thenReturn(3L);
        when(resource.getUnidadMedida()).thenReturn(unit);
        var need = spy(new Need(zone, resource, new BigDecimal("10"), "ALTA", "antes"));
        when(need.getId()).thenReturn(4L);
        when(needs.findByIdForUpdate(4L)).thenReturn(Optional.of(need));
        when(lines.assignedToNeed(4L)).thenReturn(new BigDecimal("4"));
        when(lines.deliveredToNeed(4L)).thenReturn(BigDecimal.ONE);
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        var result = service.update(1, 2, 4, "ADMIN", new UpdateNeedRequest(new BigDecimal("4"), " MEDIA ", " después "));
        assertEquals(new BigDecimal("4"), result.requerida());
        assertEquals("MEDIA", result.prioridad()); assertEquals("después", result.observaciones());
        assertSame(zone, need.getZona()); assertSame(resource, need.getRecurso());
        var record = ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(record.capture());
        assertEquals("EDICION_NECESIDAD", record.getValue().getAccion());
        assertTrue(record.getValue().getDatosAnteriores().contains("antes"));
        assertTrue(record.getValue().getDatosNuevos().contains("después"));
    }

    @Test void rejectsReductionBelowAssignedAndWrongZoneWithoutWrites() {
        var zone = mock(AffectedZone.class); var emergency = mock(Emergency.class);
        when(zone.getId()).thenReturn(2L); when(zone.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(1L);
        var need = new Need(zone, mock(Resource.class), BigDecimal.TEN, "ALTA", null);
        when(needs.findByIdForUpdate(4L)).thenReturn(Optional.of(need));
        when(lines.assignedToNeed(4L)).thenReturn(new BigDecimal("4"));
        var request = new UpdateNeedRequest(new BigDecimal("3"), "BAJA", null);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.update(1, 2, 4, "admin", request)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.update(1, 99, 4, "admin", request)).getStatusCode().value());
        assertEquals(BigDecimal.TEN, need.getCantidadRequerida()); assertEquals("ALTA", need.getPrioridad());
        verify(needs, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
}
