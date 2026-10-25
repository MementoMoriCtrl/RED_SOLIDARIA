package pe.redsolidaria.modules.emergencies.api;

import static pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.emergencies.application.EmergencyManagementService;

@RestController
@RequestMapping("/api/admin/emergencias")
public class EmergencyManagementController {
    private final EmergencyManagementService service;

    public EmergencyManagementController(EmergencyManagementService service) { this.service = service; }

    @GetMapping
    public EmergencyPageResponse list(@RequestParam(required = false) @Size(max = 150) String texto,
            @RequestParam(required = false) @Size(max = 30) String estado,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(texto, estado, page, size);
    }

    @GetMapping("/{id}")
    public EmergencyDetailResponse findById(@PathVariable long id) { return service.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyDetailResponse create(@Valid @RequestBody CreateEmergencyRequest request, Authentication authentication) {
        return service.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public EmergencyDetailResponse update(@PathVariable long id, @Valid @RequestBody SaveEmergencyRequest request,
            Authentication authentication) {
        return service.update(id, authentication.getName(), request);
    }

    @PatchMapping("/{id}/estado")
    public EmergencyDetailResponse changeState(@PathVariable long id,
            @Valid @RequestBody ChangeEmergencyStateRequest request, Authentication authentication) {
        return service.changeState(id, authentication.getName(), request);
    }

    @PostMapping("/{id}/zonas")
    @ResponseStatus(HttpStatus.CREATED)
    public AffectedZoneResponse createZone(@PathVariable long id, @Valid @RequestBody SaveAffectedZoneRequest request,
            Authentication authentication) {
        return service.createZone(id, authentication.getName(), request);
    }

    @PutMapping("/{id}/zonas/{zoneId}")
    public AffectedZoneResponse updateZone(@PathVariable long id, @PathVariable long zoneId,
            @Valid @RequestBody SaveAffectedZoneRequest request, Authentication authentication) {
        return service.updateZone(id, zoneId, authentication.getName(), request);
    }
}
