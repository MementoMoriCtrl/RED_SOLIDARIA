package pe.redsolidaria.modules.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.resources.api.dto.ResourceDtos.ChangeResourceStateRequest;
import pe.redsolidaria.modules.resources.api.dto.ResourceDtos.SaveResourceRequest;
import pe.redsolidaria.modules.resources.application.ResourceManagementService;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.resources.persistence.MeasurementUnitRepository;
import pe.redsolidaria.modules.resources.persistence.ResourceRepository;

@ExtendWith(MockitoExtension.class)
class ResourceManagementServiceTest {
    @Mock ResourceRepository resources;
    @Mock MeasurementUnitRepository units;
    @Mock MeasurementUnit unit;
    ResourceManagementService service;

    @BeforeEach
    void setUp() { service = new ResourceManagementService(resources, units); }

    @Test
    void createsAnActiveResourceUsingAnExistingMeasurementUnit() {
        when(unit.getId()).thenReturn(7L);
        when(unit.getCodigo()).thenReturn("KG");
        when(unit.getNombre()).thenReturn("Kilogramo");
        when(unit.getSimbolo()).thenReturn("kg");
        when(units.findById(7L)).thenReturn(Optional.of(unit));
        when(resources.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new SaveResourceRequest(" Arroz ", " Alimentos ", " Grano ", 7L));

        assertEquals("Arroz", response.nombre());
        assertEquals("Alimentos", response.categoria());
        assertEquals("Grano", response.descripcion());
        assertEquals("ACTIVO", response.estado());
        assertEquals("Kilogramo", response.unidadMedida().nombre());
        verify(resources).save(argThat(resource -> resource.getUnidadMedida() == unit));
    }

    @Test
    void updatesFieldsAndChangesStateWithoutReplacingTheResource() {
        MeasurementUnit otherUnit = mock(MeasurementUnit.class);
        Resource resource = new Resource(unit, "Agua", "Bebidas", null, "ACTIVO");
        when(resources.findById(12L)).thenReturn(Optional.of(resource));
        when(units.findById(8L)).thenReturn(Optional.of(otherUnit));
        when(resources.save(resource)).thenReturn(resource);

        var updated = service.update(12L, new SaveResourceRequest("Agua embotellada", "Agua", null, 8L));
        assertEquals("Agua embotellada", updated.nombre());
        assertEquals("Agua", updated.categoria());
        assertSame(otherUnit, resource.getUnidadMedida());

        var inactive = service.changeState(12L, new ChangeResourceStateRequest("INACTIVO"));
        assertEquals("INACTIVO", inactive.estado());
        verify(resources, times(2)).save(resource);
    }

    @Test
    void returnsNotFoundWhenTheMeasurementUnitDoesNotExist() {
        when(units.findById(404L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new SaveResourceRequest("Agua", "Bebidas", null, 404L)));
        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(resources);
    }

    @Test
    void rejectsDescriptionsThatExceedTheMySqlTextColumnCapacity() {
        String description = "a".repeat(65_536);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new SaveResourceRequest("Agua", "Bebidas", description, 7L)));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(units, resources);
    }
}
