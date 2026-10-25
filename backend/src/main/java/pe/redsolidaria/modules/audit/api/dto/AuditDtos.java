package pe.redsolidaria.modules.audit.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditEventResponse(Long id, LocalDateTime fechaHora, String accion, String entidad,
            String idRegistro, String responsableNombre) {}

    public record AuditEventPageResponse(List<AuditEventResponse> content, int page, int size,
            long totalElements, int totalPages, boolean last) {}
}
