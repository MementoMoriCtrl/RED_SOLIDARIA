package pe.redsolidaria.modules.donations.api;

import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.AdministrativeDonationListItem;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonationTraceResponse;
import pe.redsolidaria.modules.donations.application.DonationQueryService;

@Validated
@RestController
@RequestMapping("/api/donaciones/administracion")
public class DonationAdministrationController {
    private final DonationQueryService service;

    public DonationAdministrationController(DonationQueryService service) { this.service = service; }

    @GetMapping("/listado")
    public List<AdministrativeDonationListItem> list(
            @RequestParam(required = false) @Size(max = 100) String codigo,
            @RequestParam(required = false) @Size(max = 30) String estado) {
        return service.listAdministrative(codigo, estado);
    }

    @GetMapping("/{id}")
    public DonationTraceResponse findById(@PathVariable Long id) {
        return service.findAdministrativeById(id);
    }
}
