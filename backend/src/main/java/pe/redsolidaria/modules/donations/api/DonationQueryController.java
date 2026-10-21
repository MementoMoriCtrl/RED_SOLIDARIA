package pe.redsolidaria.modules.donations.api;

import static pe.redsolidaria.modules.donations.api.dto.DonationDtos.PublicDonationTraceResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.donations.application.DonationQueryService;

@RestController
@RequestMapping("/api/donaciones")
public class DonationQueryController {
    private final DonationQueryService service;

    public DonationQueryController(DonationQueryService service) { this.service = service; }

    @GetMapping("/{codigoTrazabilidad}")
    public PublicDonationTraceResponse findByTrackingCode(@PathVariable String codigoTrazabilidad) {
        return service.findPublicByTrackingCode(codigoTrazabilidad);
    }
}
