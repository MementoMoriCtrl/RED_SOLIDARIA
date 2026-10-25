package pe.redsolidaria.modules.donations.application;

import static pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.donations.persistence.DonorRepository;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.resources.persistence.CollectionCenterRepository;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;

@Service
public class DonationRegistrationService {
    public static final String INITIAL_STATE = "REGISTRADA";
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final DonationRepository donations;
    private final DonationLineRepository lines;
    private final DonationMovementRepository movements;
    private final DonorRepository donors;
    private final ResourceRepository resources;
    private final EmergencyRepository emergencies;
    private final CollectionCenterRepository centers;
    private final UserRepository users;
    private final TrackingCodeGenerator codeGenerator;
    private final EntityManager entityManager;

    public DonationRegistrationService(DonationRepository donations, DonationLineRepository lines,
            DonationMovementRepository movements, DonorRepository donors, ResourceRepository resources,
            EmergencyRepository emergencies, CollectionCenterRepository centers, UserRepository users,
            TrackingCodeGenerator codeGenerator, EntityManager entityManager) {
        this.donations = donations;
        this.lines = lines;
        this.movements = movements;
        this.donors = donors;
        this.resources = resources;
        this.emergencies = emergencies;
        this.centers = centers;
        this.users = users;
        this.codeGenerator = codeGenerator;
        this.entityManager = entityManager;
    }

    @Transactional
    public DonationResponse register(CreateDonationRequest request, String authenticatedEmail) {
        validateRequest(request);
        Donor donor = donors.findById(request.donanteId())
                .orElseThrow(() -> notFound("Donante no encontrado"));
        UserAccount responsible = users.findByCorreo(authenticatedEmail.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        Emergency emergency = request.emergenciaId() == null ? null : emergencies.findById(request.emergenciaId())
                .orElseThrow(() -> notFound("Emergencia no encontrada"));
        CollectionCenter center = request.centroAcopioId() == null ? null : centers.findById(request.centroAcopioId())
                .orElseThrow(() -> notFound("Centro de acopio no encontrado"));
        if (emergency == null && center == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicarse una emergencia o un centro de acopio");
        }

        List<Resource> foundResources = resources.findAllById(
                request.detalles().stream().map(DonationLineRequest::recursoId).toList());
        if (foundResources.size() != request.detalles().size()) {
            throw notFound("Uno o más recursos no existen");
        }
        if (foundResources.stream().anyMatch(resource -> !"ACTIVO".equals(resource.getEstado())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los recursos deben estar activos");
        Map<Long, Resource> resourcesById = foundResources.stream()
                .collect(Collectors.toMap(Resource::getId, Function.identity()));

        String trackingCode = nextAvailableCode();
        Donation donation = new Donation(donor, emergency, center, responsible, trackingCode,
                null, INITIAL_STATE, clean(request.observaciones()));
        try {
            donation = donations.saveAndFlush(donation);
        } catch (DataIntegrityViolationException duplicateOrConstraintFailure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se pudo registrar la donación por una colisión de código o restricción de integridad");
        }

        List<DonationLine> savedLines = new java.util.ArrayList<>();
        for (DonationLineRequest detail : request.detalles()) {
            savedLines.add(new DonationLine(donation, resourcesById.get(detail.recursoId()), detail.cantidad()));
        }
        savedLines = lines.saveAllAndFlush(savedLines);
        movements.saveAndFlush(new DonationMovement(donation, responsible, null, INITIAL_STATE,
                clean(request.ubicacion()), clean(request.observaciones())));

        entityManager.refresh(donation);
        return toResponse(donation, savedLines);
    }

    private String nextAvailableCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.nextCode();
            if (candidate != null && !candidate.isBlank() && !donations.existsByCodigoTrazabilidad(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pudo generar un código de seguimiento único; inténtelo nuevamente");
    }

    private static void validateRequest(CreateDonationRequest request) {
        if (request == null || request.detalles() == null || request.detalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La donación debe incluir al menos un detalle");
        }
        if (request.donanteId() == null || request.donanteId() <= 0
                || (request.ubicacion() != null && request.ubicacion().length() > 200)
                || (request.observaciones() != null && request.observaciones().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa donante, ubicación y observaciones");
        Set<Long> resourceIds = new HashSet<>();
        for (DonationLineRequest detail : request.detalles()) {
            if (detail == null || detail.recursoId() == null || detail.recursoId() <= 0 || !resourceIds.add(detail.recursoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada recurso debe ser válido y aparecer una sola vez por donación");
            }
            BigDecimal quantity = detail.cantidad();
            if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 3
                    || quantity.precision() - quantity.scale() > 11) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Las cantidades deben ser positivas y compatibles con DECIMAL(14,3)");
            }
        }
    }

    private static DonationResponse toResponse(Donation donation, List<DonationLine> savedLines) {
        List<DonationLineResponse> details = savedLines.stream()
                .map(line -> new DonationLineResponse(line.getRecurso().getId(), line.getRecurso().getNombre(), line.getCantidadDonada()))
                .toList();
        return new DonationResponse(donation.getId(), donation.getCodigoTrazabilidad(), donation.getFechaRegistro(),
                donation.getFechaIngreso(), donation.getEstado(), donation.getDonante().getId(),
                donation.getDonante().getNombre(), donation.getEmergencia() == null ? null : donation.getEmergencia().getId(),
                donation.getCentroAcopio() == null ? null : donation.getCentroAcopio().getId(), details);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
