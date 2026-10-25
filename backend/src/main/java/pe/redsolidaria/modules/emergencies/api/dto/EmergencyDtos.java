package pe.redsolidaria.modules.emergencies.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class EmergencyDtos {
    private EmergencyDtos() {}

    public record CreateEmergencyRequest(@NotBlank @Size(max = 150) String nombre,
            @NotBlank @Size(max = 100) String tipo, String descripcion,
            @NotNull LocalDate fechaInicio, LocalDate fechaFin,
            @NotBlank @Size(max = 30) String estado) {}

    public record SaveEmergencyRequest(@NotBlank @Size(max = 150) String nombre,
            @NotBlank @Size(max = 100) String tipo, String descripcion,
            @NotNull LocalDate fechaInicio, LocalDate fechaFin) {}

    public record ChangeEmergencyStateRequest(@NotBlank @Size(max = 30) String estado) {}

    public record SaveAffectedZoneRequest(@NotBlank @Size(max = 150) String nombre,
            @Size(max = 200) String ubicacion, String descripcion) {}

    public record EmergencyListItem(Long id, String nombre, String tipo, LocalDate fechaInicio,
            LocalDate fechaFin, String estado) {}

    public record AffectedZoneResponse(Long id, String nombre, String ubicacion, String descripcion,
            LocalDateTime creadoEn) {}

    public record EmergencyDetailResponse(Long id, String nombre, String tipo, String descripcion,
            LocalDate fechaInicio, LocalDate fechaFin, String estado, LocalDateTime creadoEn,
            LocalDateTime actualizadoEn, List<AffectedZoneResponse> zonas) {}

    public record EmergencyPageResponse(List<EmergencyListItem> content, int page, int size,
            long totalElements, int totalPages, boolean last) {}
}
