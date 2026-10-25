package pe.redsolidaria.modules.deliveries.api;

import static pe.redsolidaria.modules.deliveries.api.dto.DeliveryDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.redsolidaria.modules.deliveries.application.DeliveryManagementService;

@RestController
@RequestMapping("/api/admin/necesidades/{needId}/donaciones/{sourceId}/entrega")
public class DeliveryManagementController {
    private final DeliveryManagementService service;
    public DeliveryManagementController(DeliveryManagementService service) { this.service = service; }
    @PutMapping
    public DeliveryResponse save(@PathVariable long needId, @PathVariable long sourceId,
            @Valid @RequestBody SaveDeliveryRequest request, Authentication authentication) {
        return service.save(needId, sourceId, authentication.getName(), request);
    }
}
