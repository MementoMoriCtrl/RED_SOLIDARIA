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
    @PostMapping("/{id}/recepciones")
    public RegisterReceiptResponse receive(@PathVariable Long id, @Valid @RequestBody RegisterReceiptRequest request,
            Authentication authentication) {
        return service.register(id, authentication.getName(), request);
    }
}
