package pe.redsolidaria.modules.needs.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class NeedDtos {
    private NeedDtos() {}
    public record CreateNeedRequest(@NotNull @Positive Long recursoId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal cantidad,
            @NotBlank @Size(max = 30) String prioridad, String observaciones) {}
    public record NeedResponse(Long id, Long zonaId, Long recursoId, String recursoNombre, String unidad,
            String prioridad, String estado, BigDecimal requerida, BigDecimal asignada,
            BigDecimal entregada, BigDecimal pendienteAsignar, BigDecimal pendienteEntregar) {}
    public record NeedPage(List<NeedResponse> content, long totalElements, int totalPages, int page) {}
    public record DonationSource(Long detalleDonacionId, String codigoTrazabilidad, String estado,
            BigDecimal recibidaAcopio, BigDecimal asignadaDonacion, BigDecimal disponible,
            BigDecimal asignadaNecesidad, BigDecimal entregadaNecesidad, boolean habilitada) {}
    public record SourcePage(List<DonationSource> content, long totalElements, int totalPages, int page) {}
}
