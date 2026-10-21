package pe.redsolidaria.modules.donations.application;

import static pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;

@Service
public class DonationQueryService {
    private final DonationRepository donations;
    private final DonationMovementRepository movements;

    public DonationQueryService(DonationRepository donations, DonationMovementRepository movements) {
        this.donations = donations;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public DonationTraceResponse findByTrackingCode(String trackingCode) {
        String normalizedCode = trackingCode == null ? "" : trackingCode.trim();
        Donation donation = donations.findByCodigoTrazabilidad(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));

        List<DonationDetailSummary> details = donation.getDetalles().stream()
                .map(DonationQueryService::toDetailSummary)
                .toList();
        List<DonationMovementSummary> history = movements
                .findAllByDonacion_IdOrderByFechaHoraAscIdAsc(donation.getId()).stream()
                .map(DonationQueryService::toMovementSummary)
                .toList();

        DonorSummary donor = new DonorSummary(donation.getDonante().getId(), donation.getDonante().getNombre());
        CollectionCenterSummary center = donation.getCentroAcopio() == null ? null
                : new CollectionCenterSummary(donation.getCentroAcopio().getId(),
                        donation.getCentroAcopio().getNombre(), donation.getCentroAcopio().getUbicacion());
        Long emergencyId = donation.getEmergencia() == null ? null : donation.getEmergencia().getId();
        String emergencyName = donation.getEmergencia() == null ? null : donation.getEmergencia().getNombre();

        return new DonationTraceResponse(donation.getId(), donation.getCodigoTrazabilidad(),
                donation.getFechaRegistro(), donation.getFechaIngreso(), donation.getEstado(),
                donation.getObservaciones(), donor, emergencyId, emergencyName, center, details, history);
    }

    @Transactional(readOnly = true)
    public PublicDonationTraceResponse findPublicByTrackingCode(String trackingCode) {
        String normalizedCode = trackingCode == null ? "" : trackingCode.trim();
        Donation donation = donations.findByCodigoTrazabilidad(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));

        PublicCollectionCenterSummary center = donation.getCentroAcopio() == null ? null
                : new PublicCollectionCenterSummary(donation.getCentroAcopio().getNombre(),
                        donation.getCentroAcopio().getUbicacion());
        List<PublicDonationDetailSummary> details = donation.getDetalles().stream()
                .map(line -> {
                    var resource = line.getRecurso();
                    var unit = resource.getUnidadMedida();
                    return new PublicDonationDetailSummary(resource.getNombre(), line.getCantidadDonada(),
                            line.getCantidadRecibidaAcopio(), unit.getCodigo(), unit.getNombre(), unit.getSimbolo());
                })
                .toList();
        List<PublicDonationMovementSummary> history = movements
                .findAllByDonacion_IdOrderByFechaHoraAscIdAsc(donation.getId()).stream()
                .map(movement -> new PublicDonationMovementSummary(movement.getEstadoAnterior(),
                        movement.getEstadoNuevo(), movement.getFechaHora(), movement.getUbicacion()))
                .toList();

        return new PublicDonationTraceResponse(donation.getCodigoTrazabilidad(), donation.getFechaRegistro(),
                donation.getFechaIngreso(), donation.getEstado(), center, details, history);
    }

    private static DonationDetailSummary toDetailSummary(DonationLine line) {
        var resource = line.getRecurso();
        var unit = resource.getUnidadMedida();
        return new DonationDetailSummary(resource.getId(), resource.getNombre(), line.getCantidadDonada(),
                line.getCantidadRecibidaAcopio(), unit.getCodigo(), unit.getNombre(), unit.getSimbolo());
    }

    private static DonationMovementSummary toMovementSummary(DonationMovement movement) {
        return new DonationMovementSummary(movement.getEstadoAnterior(), movement.getEstadoNuevo(),
                movement.getFechaHora(), movement.getUbicacion(), movement.getObservaciones(),
                responsibleName(movement.getResponsable()));
    }

    private static String responsibleName(UserAccount user) {
        if (user == null) return null;
        return Stream.of(user.getNombre(), user.getApellido())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
