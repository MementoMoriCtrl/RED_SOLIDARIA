package pe.redsolidaria.modules.access.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.access.api.dto.AccessDtos.AuthenticatedProfileResponse;
import pe.redsolidaria.modules.access.application.AccessManagementService;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationValidationController {
    private final AccessManagementService service;
    public AuthenticationValidationController(AccessManagementService service) { this.service = service; }

    @GetMapping("/validate")
    public ResponseEntity<Void> validate() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthenticatedProfileResponse me(Authentication authentication) {
        return service.authenticatedProfile(authentication.getName());
    }
}
