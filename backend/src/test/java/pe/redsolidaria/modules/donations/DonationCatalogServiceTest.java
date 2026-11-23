package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.dto.DonationCatalogDtos.CreateCatalogRequest;
import pe.redsolidaria.modules.donations.application.DonationCatalogService;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.donations.persistence.DonorRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;
import pe.redsolidaria.modules.resources.api.dto.ResourceDtos.UpdateMeasurementUnitRequest;
import pe.redsolidaria.modules.resources.persistence.CollectionCenterRepository;
import pe.redsolidaria.modules.resources.persistence.MeasurementUnitRepository;
import tools.jackson.databind.json.JsonMapper;

class DonationCatalogServiceTest {
    DonorRepository donors = mock(DonorRepository.class);
    CollectionCenterRepository centers = mock(CollectionCenterRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditRecordRepository audit = mock(AuditRecordRepository.class);
    MeasurementUnitRepository units = mock(MeasurementUnitRepository.class);
    DonationCatalogService service = new DonationCatalogService(donors, centers, mock(EmergencyRepository.class),
            users, audit, JsonMapper.builder().build(), units);

    @Test void updatesUnitWithoutChangingCodeAndAuditsBeforeAndAfter() {
        var unit = spy(new MeasurementUnit("KG", "Kilogramo", "kg")); when(unit.getId()).thenReturn(9L);
        when(units.findByIdForUpdate(9L)).thenReturn(Optional.of(unit));
        var actor = mock(UserAccount.class); when(users.findByCorreo("admin")).thenReturn(Optional.of(actor));
        var result = service.updateUnit(9, "ADMIN", new UpdateMeasurementUnitRequest(" Kilogramo editado ", "k"));
        assertEquals("KG", result.codigo()); assertEquals("Kilogramo editado", unit.getNombre());
        assertEquals("k", unit.getSimbolo());
        var record = ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(record.capture());
        assertSame(actor, record.getValue().getUsuario()); assertTrue(record.getValue().getDatosAnteriores().contains("Kilogramo"));
        assertTrue(record.getValue().getDatosNuevos().contains("Kilogramo editado"));
        verify(units, never()).delete(any());
    }
    @Test void rejectsInvalidUnitUpdatesAndMissingRecords() {
        assertThrows(ResponseStatusException.class, () -> service.updateUnit(9, "admin", new UpdateMeasurementUnitRequest(" ", null)));
        assertThrows(ResponseStatusException.class, () -> service.updateUnit(9, "admin", new UpdateMeasurementUnitRequest("a", "a".repeat(21))));
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.updateUnit(9, "admin", new UpdateMeasurementUnitRequest("a", null))).getStatusCode().value());
        verifyNoInteractions(audit);
    }

    @Test void editsDonorAndAuditsTheSameRecordWithoutCheckingHomonyms() {
        var donor = spy(new Donor("Anterior")); when(donor.getId()).thenReturn(7L);
        when(donors.findByIdForUpdate(7L)).thenReturn(Optional.of(donor));
        var actor = mock(UserAccount.class); when(users.findByCorreo("admin")).thenReturn(Optional.of(actor));
        var result = service.update("donantes", 7, "ADMIN", new CreateCatalogRequest(" Otro ", null, null));
        assertEquals(7L, result.id()); assertEquals("Otro", donor.getNombre());
        var record = ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(record.capture());
        assertSame(actor, record.getValue().getUsuario()); assertTrue(record.getValue().getDatosAnteriores().contains("Anterior"));
        assertTrue(record.getValue().getDatosNuevos().contains("Otro"));
        verify(donors, never()).delete(any());
    }
    @Test void editsCenterAndKeepsBeforeAndAfterIncludingDescription() {
        var center = spy(new CollectionCenter("Centro", "Lima", "Original")); when(center.getId()).thenReturn(8L);
        when(centers.findByIdForUpdate(8L)).thenReturn(Optional.of(center));
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        var result = service.update("centros", 8, "admin", new CreateCatalogRequest("Centro", "Piura", "Nueva"));
        assertEquals("Nueva", result.descripcion()); assertEquals("Piura", center.getUbicacion());
        var record = ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(record.capture());
        assertTrue(record.getValue().getDatosAnteriores().contains("Original"));
        assertTrue(record.getValue().getDatosNuevos().contains("Nueva"));
        verify(centers, never()).delete(any());
    }
    @Test void rejectsInvalidDataAndUnknownRecords() {
        assertThrows(ResponseStatusException.class, () -> service.update("donantes", 1, "admin", new CreateCatalogRequest(" ", null, null)));
        assertThrows(ResponseStatusException.class, () -> service.update("centros", 1, "admin", new CreateCatalogRequest("x", null, "á".repeat(32768))));
        when(users.findByCorreo("admin")).thenReturn(Optional.of(mock(UserAccount.class)));
        assertThrows(ResponseStatusException.class, () -> service.update("donantes", 1, "admin", new CreateCatalogRequest("x", null, null)));
        verifyNoInteractions(audit);
    }
}
