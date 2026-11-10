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
        UserAccount responsible = users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (donation.getUsuarioRegistro().getId().equals(responsible.getId()) && !responsible.esAdministradorMaestro())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"El registrador no puede verificar su propia donación");
        if (!"REGISTRADA".equals(donation.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La donación ya fue recibida completamente o su estado no admite más recepciones");
        }
        List<DonationLine> lines = donation.getDetalles();
        if (lines.isEmpty() || lines.stream().allMatch(line -> line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada()) >= 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La donación ya está completamente recibida");
        }
        if (donation.getCentroAcopio() == null) {
            if (request.centroAcopioId() == null) throw badRequest("Selecciona el centro de acopio de recepción");
            if (lines.stream().anyMatch(line -> line.getCantidadRecibidaAcopio().signum() > 0))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Las recepciones históricas sin centro requieren conciliación previa");
            var center = entityManager.find(pe.redsolidaria.modules.resources.domain.CollectionCenter.class, request.centroAcopioId());
            if (center == null) throw badRequest("Centro de acopio no encontrado");
            donation.identificarCentroAcopio(center);
        } else if (request.centroAcopioId() != null && !request.centroAcopioId().equals(donation.getCentroAcopio().getId())) {
            throw badRequest("La recepción debe corresponder al centro de acopio de la donación");
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
            if (line.isCerrada() || line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada())>=0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La verificación de este recurso ya está cerrada");
            BigDecimal pending = line.getCantidadDonada().subtract(line.getCantidadRecibidaAcopio());
            if (!expanded(item) && (item.cantidad().signum()==0 || item.cantidad().compareTo(pending)>0))
                throw badRequest("La cantidad supera lo pendiente o no es positiva");
            if (expanded(item)) {
                BigDecimal apta=item.cantidad().add(zero(item.excedente()));
                if (item.cantidad().compareTo(apta.min(pending.max(BigDecimal.ZERO)))!=0)
                    throw badRequest("Separa como excedente la cantidad apta que supera lo declarado pendiente");
                if ((zero(item.noAceptada()).signum()>0 || zero(item.excedente()).signum()>0
                        || (item.cierre() && line.getCantidadRecibidaAcopio().add(item.cantidad()).compareTo(line.getCantidadDonada())!=0))
                        && clean(request.observaciones())==null) throw badRequest("Justifica material no aceptado, excedentes y cierre con diferencia");
                if (invalidQuantity(zero(line.getExaminada()).add(item.examinada()))
                        || invalidQuantity(zero(line.getNoAceptada()).add(zero(item.noAceptada())))
                        || invalidQuantity(zero(line.getExcedentePendiente()).add(zero(item.excedente())))) throw badRequest("Acumulado fuera de DECIMAL(14,3)");
            }
            plan.add(Map.entry(line, item.cantidad()));
        }
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
            var itemRequest=request.detalles().stream().filter(item -> item.recursoId().equals(line.getRecurso().getId())).findFirst().orElseThrow();
            if (expanded(itemRequest)) line.verificar(itemRequest.examinada(),quantity,zero(itemRequest.noAceptada()),zero(itemRequest.excedente()),itemRequest.cierre());
            else line.recibir(quantity);
            BigDecimal accumulated = line.getCantidadRecibidaAcopio();
            BigDecimal pending = line.getCantidadDonada().subtract(accumulated).max(BigDecimal.ZERO);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("detalleDonacionId", line.getId());
            item.put("recursoId", line.getRecurso().getId());
            item.put("recursoNombre", line.getRecurso().getNombre());
            item.put("cantidadRecibidaOperacion", quantity);
            item.put("cantidadRecibidaAcumulada", accumulated);
            item.put("examinada", expanded(itemRequest) ? itemRequest.examinada() : quantity);
            item.put("noAceptada", zero(itemRequest.noAceptada()));
            item.put("excedente", zero(itemRequest.excedente()));
            item.put("cerrar", itemRequest.cierre());
            receiptLines.add(item);
            responseLines.add(new ReceiptLineResult(line.getRecurso().getId(),line.getRecurso().getNombre(),quantity,accumulated,pending,
                    expanded(itemRequest) ? itemRequest.examinada() : quantity,zero(itemRequest.noAceptada()),zero(itemRequest.excedente()),
                    itemRequest.cierre() || accumulated.compareTo(line.getCantidadDonada())>=0));
        }
        boolean complete = lines.stream().allMatch(line -> line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada()) >= 0);
        if (plan.stream().anyMatch(entry -> entry.getValue().signum()>0)) donation.registrarIngresoSiFalta();
        String oldState = donation.getEstado();
        if (complete) donation.marcarRecibida();
        String newState = donation.getEstado();
        movements.saveAndFlush(new DonationMovement(donation, responsible, oldState, newState, location, observations).conTipo("RECEPCION_DONACION"));
        Map<String, Object> oldData = Map.of("donacionId", donation.getId(), "estado", oldState, "recursos", beforeLines);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("centroAcopioId", donation.getCentroAcopio().getId());
        newData.put("tipoEvento", AUDIT_ACTION);
        newData.put("registradorId", donation.getUsuarioRegistro().getId());
        newData.put("verificadorId", responsible.getId());
        newData.put("autoverificacionMaestro", responsible.esAdministradorMaestro()
                && donation.getUsuarioRegistro().getId().equals(responsible.getId()));
        newData.put("donacionId", donation.getId());
        newData.put("recursos", receiptLines);
        newData.put("ubicacion", location);
        newData.put("observaciones", observations);
        newData.put("estadoResultante", newState);
        AuditRecord saved = audit.saveAndFlush(new AuditRecord(responsible, "DONACION",
                String.valueOf(donation.getId()), AUDIT_ACTION, serialize(oldData), serialize(newData)));
        entityManager.refresh(saved);
        for (String event : List.of("MATERIAL_NO_ACEPTADO","SOLICITUD_EXCEDENTE","CIERRE_VERIFICACION")) {
            boolean applies=event.equals("MATERIAL_NO_ACEPTADO") ? request.detalles().stream().anyMatch(i -> zero(i.noAceptada()).signum()>0)
                : event.equals("SOLICITUD_EXCEDENTE") ? request.detalles().stream().anyMatch(i -> zero(i.excedente()).signum()>0)
                : responseLines.stream().anyMatch(i -> Boolean.TRUE.equals(i.cerrada()));
            if (applies) audit.saveAndFlush(new AuditRecord(responsible,"DONACION",donation.getId().toString(),event,null,json.writeValueAsString(request)));
        }
        var response = new RegisterReceiptResponse(donation.getId(), newState, saved.getFechaHora(), location, observations, responseLines,
                responsible.getId(),donation.getCentroAcopio().getId(),donation.getUsuarioRegistro().getId(),
                responsible.esAdministradorMaestro() && donation.getUsuarioRegistro().getId().equals(responsible.getId()));
        operations.saveAndFlush(new pe.redsolidaria.modules.donations.domain.ReceiptOperation(donation,
                request.operacionId().toString(), fingerprint, json.writeValueAsString(response)));
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> history(long donationId, int page) {
        if (!donations.existsById(donationId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Donación no encontrada");
        return operations.findAllByDonacion_IdOrderByIdDesc(donationId, org.springframework.data.domain.PageRequest.of(page,20))
            .getContent().stream().map(operation -> {
                Map<String,Object> result=new LinkedHashMap<>(); result.put("operacionId",operation.getOperacionId());
                result.put("recepcion",json.readValue(operation.getRespuesta(),RegisterReceiptResponse.class));
                result.put("decision",operation.getDecision()); result.put("decisorId",operation.getDecisorId());
                result.put("fechaDecision",operation.getFechaDecision()); result.put("observacionesDecision",operation.getObservacionesDecision());
                return result;
            }).toList();
    }
    private String fingerprint(String email, RegisterReceiptRequest request) {
        var canonical = new LinkedHashMap<String, Object>();
        canonical.put("responsable", email.trim().toLowerCase(java.util.Locale.ROOT));
        canonical.put("ubicacion", clean(request.ubicacion()));
        canonical.put("observaciones", clean(request.observaciones()));
        if (request.centroAcopioId() != null) canonical.put("centroAcopioId", request.centroAcopioId());
        canonical.put("detalles", request.detalles().stream()
                .sorted(java.util.Comparator.comparing(ReceiptLineRequest::recursoId))
                .map(line -> List.of(line.recursoId().toString(), line.cantidad().stripTrailingZeros().toPlainString())).toList());
        if (request.detalles().stream().anyMatch(DonationReceiptService::expanded)) {
            canonical.put("version",4);
            canonical.put("verificacion",request.detalles().stream().sorted(java.util.Comparator.comparing(ReceiptLineRequest::recursoId))
                .map(i -> List.of(i.recursoId().toString(),(i.examinada()==null ? i.cantidad() : i.examinada()).stripTrailingZeros().toPlainString(),
                    zero(i.noAceptada()).stripTrailingZeros().toPlainString(),zero(i.excedente()).stripTrailingZeros().toPlainString(),Boolean.toString(i.cierre()))).toList());
        }
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
        return quantity.signum() < 0 || quantity.scale() > 3 || quantity.precision() - quantity.scale() > 11;
    }
    private static void validateRequest(RegisterReceiptRequest request) {
        if (request == null || request.detalles() == null || request.detalles().isEmpty()) throw badRequest("Indique al menos un recurso recibido");
        if (request.operacionId() == null) throw badRequest("Indique el UUID de la operación para permitir reintentos seguros");
        Set<Long> ids = new HashSet<>();
        for (var line : request.detalles()) {
            if (line == null || line.recursoId() == null || line.recursoId() <= 0 || line.cantidad() == null
                    || invalidQuantity(line.cantidad()) || !ids.add(line.recursoId())) throw badRequest("Revisa recursos y cantidades");
            if (expanded(line)) {
                if (line.examinada()==null || invalidQuantity(line.examinada()) || invalidQuantity(zero(line.noAceptada()))
                        || invalidQuantity(zero(line.excedente())) || line.examinada().compareTo(line.cantidad().add(zero(line.noAceptada())).add(zero(line.excedente())))!=0
                        || (line.examinada().signum()==0 && !line.cierre())) throw badRequest("Examinada debe ser aceptada + no aceptada + excedente; cero solo permite cierre");
            } else if (line.cantidad().signum()==0) throw badRequest("Indica cantidad examinada y cierre para confirmar cero aceptado");
        }
    }
    private static String hashText(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean expanded(ReceiptLineRequest item) { return item.examinada()!=null || item.noAceptada()!=null || item.excedente()!=null || item.cierre(); }
    private static BigDecimal zero(BigDecimal value) { return value==null ? BigDecimal.ZERO : value; }

    @Transactional(readOnly=true)
    public List<VerificationProgress> progress(long id,String email) {
        var donation=donations.findAdministrativeById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var actor=users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        boolean canVerify=actor.esAdministradorMaestro() || !donation.getUsuarioRegistro().getId().equals(actor.getId());
        return donation.getDetalles().stream().map(line -> {
            boolean closed=line.isCerrada() || line.getCantidadRecibidaAcopio().compareTo(line.getCantidadDonada())>=0;
            return new VerificationProgress(line.getRecurso().getId(),line.getRecurso().getNombre(),line.getRecurso().getUnidadMedida().getCodigo(),
                line.getCantidadDonada(),line.getCantidadRecibidaAcopio(),line.getExaminada(),line.getNoAceptada(),line.getExcedentePendiente(),
                line.getExcedenteAprobado(),closed,line.getCantidadRecibidaAcopio().subtract(line.getCantidadDonada()),
                closed ? "CERRADA" : line.getCantidadRecibidaAcopio().signum()>0 || line.getExaminada().signum()>0 ? "PARCIAL" : "PENDIENTE",
                donation.getUsuarioRegistro().getId(),canVerify);
        }).toList();
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ACCESO_ADMINISTRAR')")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RegisterReceiptResponse decide(long donationId,String receiptUuid,String email,DecideSurplusRequest request) {
        if (request==null || request.operacionId()==null || request.aprobar()==null || clean(request.observaciones())==null
                || request.observaciones().length()>2000) throw badRequest("Indica UUID, decisión y justificación");
        var donation=donations.findByIdForUpdate(donationId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var operation=operations.findByDonacion_IdAndOperacionId(donationId,receiptUuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var actor=users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String fingerprint=hashText(json.writeValueAsString(List.of(actor.getId(),request.aprobar(),request.observaciones().trim())));
        if (operation.getDecision()!=null) {
            if (!request.operacionId().toString().equals(operation.getUuidDecision()) || !fingerprint.equals(operation.getHuellaDecision()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Excedente ya resuelto con otra operación");
            return json.readValue(operation.getRespuestaDecision(),RegisterReceiptResponse.class);
        }
        if (!pe.redsolidaria.modules.needs.application.NeedManagementService.USABLE_STATES.contains(donation.getEstado()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Estado de donación no habilitado");
        var original=json.readValue(operation.getRespuesta(),RegisterReceiptResponse.class);
        List<ReceiptLineResult> result=new ArrayList<>();
        for (var item : original.detalles()) if (zero(item.excedente()).signum()>0) {
            var line=donation.getDetalles().stream().filter(l -> l.getRecurso().getId().equals(item.recursoId())).findFirst().orElseThrow();
            if (request.aprobar() && (invalidQuantity(line.getCantidadRecibidaAcopio().add(item.excedente()))
                    || invalidQuantity(line.getExcedenteAprobado().add(item.excedente())))) throw badRequest("Excedente fuera de DECIMAL(14,3)");
            line.decidirExcedente(item.excedente(),request.aprobar());
            result.add(new ReceiptLineResult(item.recursoId(),item.recursoNombre(),request.aprobar() ? item.excedente() : BigDecimal.ZERO,
                line.getCantidadRecibidaAcopio(),line.getCantidadDonada().subtract(line.getCantidadRecibidaAcopio()).max(BigDecimal.ZERO)));
        }
        if (result.isEmpty()) throw badRequest("La operación no tiene excedentes pendientes");
        if (request.aprobar()) donation.registrarIngresoSiFalta();
        String action=request.aprobar() ? "AUTORIZACION_EXCEDENTE" : "RECHAZO_EXCEDENTE";
        var response=new RegisterReceiptResponse(donationId,donation.getEstado(),java.time.LocalDateTime.now(),null,null,result,actor.getId(),donation.getCentroAcopio().getId());
        operation.decidir(request.aprobar(),actor,request.operacionId().toString(),fingerprint,request.observaciones().trim(),json.writeValueAsString(response));
        audit.saveAndFlush(new AuditRecord(actor,"DONACION",Long.toString(donationId),action,null,json.writeValueAsString(Map.of("recepcionId",receiptUuid,"solicitud",request,"resultado",response))));
        movements.saveAndFlush(new DonationMovement(donation,actor,donation.getEstado(),donation.getEstado(),null,request.observaciones()).conTipo(action));
        return response;
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
