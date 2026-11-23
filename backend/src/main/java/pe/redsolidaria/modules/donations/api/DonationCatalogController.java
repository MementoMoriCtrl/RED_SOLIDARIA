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
    @GetMapping("/api/admin/catalogos-donaciones/{kind}/{id}")
    public CatalogDetail detail(@PathVariable String kind, @PathVariable long id) { return service.detail(kind, id); }
    @PutMapping("/api/admin/catalogos-donaciones/{kind}/{id}")
    public CatalogDetail update(@PathVariable String kind, @PathVariable long id,
            @Valid @RequestBody CreateCatalogRequest request, Authentication authentication) {
        return service.update(kind, id, authentication.getName(), request);
    }
    @PostMapping("/api/admin/catalogos-donaciones/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse createUnit(
            @Valid @RequestBody pe.redsolidaria.modules.resources.api.dto.ResourceDtos.CreateMeasurementUnitRequest request,
            Authentication authentication) { return service.createUnit(authentication.getName(), request); }
    @PutMapping("/api/admin/catalogos-donaciones/unidades/{id}")
    public pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse updateUnit(@PathVariable long id,
            @Valid @RequestBody pe.redsolidaria.modules.resources.api.dto.ResourceDtos.UpdateMeasurementUnitRequest request,
            Authentication authentication) { return service.updateUnit(id, authentication.getName(), request); }
}
