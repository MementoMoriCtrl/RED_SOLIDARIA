package pe.redsolidaria.modules.donations.api;

import static pe.redsolidaria.modules.donations.api.dto.DonationCatalogDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.redsolidaria.modules.donations.application.DonationCatalogService;

@RestController
public class DonationCatalogController {
    private final DonationCatalogService service;
    public DonationCatalogController(DonationCatalogService service) { this.service = service; }
    @GetMapping("/api/donaciones/catalogos/{kind}")
    @PreAuthorize("hasAnyAuthority('DONACION_REGISTRAR', 'ACCESO_ADMINISTRAR')")
    public CatalogPage list(@PathVariable String kind, @RequestParam(defaultValue = "") @Size(max = 150) String texto,
            @RequestParam(defaultValue = "0") @Min(0) int page) { return service.list(kind, texto, page); }
    @PostMapping("/api/admin/catalogos-donaciones/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItem create(@PathVariable String kind, @Valid @RequestBody CreateCatalogRequest request, Authentication authentication) {
        return service.create(kind, authentication.getName(), request);
    }
    @PostMapping("/api/admin/catalogos-donaciones/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse createUnit(
            @Valid @RequestBody pe.redsolidaria.modules.resources.api.dto.ResourceDtos.CreateMeasurementUnitRequest request,
            Authentication authentication) { return service.createUnit(authentication.getName(), request); }
}
