package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.CreateDonationRequest;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonationLineRequest;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;
import pe.redsolidaria.modules.donations.application.TrackingCodeGenerator;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.donations.persistence.DonorRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.resources.persistence.CollectionCenterRepository;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;

class DonationRegistrationServiceTest {
    private DonationRepository donations;
    private DonationLineRepository lines;
    private DonationMovementRepository movements;
    private DonorRepository donors;
    private ResourceRepository resources;
    private EmergencyRepository emergencies;
    private CollectionCenterRepository centers;
    private UserRepository users;
    private TrackingCodeGenerator codes;
    private DonationRegistrationService service;
    private Donor donor;
    private Resource resource;
    private UserAccount user;
    private CollectionCenter center;

    @BeforeEach
    void setUp() {
        donations = mock(DonationRepository.class);
        lines = mock(DonationLineRepository.class);
        movements = mock(DonationMovementRepository.class);
        donors = mock(DonorRepository.class);
        resources = mock(ResourceRepository.class);
        emergencies = mock(EmergencyRepository.class);
        centers = mock(CollectionCenterRepository.class);
        users = mock(UserRepository.class);
        codes = mock(TrackingCodeGenerator.class);
        service = new DonationRegistrationService(donations, lines, movements, donors, resources,
                emergencies, centers, users, codes, mock(EntityManager.class));
        donor = mock(Donor.class);
        resource = mock(Resource.class);
        user = mock(UserAccount.class);
        center = mock(CollectionCenter.class);
        when(donors.findById(11L)).thenReturn(Optional.of(donor));
        when(users.findByCorreo("responsable@example.test")).thenReturn(Optional.of(user));
        when(centers.findById(3L)).thenReturn(Optional.of(center));
        when(resources.findAllById(List.of(7L))).thenReturn(List.of(resource));
        when(resource.getId()).thenReturn(7L);
        when(resource.getNombre()).thenReturn("Agua");
        when(resource.getEstado()).thenReturn("ACTIVO");
        when(donor.getId()).thenReturn(11L);
        when(donor.getNombre()).thenReturn("Donante de prueba");
        when(center.getId()).thenReturn(3L);
        when(donations.saveAndFlush(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(codes.nextCode()).thenReturn("RS-UNICO");
    }

    @Test
    void registersDonationDetailsAndInitialMovementWithServerControlledFields() {
        var response = service.register(request(new BigDecimal("2.500")), "Responsable@example.test");

        assertEquals("RS-UNICO", response.codigoTrazabilidad());
        assertEquals(DonationRegistrationService.INITIAL_STATE, response.estado());
        assertEquals(11L, response.donanteId());
        assertEquals(1, response.detalles().size());
        verify(donations).saveAndFlush(argThat(d -> d.getUsuarioRegistro() == user
                && "RS-UNICO".equals(d.getCodigoTrazabilidad())
                && "REGISTRADA".equals(d.getEstado())));
        verify(movements).saveAndFlush(argThat(m -> m.getResponsable() == user
                && m.getEstadoAnterior() == null && "REGISTRADA".equals(m.getEstadoNuevo())));
    }

    @Test
    void rejectsUnknownDonorBeforeWriting() {
        when(donors.findById(11L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.register(request(new BigDecimal("1")), "responsable@example.test")).getStatusCode().value());
        verify(donations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownResourceBeforeWriting() {
        when(resources.findAllById(List.of(7L))).thenReturn(List.of());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.register(request(new BigDecimal("1")), "responsable@example.test")).getStatusCode().value());
        verify(donations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidQuantitiesAndDuplicateResourceLines() {
        for (BigDecimal quantity : List.of(BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("1.0000"), new BigDecimal("100000000000.000"))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.register(request(quantity), "responsable@example.test")).getStatusCode().value());
        }
        var repeated = new CreateDonationRequest(11L, null, 3L, null, null,
                List.of(new DonationLineRequest(7L, BigDecimal.ONE), new DonationLineRequest(7L, BigDecimal.ONE)));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.register(repeated, "responsable@example.test")).getStatusCode().value());
        verify(donations, never()).saveAndFlush(any());
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        AtomicInteger generated = new AtomicInteger();
        when(codes.nextCode()).thenAnswer(invocation -> generated.incrementAndGet() == 1 ? "RS-TAKEN" : "RS-UNICO");
        when(donations.existsByCodigoTrazabilidad("RS-TAKEN")).thenReturn(true);

        assertEquals("RS-UNICO", service.register(request(BigDecimal.ONE), "responsable@example.test").codigoTrazabilidad());
        verify(codes, times(2)).nextCode();
    }

    @Test
    void abortsAfterTheBoundedNumberOfCodeCollisions() {
        when(donations.existsByCodigoTrazabilidad(any())).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(BigDecimal.ONE), "responsable@example.test")).getStatusCode().value());
        verify(codes, times(5)).nextCode();
        verify(donations, never()).saveAndFlush(any());
    }

    private static CreateDonationRequest request(BigDecimal quantity) {
        return new CreateDonationRequest(11L, null, 3L, "Centro", "Registro inicial",
                List.of(new DonationLineRequest(7L, quantity)));
    }
}
