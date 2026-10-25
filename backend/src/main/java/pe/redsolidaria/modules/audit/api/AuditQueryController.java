package pe.redsolidaria.modules.audit.api;

import java.time.LocalDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.audit.api.dto.AuditDtos.AuditEventPageResponse;
import pe.redsolidaria.modules.audit.application.AuditQueryService;

@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditQueryController {
    private final AuditQueryService service;

    public AuditQueryController(AuditQueryService service) { this.service = service; }

    @GetMapping
    public AuditEventPageResponse search(
            @RequestParam(required = false) @Size(max = 50) String accion,
            @RequestParam(required = false) @Size(max = 100) String entidad,
            @RequestParam(required = false) @Size(max = 100) String idRegistro,
            @RequestParam(required = false) @Size(max = 150) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(accion, entidad, idRegistro, usuario, desde, hasta, page, size);
    }
}
