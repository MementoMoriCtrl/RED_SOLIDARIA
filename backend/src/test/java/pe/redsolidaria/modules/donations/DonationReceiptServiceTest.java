package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.RegisterReceiptRequest;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.ReceiptLineRequest;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.resources.domain.Resource;

class DonationReceiptServiceTest {
    DonationRepository donations = mock(DonationRepository.class);
    UserRepository users = mock(UserRepository.class);
    DonationMovementRepository movements = mock(DonationMovementRepository.class);
    AuditRecordRepository audits = mock(AuditRecordRepository.class);
    EntityManager entityManager = mock(EntityManager.class);
    DonationReceiptService service = new DonationReceiptService(donations, users, movements, audits, entityManager);
    Donation donation = mock(Donation.class);
    DonationLine line = mock(DonationLine.class);
    Resource resource = mock(Resource.class);
    UserAccount responsible = mock(UserAccount.class);
    AtomicReference<BigDecimal> accumulated = new AtomicReference<>(new BigDecimal("2.000"));

    @BeforeEach void setup() {
        when(donations.findByIdForUpdate(22L)).thenReturn(Optional.of(donation));
        when(donation.getEstado()).thenReturn("REGISTRADA");
        when(donation.getDetalles()).thenReturn(List.of(line));
        when(donation.getId()).thenReturn(22L);
        when(line.getRecurso()).thenReturn(resource);
        when(resource.getId()).thenReturn(8L);
        when(resource.getNombre()).thenReturn("Arroz");
        when(line.getCantidadDonada()).thenReturn(new BigDecimal("10.000"));
        when(line.getCantidadRecibidaAcopio()).thenAnswer(invocation -> accumulated.get());
        doAnswer(invocation -> { accumulated.set(accumulated.get().add(invocation.getArgument(0))); return null; })
                .when(line).recibir(any());
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(responsible));
        when(audits.saveAndFlush(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void recordsPartialReceiptAtomicallyAndLocksBeforeReadingDetails() {
        var request = new RegisterReceiptRequest(
                List.of(new ReceiptLineRequest(8L, new BigDecimal("3.000"))),
                "Centro Norte", "Primera entrega");
        var response = service.register(22L, "admin@example.test", request);
        assertEquals("REGISTRADA", response.estado());
        assertEquals(new BigDecimal("3.000"), response.detalles().getFirst().cantidadRecibidaOperacion());
        verify(line).recibir(new BigDecimal("3.000"));
        verify(movements).saveAndFlush(any(DonationMovement.class));
        ArgumentCaptor<AuditRecord> auditRecord = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audits).saveAndFlush(auditRecord.capture());
        assertEquals(DonationReceiptService.AUDIT_ACTION, auditRecord.getValue().getAccion());
        assertEquals("DONACION", auditRecord.getValue().getEntidad());
        org.junit.jupiter.api.Assertions.assertTrue(auditRecord.getValue().getDatosNuevos().contains("\"donacionId\":22"));
        org.junit.jupiter.api.Assertions.assertTrue(auditRecord.getValue().getDatosNuevos().contains("\"cantidadRecibidaOperacion\":3.000"));
        org.junit.jupiter.api.Assertions.assertTrue(auditRecord.getValue().getDatosNuevos().contains("\"cantidadRecibidaAcumulada\":5.000"));
        org.junit.jupiter.api.Assertions.assertTrue(auditRecord.getValue().getDatosNuevos().contains("\"ubicacion\":\"Centro Norte\""));
        org.junit.jupiter.api.Assertions.assertTrue(auditRecord.getValue().getDatosNuevos().contains("\"observaciones\":\"Primera entrega\""));
        InOrder order = inOrder(donations, donation);
        order.verify(donations).findByIdForUpdate(22L);
        order.verify(donation).getDetalles();
    }

    @Test void rejectsOverReceiptBeforeAnyWrite() {
        var request = new RegisterReceiptRequest(
                List.of(new ReceiptLineRequest(8L, new BigDecimal("9.000"))), null, null);
        assertThrows(ResponseStatusException.class, () -> service.register(22L, "admin@example.test", request));
        verify(line, never()).recibir(any());
        verify(movements, never()).saveAndFlush(any());
        verify(audits, never()).saveAndFlush(any());
    }

    @Test void rejectsDuplicateResourceLines() {
        var request = new RegisterReceiptRequest(List.of(
                new ReceiptLineRequest(8L, BigDecimal.ONE),
                new ReceiptLineRequest(8L, BigDecimal.ONE)), null, null);
        assertThrows(ResponseStatusException.class, () -> service.register(22L, "admin@example.test", request));
        verify(movements, never()).saveAndFlush(any());
    }
}
