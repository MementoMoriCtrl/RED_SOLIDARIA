package pe.redsolidaria.modules.resources.api;

import static pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.redsolidaria.modules.resources.application.ResourceManagementService;

@RestController
@RequestMapping("/api/unidades-medida")
public class MeasurementUnitController {
    private final ResourceManagementService service;

    public MeasurementUnitController(ResourceManagementService service) { this.service = service; }

    @GetMapping
    public List<MeasurementUnitResponse> measurementUnits() { return service.measurementUnits(); }
}
