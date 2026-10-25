package pe.redsolidaria.modules.emergencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.CreateEmergencyRequest;
import pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.SaveEmergencyRequest;
import pe.redsolidaria.modules.emergencies.application.EmergencyManagementService;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.emergencies.persistence.AffectedZoneRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;

class EmergencyManagementServiceTest {
    EmergencyRepository emergencies = mock(EmergencyRepository.class);
    AffectedZoneRepository zones = mock(AffectedZoneRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditRecordRepository audit = mock(AuditRecordRepository.class);
    EntityManager entityManager = mock(EntityManager.class);
    EmergencyManagementService service = new EmergencyManagementService(emergencies, zones, users, audit, entityManager);

    @Test void appliesSearchAndStablePagination() {
        when(emergencies.search(eq("inundación"), eq("ACTIVA"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 8));
        var result = service.list(" inundación ", " ACTIVA ", 1, 5);
        assertEquals(8, result.totalElements());
        assertEquals(1, result.page());
        assertEquals(2, result.totalPages());
        verify(emergencies).search(eq("inundación"), eq("ACTIVA"), argThat(pageable ->
                pageable.getSort().equals(Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id")))));
    }

    @Test void createsEmergencyAndAuditInSameServiceTransaction() {
        var request = new CreateEmergencyRequest("Inundación Norte", "INUNDACION", "Descripción",
                LocalDate.parse("2026-06-01"), null, "ACTIVA");
        var emergency = mock(Emergency.class);
        var actor = mock(UserAccount.class);
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(actor));
        when(emergencies.saveAndFlush(any(Emergency.class))).thenReturn(emergency);
        when(emergency.getId()).thenReturn(12L);
        when(emergency.getNombre()).thenReturn("Inundación Norte");
        when(emergency.getTipo()).thenReturn("INUNDACION");
        when(emergency.getDescripcion()).thenReturn("Descripción");
        when(emergency.getFechaInicio()).thenReturn(LocalDate.parse("2026-06-01"));
        when(emergency.getEstado()).thenReturn("ACTIVA");
        when(zones.findAllByEmergencia_IdOrderByIdAsc(12L)).thenReturn(List.of());

        var result = service.create("admin@example.test", request);

        assertEquals(12L, result.id());
        InOrder order = inOrder(emergencies, entityManager, audit);
        order.verify(emergencies).saveAndFlush(any(Emergency.class));
        order.verify(entityManager).refresh(emergency);
        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        order.verify(audit).saveAndFlush(record.capture());
        assertEquals("CREACION_EMERGENCIA", record.getValue().getAccion());
        assertEquals("EMERGENCIA", record.getValue().getEntidad());
        assertEquals("12", record.getValue().getIdRegistro());
        org.junit.jupiter.api.Assertions.assertTrue(record.getValue().getDatosNuevos().contains("\"fechaInicio\":\"2026-06-01\""));
    }

    @Test void rejectsEndDateBeforeStartWithoutWriting() {
        var request = new SaveEmergencyRequest("Inundación", "INUNDACION", null,
                LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-01"));
        assertThrows(ResponseStatusException.class, () -> service.update(1L, "admin@example.test", request));
        verifyNoInteractions(emergencies, zones, users, audit, entityManager);
    }

    @Test void rejectsUpdatingZoneBelongingToAnotherEmergencyWithoutWriting() {
        when(emergencies.findById(1L)).thenReturn(Optional.of(mock(Emergency.class)));
        when(zones.findByIdAndEmergencia_Id(9L, 1L)).thenReturn(Optional.empty());
        var request = new pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.SaveAffectedZoneRequest(
                "Zona Norte", null, null);
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.updateZone(1L, 9L, "admin@example.test", request));
        assertEquals(404, failure.getStatusCode().value());
        verifyNoInteractions(users, audit, entityManager);
        verify(zones, never()).flush();
    }

    @Test void recordsPreviousAndNewStateWithTheAuthenticatedActor() {
        var emergency = new Emergency("Inundación", "INUNDACION", null,
                LocalDate.parse("2026-06-01"), null, "ACTIVA");
        var actor = mock(UserAccount.class);
        when(emergencies.findById(1L)).thenReturn(Optional.of(emergency));
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(actor));
        service.changeState(1L, "ADMIN@example.test",
                new pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.ChangeEmergencyStateRequest("CERRADA"));
        var record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).saveAndFlush(record.capture());
        assertEquals(actor, record.getValue().getUsuario());
        assertEquals("CAMBIO_ESTADO_EMERGENCIA", record.getValue().getAccion());
        org.junit.jupiter.api.Assertions.assertTrue(record.getValue().getDatosAnteriores().contains("\"estado\":\"ACTIVA\""));
        org.junit.jupiter.api.Assertions.assertTrue(record.getValue().getDatosNuevos().contains("\"estado\":\"CERRADA\""));
    }
}
