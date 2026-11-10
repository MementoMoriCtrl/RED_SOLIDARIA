package pe.redsolidaria.modules.donations.api;

import static pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;

@RestController
@RequestMapping("/api/donaciones/administracion")
public class DonationReceiptController {
    private final DonationReceiptService service;
    public DonationReceiptController(DonationReceiptService service) { this.service = service; }
    @org.springframework.web.bind.annotation.GetMapping("/{id}/recepciones")
    public java.util.List<java.util.Map<String,Object>> history(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page) {
        return service.history(id,page);
    }
    @org.springframework.web.bind.annotation.GetMapping("/{id}/verificacion")
    public java.util.List<VerificationProgress> progress(@PathVariable long id,Authentication actor) { return service.progress(id,actor.getName()); }
    @PostMapping("/{id}/recepciones/{uuid}/excedente")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ACCESO_ADMINISTRAR')")
    public RegisterReceiptResponse decide(@PathVariable long id,@PathVariable String uuid,
            @Valid @RequestBody DecideSurplusRequest request,Authentication actor) { return service.decide(id,uuid,actor.getName(),request); }
    @PostMapping("/{id}/recepciones")
    public RegisterReceiptResponse receive(@PathVariable Long id, @Valid @RequestBody RegisterReceiptRequest request,
            Authentication authentication) {
        return service.register(id, authentication.getName(), request);
    }
}
