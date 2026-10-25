package pe.redsolidaria.modules.donations.application;

import static pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;

@Service
public class DonationReceiptService {
    public static final String AUDIT_ACTION = "RECEPCION_DONACION";
    private final DonationRepository donations;
    private final UserRepository users;
    private final DonationMovementRepository movements;
    private final AuditRecordRepository audit;
    private final EntityManager entityManager;
    private final pe.redsolidaria.modules.donations.persistence.ReceiptOperationRepository operations;
    private final tools.jackson.databind.json.JsonMapper json;

    public DonationReceiptService(DonationRepository donations, UserRepository users,
            DonationMovementRepository movements, AuditRecordRepository audit, EntityManager entityManager,
            pe.redsolidaria.modules.donations.persistence.ReceiptOperationRepository operations,
            tools.jackson.databind.json.JsonMapper json) {
        this.donations = donations;
        this.users = users;
        this.movements = movements;
        this.audit = audit;
        this.entityManager = entityManager;
        this.operations = operations; this.json = json;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RegisterReceiptResponse register(Long donationId, String email, RegisterReceiptRequest request) {
        validateRequest(request);
        // Acquire the parent row lock before any read of the received quantities.
        Donation donation = donations.findByIdForUpdate(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada"));
        String fingerprint = fingerprint(email, request);
        var previous = operations.findByDonacion_IdAndOperacionId(donationId, request.operacionId().toString());
        if (previous.isPresent()) {
            if (!previous.get().getHuella().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La operación ya existe con otro contenido o responsable");
            return json.readValue(previous.get().getRespuesta(), RegisterReceiptResponse.class);
        }
        if (!"REGISTRADA".equals(donation.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La donación ya fue recibida completamente o su estado no admite más recepciones");
        }
        List<DonationLine> lines = donation.getDetalles();
        if (lines.isEmpty() || lines.stream().allMatch(line -> line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada()) >= 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La donación ya está completamente recibida");
        }
        Map<Long, DonationLine> byResource = new HashMap<>();
        lines.forEach(line -> byResource.put(line.getRecurso().getId(), line));
        Set<Long> unique = new HashSet<>();
        List<Map.Entry<DonationLine, BigDecimal>> plan = new ArrayList<>();
        for (ReceiptLineRequest item : request.detalles()) {
            if (item == null || item.recursoId() == null || item.cantidad() == null
                    || !unique.add(item.recursoId()) || invalidQuantity(item.cantidad())) {
                throw badRequest("Los recursos deben ser únicos y las cantidades positivas con hasta 3 decimales");
            }
            DonationLine line = byResource.get(item.recursoId());
            if (line == null) throw badRequest("El recurso indicado no pertenece a esta donación");
            BigDecimal pending = line.getCantidadDonada().subtract(line.getCantidadRecibidaAcopio());
            if (item.cantidad().compareTo(pending) > 0) throw badRequest("La cantidad supera lo pendiente para " + line.getRecurso().getNombre());
            plan.add(Map.entry(line, item.cantidad()));
        }
        UserAccount responsible = users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        String location = clean(request.ubicacion());
        String observations = clean(request.observaciones());
        if (location != null && location.length() > 200) throw badRequest("La ubicación supera 200 caracteres");
        if (observations != null && observations.getBytes(StandardCharsets.UTF_8).length > 65535) throw badRequest("Las observaciones superan el límite permitido");

        List<Map<String, Object>> beforeLines = new ArrayList<>();
        List<Map<String, Object>> receiptLines = new ArrayList<>();
        List<ReceiptLineResult> responseLines = new ArrayList<>();
        for (var entry : plan) {
            DonationLine line = entry.getKey();
            BigDecimal quantity = entry.getValue();
            beforeLines.add(Map.of("recursoId", line.getRecurso().getId(), "cantidadRecibidaAcumulada", line.getCantidadRecibidaAcopio()));
            line.recibir(quantity);
            BigDecimal accumulated = line.getCantidadRecibidaAcopio();
            BigDecimal pending = line.getCantidadDonada().subtract(accumulated);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("detalleDonacionId", line.getId());
            item.put("recursoId", line.getRecurso().getId());
            item.put("recursoNombre", line.getRecurso().getNombre());
            item.put("cantidadRecibidaOperacion", quantity);
            item.put("cantidadRecibidaAcumulada", accumulated);
            receiptLines.add(item);
            responseLines.add(new ReceiptLineResult(line.getRecurso().getId(), line.getRecurso().getNombre(), quantity, accumulated, pending));
        }
        boolean complete = lines.stream().allMatch(line -> line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada()) >= 0);
        donation.registrarIngresoSiFalta();
        String oldState = donation.getEstado();
        if (complete) donation.marcarRecibida();
        String newState = donation.getEstado();
        movements.saveAndFlush(new DonationMovement(donation, responsible, oldState, newState, location, observations));
        Map<String, Object> oldData = Map.of("donacionId", donation.getId(), "estado", oldState, "recursos", beforeLines);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("tipoEvento", AUDIT_ACTION);
        newData.put("donacionId", donation.getId());
        newData.put("recursos", receiptLines);
        newData.put("ubicacion", location);
        newData.put("observaciones", observations);
        newData.put("estadoResultante", newState);
        AuditRecord saved = audit.saveAndFlush(new AuditRecord(responsible, "DONACION",
                String.valueOf(donation.getId()), AUDIT_ACTION, serialize(oldData), serialize(newData)));
        entityManager.refresh(saved);
        var response = new RegisterReceiptResponse(donation.getId(), newState, saved.getFechaHora(), location, observations, responseLines);
        operations.saveAndFlush(new pe.redsolidaria.modules.donations.domain.ReceiptOperation(donation,
                request.operacionId().toString(), fingerprint, json.writeValueAsString(response)));
        return response;
    }

    private String fingerprint(String email, RegisterReceiptRequest request) {
        var canonical = new LinkedHashMap<String, Object>();
        canonical.put("responsable", email.trim().toLowerCase(java.util.Locale.ROOT));
        canonical.put("ubicacion", clean(request.ubicacion()));
        canonical.put("observaciones", clean(request.observaciones()));
        canonical.put("detalles", request.detalles().stream()
                .sorted(java.util.Comparator.comparing(ReceiptLineRequest::recursoId))
                .map(line -> List.of(line.recursoId().toString(), line.cantidad().stripTrailingZeros().toPlainString())).toList());
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String serialize(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return quote(string);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> quote(String.valueOf(entry.getKey())) + ":" + serialize(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            iterable.forEach(item -> items.add(serialize(item)));
            return String.join(",", items).transform(joined -> "[" + joined + "]");
        }
        throw new IllegalArgumentException("Tipo no admitido para auditoría JSON");
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 0x20) result.append(String.format("\\u%04x", (int) c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
    private static boolean invalidQuantity(BigDecimal quantity) {
        return quantity.signum() <= 0 || quantity.scale() > 3 || quantity.precision() - quantity.scale() > 11;
    }
    private static void validateRequest(RegisterReceiptRequest request) {
        if (request == null || request.detalles() == null || request.detalles().isEmpty()) throw badRequest("Indique al menos un recurso recibido");
        if (request.operacionId() == null) throw badRequest("Indique el UUID de la operación para permitir reintentos seguros");
        Set<Long> ids = new HashSet<>();
        for (var line : request.detalles()) {
            if (line == null || line.recursoId() == null || line.recursoId() <= 0 || line.cantidad() == null
                    || invalidQuantity(line.cantidad()) || !ids.add(line.recursoId())) throw badRequest("Revisa recursos y cantidades");
        }
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
