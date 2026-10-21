package pe.redsolidaria.modules.resources.application;

import static pe.redsolidaria.modules.resources.api.dto.ResourceDtos.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.resources.persistence.MeasurementUnitRepository;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;

@Service
@Transactional(readOnly = true)
public class ResourceManagementService {
    public static final String ACTIVE = "ACTIVO";
    public static final String INACTIVE = "INACTIVO";
    private static final int MAX_TEXT_BYTES = 65_535;

    private final ResourceRepository resources;
    private final MeasurementUnitRepository units;

    public ResourceManagementService(ResourceRepository resources, MeasurementUnitRepository units) {
        this.resources = resources;
        this.units = units;
    }

    public List<ResourceResponse> resources() {
        return resources.findAllByOrderByNombreAsc().stream().map(this::resourceResponse).toList();
    }

    public List<MeasurementUnitResponse> measurementUnits() {
        return units.findAllByOrderByNombreAsc().stream().map(this::unitResponse).toList();
    }

    @Transactional
    public ResourceResponse create(SaveResourceRequest request) {
        validateDescription(request.descripcion());
        MeasurementUnit unit = unit(request.unidadMedidaId());
        Resource created = resources.save(new Resource(unit, clean(request.nombre()), clean(request.categoria()),
                clean(request.descripcion()), ACTIVE));
        return resourceResponse(created);
    }

    @Transactional
    public ResourceResponse update(long id, SaveResourceRequest request) {
        validateDescription(request.descripcion());
        Resource resource = resource(id);
        resource.actualizar(unit(request.unidadMedidaId()), clean(request.nombre()), clean(request.categoria()),
                clean(request.descripcion()));
        return resourceResponse(resources.save(resource));
    }

    @Transactional
    public ResourceResponse changeState(long id, ChangeResourceStateRequest request) {
        Resource resource = resource(id);
        resource.cambiarEstado(request.estado());
        return resourceResponse(resources.save(resource));
    }

    private Resource resource(long id) {
        return resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Recurso no encontrado"));
    }

    private MeasurementUnit unit(long id) {
        return units.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Unidad de medida no encontrada"));
    }

    private ResourceResponse resourceResponse(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getNombre(), resource.getCategoria(),
                resource.getDescripcion(), resource.getEstado(), unitResponse(resource.getUnidadMedida()));
    }

    private MeasurementUnitResponse unitResponse(MeasurementUnit unit) {
        return new MeasurementUnitResponse(unit.getId(), unit.getCodigo(), unit.getNombre(), unit.getSimbolo());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateDescription(String description) {
        if (description != null && description.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La descripción supera la capacidad de la columna TEXT");
        }
    }
}
