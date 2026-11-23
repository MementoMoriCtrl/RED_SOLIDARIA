package pe.redsolidaria.modules.access.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.CacheControl;
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
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/me")
    public AuthenticatedProfileResponse me(Authentication authentication) {
        return service.authenticatedProfile(authentication.getName());
    }
}
