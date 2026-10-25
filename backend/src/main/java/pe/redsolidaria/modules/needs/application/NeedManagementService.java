package pe.redsolidaria.modules.needs.application;

import static pe.redsolidaria.modules.needs.api.dto.NeedDtos.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.emergencies.persistence.AffectedZoneRepository;
import pe.redsolidaria.modules.needs.domain.Need;
import pe.redsolidaria.modules.needs.persistence.NeedRepository;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class NeedManagementService {
    // REGISTRADA may already contain a real partial receipt. Never use cantidadDonada as stock.
    public static final Set<String> USABLE_STATES = Set.of("REGISTRADA", "RECIBIDA", "ALMACENADA", "CLASIFICADA");
    private final NeedRepository needs;
    private final AffectedZoneRepository zones;
    private final ResourceRepository resources;
    private final DonationLineRepository donations;
    private final DeliveryLineRepository lines;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final JsonMapper json;

    public NeedManagementService(NeedRepository needs, AffectedZoneRepository zones, ResourceRepository resources,
            DonationLineRepository donations, DeliveryLineRepository lines, UserRepository users,
            AuditRecordRepository audit, JsonMapper json) {
        this.needs = needs; this.zones = zones; this.resources = resources; this.donations = donations;
        this.lines = lines; this.users = users; this.audit = audit; this.json = json;
    }

    public NeedPage list(long emergencyId, long zoneId, int page) {
        zone(emergencyId, zoneId);
        var result = needs.findAllByZona_IdOrderByIdDesc(zoneId, PageRequest.of(page, 20));
        return new NeedPage(result.getContent().stream().map(this::response).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    @Transactional
    public NeedResponse create(long emergencyId, long zoneId, String email, CreateNeedRequest request) {
        if (request == null || request.recursoId() == null || request.recursoId() <= 0
                || request.cantidad() == null || request.cantidad().signum() <= 0 || request.cantidad().scale() > 3
                || request.cantidad().precision() - request.cantidad().scale() > 11
                || request.prioridad() == null || request.prioridad().isBlank() || request.prioridad().length() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa recurso, cantidad y prioridad");
        var zone = zone(emergencyId, zoneId);
        var resource = resources.findById(request.recursoId()).orElseThrow(() -> missing("Recurso no encontrado"));
        if (!"ACTIVO".equals(resource.getEstado())) throw conflict("El recurso debe estar activo");
        if (request.observaciones() != null && request.observaciones().getBytes(StandardCharsets.UTF_8).length > 65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las observaciones superan el límite permitido");
        var actor = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var need = needs.saveAndFlush(new Need(zone, resource, request.cantidad(), request.prioridad().trim(),
                request.observaciones() == null ? null : request.observaciones().trim()));
        audit.saveAndFlush(new AuditRecord(actor, "NECESIDAD", need.getId().toString(), "CREACION_NECESIDAD", null,
                json.writeValueAsString(Map.of("zonaId", zoneId, "recursoId", resource.getId(),
                        "cantidadRequerida", request.cantidad(), "prioridad", request.prioridad().trim()))));
        return response(need);
    }

    public SourcePage sources(long needId, int page) {
        var need = needs.findById(needId).orElseThrow(() -> missing("Necesidad no encontrada"));
        var result = donations.findSources(need.getZona().getEmergencia().getId(), need.getRecurso().getId(), PageRequest.of(page, 20));
        return new SourcePage(result.getContent().stream().map(line -> {
            var assigned = lines.assignedToDonation(line.getId());
            boolean usable = USABLE_STATES.contains(line.getDonacion().getEstado())
                    && "ACTIVO".equals(need.getRecurso().getEstado()) && "PENDIENTE".equals(need.getEstado());
            return new DonationSource(line.getId(), line.getDonacion().getCodigoTrazabilidad(), line.getDonacion().getEstado(),
                    line.getCantidadRecibidaAcopio(), assigned,
                    usable ? line.getCantidadRecibidaAcopio().subtract(assigned).max(BigDecimal.ZERO) : BigDecimal.ZERO,
                    lines.assignedToPair(needId, line.getId()), lines.deliveredToPair(needId, line.getId()), usable);
        }).toList(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    public NeedResponse response(Need need) {
        var assigned = lines.assignedToNeed(need.getId());
        var delivered = lines.deliveredToNeed(need.getId());
        return new NeedResponse(need.getId(), need.getZona().getId(), need.getRecurso().getId(),
                need.getRecurso().getNombre(), need.getRecurso().getUnidadMedida().getCodigo(), need.getPrioridad(),
                need.getEstado(), need.getCantidadRequerida(), assigned, delivered,
                need.getCantidadRequerida().subtract(assigned).max(BigDecimal.ZERO),
                need.getCantidadRequerida().subtract(delivered).max(BigDecimal.ZERO));
    }

    private pe.redsolidaria.modules.emergencies.domain.AffectedZone zone(long emergencyId, long zoneId) {
        return zones.findByIdAndEmergencia_Id(zoneId, emergencyId).orElseThrow(() -> missing("Zona no encontrada en la emergencia"));
    }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
