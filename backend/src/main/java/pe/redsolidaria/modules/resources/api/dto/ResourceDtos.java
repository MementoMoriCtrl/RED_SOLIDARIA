package pe.redsolidaria.modules.resources.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ResourceDtos {
    private ResourceDtos() {}

    public record MeasurementUnitResponse(Long id, String codigo, String nombre, String simbolo) {}

    public record ResourceResponse(Long id, String nombre, String categoria, String descripcion,
                                   String estado, MeasurementUnitResponse unidadMedida) {}

    public record SaveResourceRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Size(max = 100) String categoria,
            String descripcion,
            @NotNull @Positive Long unidadMedidaId) {}

    public record ChangeResourceStateRequest(
            @NotBlank @Pattern(regexp = "ACTIVO|INACTIVO") String estado) {}
}
