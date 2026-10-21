package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.donations.application.DonationQueryService;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;
import pe.redsolidaria.modules.resources.domain.Resource;

class DonationQueryServiceTest {
    private DonationRepository donations;
    private DonationMovementRepository movements;
    private DonationQueryService service;

    @BeforeEach
    void setUp() {
        donations = mock(DonationRepository.class);
        movements = mock(DonationMovementRepository.class);
        service = new DonationQueryService(donations, movements);
    }

    @Test
    void returnsDonationDetailsAndChronologicalMovementHistoryWithoutUserSecrets() {
        LocalDateTime registeredAt = LocalDateTime.parse("2026-04-02T10:00:00");
        LocalDateTime movedAt = LocalDateTime.parse("2026-04-02T10:05:00");
        Donation donation = mock(Donation.class);
        Donor donor = mock(Donor.class);
        CollectionCenter center = mock(CollectionCenter.class);
        Emergency emergency = mock(Emergency.class);
        DonationLine line = mock(DonationLine.class);
        Resource resource = mock(Resource.class);
        MeasurementUnit unit = mock(MeasurementUnit.class);
        DonationMovement movement = mock(DonationMovement.class);
        DonationMovement laterMovement = mock(DonationMovement.class);
        UserAccount responsible = mock(UserAccount.class);

        when(donations.findByCodigoTrazabilidad("RS-TRACE-1")).thenReturn(Optional.of(donation));
        when(donation.getId()).thenReturn(31L);
        when(donation.getCodigoTrazabilidad()).thenReturn("RS-TRACE-1");
        when(donation.getFechaRegistro()).thenReturn(registeredAt);
        when(donation.getFechaIngreso()).thenReturn(registeredAt);
        when(donation.getEstado()).thenReturn("REGISTRADA");
        when(donation.getObservaciones()).thenReturn("Recepción inicial");
        when(donation.getDonante()).thenReturn(donor);
        when(donor.getId()).thenReturn(8L);
        when(donor.getNombre()).thenReturn("Donante de prueba");
        when(donation.getCentroAcopio()).thenReturn(center);
        when(center.getId()).thenReturn(5L);
        when(center.getNombre()).thenReturn("Centro principal");
        when(center.getUbicacion()).thenReturn("Miraflores");
        when(donation.getEmergencia()).thenReturn(emergency);
        when(emergency.getId()).thenReturn(4L);
        when(emergency.getNombre()).thenReturn("Emergencia norte");
        when(donation.getDetalles()).thenReturn(List.of(line));
        when(line.getRecurso()).thenReturn(resource);
        when(resource.getId()).thenReturn(12L);
        when(resource.getNombre()).thenReturn("Agua");
        when(resource.getUnidadMedida()).thenReturn(unit);
        when(unit.getCodigo()).thenReturn("L");
        when(unit.getNombre()).thenReturn("Litro");
        when(unit.getSimbolo()).thenReturn("L");
        when(line.getCantidadDonada()).thenReturn(new BigDecimal("20.000"));
        when(line.getCantidadRecibidaAcopio()).thenReturn(new BigDecimal("18.000"));
        when(movements.findAllByDonacion_IdOrderByFechaHoraAscIdAsc(31L))
                .thenReturn(List.of(movement, laterMovement));
        when(movement.getEstadoAnterior()).thenReturn(null);
        when(movement.getEstadoNuevo()).thenReturn("REGISTRADA");
        when(movement.getFechaHora()).thenReturn(movedAt);
        when(movement.getUbicacion()).thenReturn("Miraflores");
        when(movement.getObservaciones()).thenReturn("Recepción inicial");
        when(movement.getResponsable()).thenReturn(responsible);
        when(responsible.getNombre()).thenReturn("Ana");
        when(responsible.getApellido()).thenReturn("Pérez");
        when(laterMovement.getEstadoAnterior()).thenReturn("REGISTRADA");
        when(laterMovement.getEstadoNuevo()).thenReturn("RECIBIDA");
        when(laterMovement.getFechaHora()).thenReturn(movedAt.plusMinutes(10));
        when(laterMovement.getResponsable()).thenReturn(responsible);

        var response = service.findByTrackingCode(" RS-TRACE-1 ");

        assertEquals("RS-TRACE-1", response.codigoTrazabilidad());
        assertEquals("Donante de prueba", response.donante().nombre());
        assertEquals("Centro principal", response.centroAcopio().nombre());
        assertEquals("Emergencia norte", response.emergenciaNombre());
        assertEquals(new BigDecimal("20.000"), response.detalles().getFirst().cantidadDonada());
        assertEquals("Litro", response.detalles().getFirst().unidadNombre());
        assertEquals("REGISTRADA", response.movimientos().getFirst().estadoNuevo());
        assertEquals(movedAt, response.movimientos().getFirst().fechaHora());
        assertEquals("RECIBIDA", response.movimientos().get(1).estadoNuevo());
        assertEquals("Ana Pérez", response.movimientos().getFirst().responsableNombre());
        verify(movements).findAllByDonacion_IdOrderByFechaHoraAscIdAsc(31L);

        var publicResponse = service.findPublicByTrackingCode("RS-TRACE-1");
        assertEquals("RS-TRACE-1", publicResponse.codigoTrazabilidad());
        assertEquals("Centro principal", publicResponse.centroAcopio().nombre());
        assertEquals("Agua", publicResponse.detalles().getFirst().recursoNombre());
        assertEquals("REGISTRADA", publicResponse.movimientos().getFirst().estadoNuevo());
        assertFalse(publicResponse.toString().contains("Donante de prueba"));
        assertFalse(publicResponse.toString().contains("Ana"));
        assertFalse(publicResponse.toString().contains("Recepci"));
    }

    @Test
    void returnsNotFoundWhenTrackingCodeDoesNotExist() {
        when(donations.findByCodigoTrazabilidad("RS-MISSING")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findByTrackingCode("RS-MISSING"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
