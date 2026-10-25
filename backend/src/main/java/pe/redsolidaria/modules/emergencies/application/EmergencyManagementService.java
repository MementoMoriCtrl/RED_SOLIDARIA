package pe.redsolidaria.modules.emergencies.application;

import static pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.*;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.emergencies.domain.AffectedZone;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.emergencies.persistence.AffectedZoneRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;

@Service
public class EmergencyManagementService {
    private final EmergencyRepository emergencies;
    private final AffectedZoneRepository zones;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final EntityManager entityManager;

    public EmergencyManagementService(EmergencyRepository emergencies, AffectedZoneRepository zones,
            UserRepository users, AuditRecordRepository audit, EntityManager entityManager) {
        this.emergencies = emergencies;
        this.zones = zones;
        this.users = users;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public EmergencyPageResponse list(String text, String state, int page, int size) {
        var results = emergencies.search(clean(text), clean(state), PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"))));
        return new EmergencyPageResponse(results.getContent().stream().map(EmergencyManagementService::toListItem).toList(),
                results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages(), results.isLast());
    }

    @Transactional(readOnly = true)
    public EmergencyDetailResponse findById(long id) {
        return toDetail(requireEmergency(id));
    }

    @Transactional
    public EmergencyDetailResponse create(String email, CreateEmergencyRequest request) {
        validateEmergency(request);
        UserAccount actor = requireActor(email);
        Emergency emergency = emergencies.saveAndFlush(new Emergency(cleanRequired(request.nombre()),
                cleanRequired(request.tipo()), clean(request.descripcion()), request.fechaInicio(), request.fechaFin(),
                cleanRequired(request.estado())));
        entityManager.refresh(emergency);
        record(actor, "EMERGENCIA", emergency.getId(), "CREACION_EMERGENCIA", null, emergencyData(emergency));
        return toDetail(emergency);
    }

    @Transactional
    public EmergencyDetailResponse update(long id, String email, SaveEmergencyRequest request) {
        validateEmergency(request);
        Emergency emergency = requireEmergency(id);
        UserAccount actor = requireActor(email);
        Map<String, Object> before = emergencyData(emergency);
        emergency.actualizar(cleanRequired(request.nombre()), cleanRequired(request.tipo()),
                clean(request.descripcion()), request.fechaInicio(), request.fechaFin());
        emergencies.flush();
        entityManager.refresh(emergency);
        record(actor, "EMERGENCIA", emergency.getId(), "ACTUALIZACION_EMERGENCIA", before, emergencyData(emergency));
        return toDetail(emergency);
    }

    @Transactional
    public EmergencyDetailResponse changeState(long id, String email, ChangeEmergencyStateRequest request) {
        if (request == null || request.estado() == null || request.estado().isBlank() || request.estado().trim().length() > 30) {
            throw badRequest("El estado es obligatorio y admite hasta 30 caracteres");
        }
        Emergency emergency = requireEmergency(id);
        UserAccount actor = requireActor(email);
        Map<String, Object> before = emergencyData(emergency);
        emergency.cambiarEstado(request.estado().trim());
        emergencies.flush();
        entityManager.refresh(emergency);
        record(actor, "EMERGENCIA", emergency.getId(), "CAMBIO_ESTADO_EMERGENCIA", before, emergencyData(emergency));
        return toDetail(emergency);
    }

    @Transactional
    public AffectedZoneResponse createZone(long emergencyId, String email, SaveAffectedZoneRequest request) {
        validateZone(request);
        Emergency emergency = requireEmergency(emergencyId);
        UserAccount actor = requireActor(email);
        AffectedZone zone = zones.saveAndFlush(new AffectedZone(emergency, cleanRequired(request.nombre()),
                clean(request.ubicacion()), clean(request.descripcion())));
        entityManager.refresh(zone);
        record(actor, "ZONA_AFECTADA", zone.getId(), "CREACION_ZONA_AFECTADA", null, zoneData(zone));
        return toZoneResponse(zone);
    }

    @Transactional
    public AffectedZoneResponse updateZone(long emergencyId, long zoneId, String email, SaveAffectedZoneRequest request) {
        validateZone(request);
        requireEmergency(emergencyId);
        AffectedZone zone = zones.findByIdAndEmergencia_Id(zoneId, emergencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona afectada no encontrada"));
        UserAccount actor = requireActor(email);
        Map<String, Object> before = zoneData(zone);
        zone.actualizar(cleanRequired(request.nombre()), clean(request.ubicacion()), clean(request.descripcion()));
        zones.flush();
        entityManager.refresh(zone);
        record(actor, "ZONA_AFECTADA", zone.getId(), "ACTUALIZACION_ZONA_AFECTADA", before, zoneData(zone));
        return toZoneResponse(zone);
    }

    private Emergency requireEmergency(long id) {
        if (id <= 0) throw badRequest("El identificador de emergencia debe ser positivo");
        return emergencies.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergencia no encontrada"));
    }

    private UserAccount requireActor(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado");
        return users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private void record(UserAccount actor, String entity, Long id, String action,
            Map<String, Object> before, Map<String, Object> after) {
        audit.saveAndFlush(new AuditRecord(actor, entity, String.valueOf(id), action,
                json(before), json(after)));
    }

    private String json(Object value) {
        if (value == null) return null;
        if (value instanceof String string) return quote(string);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof java.time.temporal.TemporalAccessor) return quote(value.toString());
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + json(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> iterable) {
            java.util.List<String> items = new java.util.ArrayList<>();
            iterable.forEach(item -> items.add(json(item)));
            return String.join(",", items).transform(itemsJson -> "[" + itemsJson + "]");
        }
        throw new IllegalArgumentException("Tipo no admitido para auditoría JSON");
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (character < 0x20) result.append(String.format("\\u%04x", (int) character)); else result.append(character); }
            }
        }
        return result.append('"').toString();
    }

    private static void validateEmergency(SaveEmergencyRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.nombre().trim().length() > 150 || request.tipo() == null || request.tipo().isBlank()
                || request.tipo().trim().length() > 100 || request.fechaInicio() == null) {
            throw badRequest("Nombre, tipo y fecha de inicio son obligatorios y deben respetar sus límites");
        }
        if (request.fechaFin() != null && request.fechaFin().isBefore(request.fechaInicio())) {
            throw badRequest("La fecha de fin debe ser igual o posterior a la fecha de inicio");
        }
        validateText(request.descripcion(), "La descripción supera el límite de la tabla");
    }

    private static void validateEmergency(CreateEmergencyRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.nombre().trim().length() > 150 || request.tipo() == null || request.tipo().isBlank()
                || request.tipo().trim().length() > 100 || request.fechaInicio() == null
                || request.estado() == null || request.estado().isBlank() || request.estado().trim().length() > 30) {
            throw badRequest("Nombre, tipo, fecha de inicio y estado son obligatorios y deben respetar sus límites");
        }
        if (request.fechaFin() != null && request.fechaFin().isBefore(request.fechaInicio())) {
            throw badRequest("La fecha de fin debe ser igual o posterior a la fecha de inicio");
        }
        validateText(request.descripcion(), "La descripción supera el límite de la tabla");
    }

    private static void validateZone(SaveAffectedZoneRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.nombre().trim().length() > 150
                || (request.ubicacion() != null && request.ubicacion().trim().length() > 200)) {
            throw badRequest("El nombre de la zona es obligatorio; revisa también el límite de ubicación");
        }
        validateText(request.descripcion(), "La descripción supera el límite de la tabla");
    }

    private static void validateText(String value, String message) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > 65535) throw badRequest(message);
    }

    private static Map<String, Object> emergencyData(Emergency emergency) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nombre", emergency.getNombre()); data.put("tipo", emergency.getTipo());
        data.put("descripcion", emergency.getDescripcion()); data.put("fechaInicio", emergency.getFechaInicio());
        data.put("fechaFin", emergency.getFechaFin()); data.put("estado", emergency.getEstado());
        return data;
    }

    private static Map<String, Object> zoneData(AffectedZone zone) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emergenciaId", zone.getEmergencia().getId()); data.put("nombre", zone.getNombre());
        data.put("ubicacion", zone.getUbicacion()); data.put("descripcion", zone.getDescripcion());
        return data;
    }

    private static EmergencyListItem toListItem(Emergency emergency) {
        return new EmergencyListItem(emergency.getId(), emergency.getNombre(), emergency.getTipo(),
                emergency.getFechaInicio(), emergency.getFechaFin(), emergency.getEstado());
    }

    private EmergencyDetailResponse toDetail(Emergency emergency) {
        var affectedZones = zones.findAllByEmergencia_IdOrderByIdAsc(emergency.getId()).stream()
                .map(EmergencyManagementService::toZoneResponse).toList();
        return new EmergencyDetailResponse(emergency.getId(), emergency.getNombre(), emergency.getTipo(),
                emergency.getDescripcion(), emergency.getFechaInicio(), emergency.getFechaFin(), emergency.getEstado(),
                emergency.getCreadoEn(), emergency.getActualizadoEn(), affectedZones);
    }

    private static AffectedZoneResponse toZoneResponse(AffectedZone zone) {
        return new AffectedZoneResponse(zone.getId(), zone.getNombre(), zone.getUbicacion(), zone.getDescripcion(), zone.getCreadoEn());
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String cleanRequired(String value) { return value.trim(); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
