package pe.redsolidaria.modules.donations.application;

import static pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;

import java.util.List;
import java.util.Set;
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
    private static final Set<String> DONATION_STATES = Set.of(
            "REGISTRADA", "RECIBIDA", "ALMACENADA", "CLASIFICADA",
            "EN_TRANSITO", "ENTREGADA", "DEVUELTA", "INCIDENCIA");
    private final DonationRepository donations;
    private final DonationMovementRepository movements;
    private final DonationCustodyService custody;

    public DonationQueryService(DonationRepository donations, DonationMovementRepository movements, DonationCustodyService custody) {
        this.donations = donations;
        this.movements = movements;
        this.custody = custody;
    }

    @Transactional(readOnly = true)
    public DonationTraceResponse findByTrackingCode(String trackingCode) {
        String normalizedCode = trackingCode == null ? "" : trackingCode.trim();
        Donation donation = donations.findByCodigoTrazabilidad(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));

        List<DonationDetailSummary> details = donation.getDetalles().stream()
                .map(DonationQueryService::toDetailSummary)
                .toList();
        var recordedHistory = movements.findAllByDonacion_IdOrderByFechaHoraAscIdAsc(donation.getId());
        List<DonationMovementSummary> history = recordedHistory.stream()
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
                donation.getObservaciones(), donor, emergencyId, emergencyName, center, details, history,
                custody.read(donation, recordedHistory));
    }

    @Transactional(readOnly = true)
    public List<AdministrativeDonationListItem> listAdministrative(String trackingCode, String state) {
        return listAdministrative(trackingCode, state, null, null);
    }

    @Transactional(readOnly = true)
    public List<AdministrativeDonationListItem> listAdministrative(String trackingCode, String state,
            java.time.LocalDate from, java.time.LocalDate to) {
        pe.redsolidaria.modules.reports.ReportDates.validate(from, to);
        String normalizedCode = trackingCode == null || trackingCode.isBlank() ? null : trackingCode.trim();
        if (normalizedCode != null && normalizedCode.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de trazabilidad supera 100 caracteres");
        }
        String normalizedState = state == null || state.isBlank() ? null : state.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedState != null && !DONATION_STATES.contains(normalizedState)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado indicado no es válido");
        }
        var rows = from == null && to == null ? donations.findAdministrativeList(normalizedCode, normalizedState)
                : donations.findAdministrativeListByDate(normalizedCode, normalizedState,
                    from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay());
        return rows.stream()
                .map(DonationQueryService::toAdministrativeListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public DonationTraceResponse findAdministrativeById(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador debe ser positivo");
        }
        Donation donation = donations.findAdministrativeById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));
        List<DonationDetailSummary> details = donation.getDetalles().stream()
                .map(DonationQueryService::toDetailSummary)
                .toList();
        var recordedHistory = movements.findAllByDonacion_IdOrderByFechaHoraAscIdAsc(donation.getId());
        List<DonationMovementSummary> history = recordedHistory.stream()
                .map(DonationQueryService::toMovementSummary)
                .toList();
        return toAdministrativeTrace(donation, details, history, custody.read(donation, recordedHistory));
    }

    @Transactional(readOnly = true)
    public PublicDonationSummary findPublicByTrackingCode(String trackingCode) {
        String normalizedCode = trackingCode == null ? "" : trackingCode.trim();
        Donation donation = donations.findByCodigoTrazabilidad(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));

        List<String> resources = donation.getDetalles().stream().map(line -> line.getRecurso().getNombre()).distinct().toList();
        var recordedHistory = movements.findAllByDonacion_IdOrderByFechaHoraAscIdAsc(donation.getId());
        var milestones = publicMilestones(donation, recordedHistory);
        String state = publicState(donation.getEstado(), recordedHistory);
        String emergency = donation.getEmergencia() == null ? null : donation.getEmergencia().getNombre();
        return new PublicDonationSummary(donation.getCodigoTrazabilidad(), donation.getFechaRegistro(), emergency,
                resources, state, publicStateExplanation(state), milestones);
    }

    private static List<PublicTraceMilestone> publicMilestones(Donation donation, List<DonationMovement> movements) {
        var hits = new java.util.ArrayList<PublicTraceMilestone>();
        hits.add(new PublicTraceMilestone("REGISTRADA", "Donación registrada", donation.getFechaRegistro()));
        for (var movement : movements) {
            String type = movement.getTipoEvento();
            if ("RECEPCION_DONACION".equals(type) || "RECEPCION_ACOPIO".equals(type) || "AUTORIZACION_EXCEDENTE".equals(type))
                hits.add(new PublicTraceMilestone("RECIBIDA", "Recepción confirmada", movement.getFechaHora()));
            else if ("SALIDA_TRASLADO".equals(type)) hits.add(new PublicTraceMilestone("EN_TRANSITO", "Traslado en tránsito", movement.getFechaHora()));
            else if ("RECEPCION_DISTRIBUCION".equals(type)) hits.add(new PublicTraceMilestone("RECIBIDA_EN_DESTINO", "Recibida en destino", movement.getFechaHora()));
            else if ("ENTREGA_POBLACION".equals(type)) hits.add(new PublicTraceMilestone("ENTREGADA", "Entrega a la población confirmada", movement.getFechaHora()));
        }
        if ("INCIDENCIA".equals(donation.getEstado()) || movements.stream().anyMatch(m -> "INCIDENCIA".equals(m.getEstadoNuevo())))
            hits.add(new PublicTraceMilestone("EN_VERIFICACION", "La donación está en verificación", null));
        if ("DEVUELTA".equals(donation.getEstado())) hits.add(new PublicTraceMilestone("DEVUELTA", "Donación devuelta", null));
        return hits.stream().collect(Collectors.toMap(h -> h.estado()+"|"+h.fechaHora(), h -> h, (a,b)->a, java.util.LinkedHashMap::new)).values().stream().toList();
    }
    private static String publicState(String state, List<DonationMovement> history) {
        if (history.stream().anyMatch(m -> "ENTREGA_POBLACION".equals(m.getTipoEvento()))) return "ENTREGADA";
        if (history.stream().anyMatch(m -> "RECEPCION_DISTRIBUCION".equals(m.getTipoEvento()))) return "RECIBIDA_EN_DESTINO";
        if (history.stream().anyMatch(m -> "SALIDA_TRASLADO".equals(m.getTipoEvento()))) return "EN_TRANSITO";
        if (history.stream().anyMatch(m -> Set.of("RECEPCION_DONACION", "RECEPCION_ACOPIO", "AUTORIZACION_EXCEDENTE").contains(m.getTipoEvento()))) return "RECIBIDA";
        if ("INCIDENCIA".equals(state)) return "EN_VERIFICACION";
        return switch (state) {
            case "REGISTRADA" -> "REGISTRADA";
            case "EN_TRANSITO" -> "EN_TRANSITO";
            case "ENTREGADA" -> history.stream().anyMatch(m -> "ENTREGA_POBLACION".equals(m.getTipoEvento())) ? "ENTREGADA" : "RECIBIDA_EN_DESTINO";
            case "DEVUELTA" -> "DEVUELTA";
            case "RECIBIDA", "ALMACENADA", "CLASIFICADA" -> "RECIBIDA";
            default -> "EN_VERIFICACION";
        };
    }
    private static String publicStateExplanation(String state) {
        return switch (state) {
            case "REGISTRADA" -> "La donación fue registrada y está pendiente de recepción confirmada.";
            case "RECIBIDA" -> "La recepción de la donación fue confirmada.";
            case "EN_TRANSITO" -> "La donación está en traslado hacia su destino.";
            case "RECIBIDA_EN_DESTINO" -> "La recepción en destino fue confirmada. Esto no significa que ya se entregó a la población.";
            case "ENTREGADA" -> "La entrega a la población fue confirmada.";
            case "DEVUELTA" -> "La donación fue devuelta.";
            default -> "La donación está en verificación.";
        };
    }

    private static List<PublicDonationMovementSummary> publicHistory(List<DonationMovement> recorded,
            pe.redsolidaria.modules.donations.api.dto.CustodyDtos.PublicCustody custody) {
        var result=new java.util.ArrayList<PublicDonationMovementSummary>();
        for (var resource:custody.recursos()) for (var event:resource.movimientos()) {
            String location=event.zona()!=null ? event.zona() : event.destino()!=null ? event.destino().nombre() : null;
            if (event.evento().equals("SALIDA_TRASLADO") && event.origen()!=null && event.destino()!=null)
                location=event.origen().nombre()+" → "+event.destino().nombre();
            result.add(new PublicDonationMovementSummary(null,event.estado(),event.fechaHora(),location,publicDescription(event.evento()),
                resource.recurso(),resource.unidad(),event.cantidadEnviada(),event.cantidadRecibida(),event.origen(),event.destino(),event.zona()));
        }
        for (var movement:recorded) {
            String type=movement.getTipoEvento();
            String eventType="RECEPCION_DONACION".equals(type) || "AUTORIZACION_EXCEDENTE".equals(type) ? "RECEPCION_ACOPIO" : type;
            // The general movement is a summary of the same transactional operation. Keep it only when no detailed
            // evidence exists at that instant, preserving older history without duplicating the new timeline.
            boolean covered=eventType!=null && custody.recursos().stream().flatMap(r -> r.movimientos().stream())
                .anyMatch(e -> eventType.equals(e.evento()) && e.fechaHora()!=null && movement.getFechaHora()!=null
                    && e.fechaHora().withNano(0).equals(movement.getFechaHora().withNano(0)));
            if (!covered) result.add(new PublicDonationMovementSummary(movement.getEstadoAnterior(),movement.getEstadoNuevo(),
                movement.getFechaHora(),"DONACION_PRESENTADA".equals(type)
                    || (type==null && movement.getEstadoAnterior()==null && "REGISTRADA".equals(movement.getEstadoNuevo()))
                    ? "Punto de presentación registrado. Dirección exacta protegida por privacidad" : null,publicDescription(type)));
        }
        result.sort(java.util.Comparator.comparing(PublicDonationMovementSummary::fechaHora,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return result;
    }

    private static String publicDescription(String type) {
        if (type==null) return "Movimiento registrado";
        return switch(type) {
            case "DONACION_PRESENTADA" -> "Donación presentada, pendiente de verificación";
            case "RECEPCION_DONACION" -> "Verificación física confirmada";
            case "RECEPCION_ACOPIO" -> "Recepción confirmada en centro de acopio";
            case "SALIDA_TRASLADO" -> "Traslado al centro de distribución";
            case "RECEPCION_DISTRIBUCION" -> "Recepción en distribución";
            case "ASIGNACION_RECURSOS" -> "Asignación de recursos";
            case "ENTREGA_POBLACION" -> "Entrega a la población";
            case "AUTORIZACION_EXCEDENTE" -> "Recepción adicional autorizada";
            case "RECHAZO_EXCEDENTE" -> "Verificación adicional resuelta";
            default -> "Movimiento registrado";
        };
    }
    private static DonationDetailSummary toDetailSummary(DonationLine line) {
        var resource = line.getRecurso();
        var unit = resource.getUnidadMedida();
        return new DonationDetailSummary(resource.getId(), resource.getNombre(), line.getCantidadDonada(),
                line.getCantidadRecibidaAcopio(), unit.getCodigo(), unit.getNombre(), unit.getSimbolo());
    }

    private static AdministrativeDonationListItem toAdministrativeListItem(Donation donation) {
        DonorSummary donor = new DonorSummary(donation.getDonante().getId(), donation.getDonante().getNombre());
        CollectionCenterSummary center = donation.getCentroAcopio() == null ? null
                : new CollectionCenterSummary(donation.getCentroAcopio().getId(),
                        donation.getCentroAcopio().getNombre(), donation.getCentroAcopio().getUbicacion());
        Long emergencyId = donation.getEmergencia() == null ? null : donation.getEmergencia().getId();
        String emergencyName = donation.getEmergencia() == null ? null : donation.getEmergencia().getNombre();
        return new AdministrativeDonationListItem(donation.getId(), donation.getCodigoTrazabilidad(),
                donation.getFechaRegistro(), donation.getEstado(), donor, emergencyId, emergencyName, center);
    }

    private DonationTraceResponse toAdministrativeTrace(Donation donation,
            List<DonationDetailSummary> details, List<DonationMovementSummary> history,
            pe.redsolidaria.modules.donations.api.dto.CustodyDtos.AdministrativeCustody trace) {
        DonorSummary donor = new DonorSummary(donation.getDonante().getId(), donation.getDonante().getNombre());
        CollectionCenterSummary center = donation.getCentroAcopio() == null ? null
                : new CollectionCenterSummary(donation.getCentroAcopio().getId(),
                        donation.getCentroAcopio().getNombre(), donation.getCentroAcopio().getUbicacion());
        Long emergencyId = donation.getEmergencia() == null ? null : donation.getEmergencia().getId();
        String emergencyName = donation.getEmergencia() == null ? null : donation.getEmergencia().getNombre();
        return new DonationTraceResponse(donation.getId(), donation.getCodigoTrazabilidad(),
                donation.getFechaRegistro(), donation.getFechaIngreso(), donation.getEstado(),
                donation.getObservaciones(), donor, emergencyId, emergencyName, center, details, history,
                trace);
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
