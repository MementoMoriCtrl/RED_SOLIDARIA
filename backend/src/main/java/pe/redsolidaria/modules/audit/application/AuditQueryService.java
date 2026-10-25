package pe.redsolidaria.modules.audit.application;

import static pe.redsolidaria.modules.audit.api.dto.AuditDtos.*;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;

@Service
public class AuditQueryService {
    private final AuditRecordRepository records;

    public AuditQueryService(AuditRecordRepository records) { this.records = records; }

    @Transactional(readOnly = true)
    public AuditEventPageResponse search(String action, String entity, String recordId, String user,
            LocalDateTime from, LocalDateTime to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior o igual a la fecha final");
        }
        PageRequest request = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
        Page<AuditRecord> result = records.search(clean(action), clean(entity), clean(recordId),
                clean(user), from, to, request);
        return new AuditEventPageResponse(result.getContent().stream().map(AuditQueryService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    private static AuditEventResponse toResponse(AuditRecord record) {
        var user = record.getUsuario();
        String name = user == null ? null : Stream.of(user.getNombre(), user.getApellido())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" "));
        return new AuditEventResponse(record.getId(), record.getFechaHora(), record.getAccion(),
                record.getEntidad(), record.getIdRegistro(), name);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
