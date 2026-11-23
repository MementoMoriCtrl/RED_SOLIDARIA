package pe.redsolidaria.modules.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logistica")
@PreAuthorize("hasAuthority('ACCESO_ADMINISTRAR')")
public class TransferResolutionController {
    private final TransferResolutionService service;
    public TransferResolutionController(TransferResolutionService service) { this.service=service; }
    @GetMapping("/resoluciones") public java.util.List<ResolutionDtos.Resolution> history(@RequestParam(defaultValue="0") @Min(0) int page) { return service.history(page); }
    @PostMapping("/traslados/{id}/resolucion") public ResolutionDtos.Resolution resolve(@PathVariable String id,
            @Valid @RequestBody ResolutionDtos.Request request, Authentication actor) { return service.resolve(id,request,actor.getName()); }
}
