package pe.redsolidaria.modules.deliveries.application;

import static pe.redsolidaria.modules.deliveries.api.dto.DeliveryDtos.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.deliveries.domain.Delivery;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryRepository;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.needs.persistence.NeedRepository;
import pe.redsolidaria.modules.needs.application.NeedManagementService;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DeliveryManagementService {
    private final NeedRepository needs;
    private final DonationRepository donations;
    private final DonationLineRepository sources;
    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository lines;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final EntityManager entityManager;
    private final JsonMapper json;

    public DeliveryManagementService(NeedRepository needs, DonationRepository donations, DonationLineRepository sources,
            DeliveryRepository deliveries, DeliveryLineRepository lines, UserRepository users,
            AuditRecordRepository audit, EntityManager entityManager, JsonMapper json) {
        this.needs = needs; this.donations = donations; this.sources = sources; this.deliveries = deliveries;
        this.lines = lines; this.users = users; this.audit = audit; this.entityManager = entityManager; this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeliveryResponse save(long needId, long sourceId, String email, SaveDeliveryRequest request) {
        if (request == null || invalid(request.totalAsignado()) || invalid(request.totalEntregado())
                || request.totalEntregado().compareTo(request.totalAsignado()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los totales: entregado no puede superar asignado");
        var source = sources.findById(sourceId).orElseThrow(() -> missing("Detalle de donación no encontrado"));
        // Same parent lock as DonationReceiptService; then serialize competing allocations on the need.
        var donation = donations.findByIdForUpdate(source.getDonacion().getId())
                .orElseThrow(() -> missing("Donación no encontrada"));
        var need = needs.findByIdForUpdate(needId).orElseThrow(() -> missing("Necesidad no encontrada"));
        entityManager.refresh(source);
        if (!source.getRecurso().getId().equals(need.getRecurso().getId()) || donation.getEmergencia() == null
                || !donation.getEmergencia().getId().equals(need.getZona().getEmergencia().getId()))
            throw conflict("La donación debe corresponder al mismo recurso y emergencia de la necesidad");
        var actor = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var assigned = lines.assignedToPair(needId, sourceId);
        var delivered = lines.deliveredToPair(needId, sourceId);
        var assignDelta = request.totalAsignado().subtract(assigned);
        var deliverDelta = request.totalEntregado().subtract(delivered);
        if (assignDelta.signum() == 0 && deliverDelta.signum() == 0)
            return new DeliveryResponse(null, assigned, delivered, true);
        if (assignDelta.signum() < 0 || deliverDelta.signum() < 0)
            throw conflict("Los totales ya avanzaron; recarga los datos. No se permiten disminuciones");
        if (!NeedManagementService.USABLE_STATES.contains(donation.getEstado())
                || !"ACTIVO".equals(need.getRecurso().getEstado()) || !"PENDIENTE".equals(need.getEstado()))
            throw conflict("El estado actual de la donación, recurso o necesidad no admite esta operación");
        if (lines.assignedToDonation(sourceId).add(assignDelta).compareTo(source.getCantidadRecibidaAcopio()) > 0)
            throw conflict("La asignación supera lo realmente recibido y disponible en acopio");
        if (lines.assignedToNeed(needId).add(assignDelta).compareTo(need.getCantidadRequerida()) > 0)
            throw conflict("La asignación supera la cantidad requerida por la necesidad");
        var delivery = deliveries.saveAndFlush(new Delivery(actor));
        lines.saveAndFlush(new DeliveryLine(delivery, source, need, assignDelta, deliverDelta));
        audit.saveAndFlush(new AuditRecord(actor, "ENTREGA", delivery.getId().toString(), "ASIGNACION_ENTREGA",
                json.writeValueAsString(Map.of("necesidadId", needId, "detalleDonacionId", sourceId,
                        "totalAsignado", assigned, "totalEntregado", delivered)),
                json.writeValueAsString(Map.of("necesidadId", needId, "detalleDonacionId", sourceId,
                        "totalAsignado", request.totalAsignado(), "totalEntregado", request.totalEntregado(),
                        "incrementoAsignado", assignDelta, "incrementoEntregado", deliverDelta))));
        return new DeliveryResponse(delivery.getId(), request.totalAsignado(), request.totalEntregado(), false);
    }

    private static boolean invalid(BigDecimal quantity) {
        return quantity == null || quantity.signum() < 0 || quantity.scale() > 3 || quantity.precision() - quantity.scale() > 11;
    }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
