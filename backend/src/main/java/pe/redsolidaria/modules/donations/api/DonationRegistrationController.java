package pe.redsolidaria.modules.donations.api;

import static pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;

@RestController
@RequestMapping("/api/donaciones")
public class DonationRegistrationController {
    private final DonationRegistrationService service;

    public DonationRegistrationController(DonationRegistrationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DonationResponse> register(@Valid @RequestBody CreateDonationRequest request,
                                                       Authentication authentication) {
        DonationResponse created = service.register(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/donaciones/" + created.codigoTrazabilidad())).body(created);
    }
}
