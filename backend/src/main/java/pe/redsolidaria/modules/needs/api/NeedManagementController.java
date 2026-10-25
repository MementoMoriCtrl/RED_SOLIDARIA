package pe.redsolidaria.modules.needs.api;

import static pe.redsolidaria.modules.needs.api.dto.NeedDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.redsolidaria.modules.needs.application.NeedManagementService;

@RestController
@RequestMapping("/api/admin")
public class NeedManagementController {
    private final NeedManagementService service;
    public NeedManagementController(NeedManagementService service) { this.service = service; }
    @GetMapping("/emergencias/{emergencyId}/zonas/{zoneId}/necesidades")
    public NeedPage list(@PathVariable long emergencyId, @PathVariable long zoneId,
            @RequestParam(defaultValue = "0") @Min(0) int page) {
        return service.list(emergencyId, zoneId, page);
    }
    @PostMapping("/emergencias/{emergencyId}/zonas/{zoneId}/necesidades")
    @ResponseStatus(HttpStatus.CREATED)
    public NeedResponse create(@PathVariable long emergencyId, @PathVariable long zoneId,
            @Valid @RequestBody CreateNeedRequest request, Authentication authentication) {
        return service.create(emergencyId, zoneId, authentication.getName(), request);
    }
    @GetMapping("/necesidades/{needId}/donaciones")
    public SourcePage sources(@PathVariable long needId, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return service.sources(needId, page);
    }
}
