package pe.redsolidaria.modules.resources.api;

import static pe.redsolidaria.modules.resources.api.dto.ResourceDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.resources.application.ResourceManagementService;

@RestController
@RequestMapping("/api/recursos")
public class ResourceManagementController {
    private final ResourceManagementService service;

    public ResourceManagementController(ResourceManagementService service) { this.service = service; }

    @GetMapping
    public List<ResourceResponse> resources() { return service.resources(); }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody SaveResourceRequest request) {
        ResourceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/recursos/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResourceResponse update(@PathVariable long id, @Valid @RequestBody SaveResourceRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/estado")
    public ResourceResponse changeState(@PathVariable long id,
                                        @Valid @RequestBody ChangeResourceStateRequest request) {
        return service.changeState(id, request);
    }
}
