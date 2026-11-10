package pe.redsolidaria.modules.logistics;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class LogisticsDtos {
    private LogisticsDtos() {}
    public record Center(Long id, String nombre, String ubicacion, String descripcion) {}
    public record SaveCenter(@NotBlank @Size(max=150) String nombre, @Size(max=200) String ubicacion,
                             @Size(max=2000) String descripcion) {}
    public record Dispatch(@NotNull UUID operacionId, @Positive long detalleDonacionId,
            @Positive long centroDistribucionId,
            @NotNull @DecimalMin("0.001") @Digits(integer=11, fraction=3) BigDecimal cantidad,
            @Size(max=2000) String observaciones) {}
    public record Receive(@NotNull UUID operacionId,
            @NotNull @DecimalMin("0") @Digits(integer=11, fraction=3) BigDecimal cantidad,
            @Size(max=2000) String observaciones) {}
    public record Transfer(String operacionId, long detalleDonacionId, long centroAcopioId,
            long centroDistribucionId, String origen, String destino, String recurso, String unidad,
            BigDecimal cantidadEnviada, BigDecimal cantidadRecibida, String estado,
            LocalDateTime fechaSalida, LocalDateTime fechaRecepcion, long responsableSalida,
            Long responsableRecepcion, String observaciones, String observacionesRecepcion,
            String operacionRecepcion, String huellaSalida, String huellaRecepcion) {}
    public record Stock(long detalleDonacionId, String codigoTrazabilidad, Long centroAcopioId,
            String centroAcopio, String recurso, String unidad, BigDecimal recibidaAcopio,
            BigDecimal trasladada, BigDecimal recibidaDistribucion, BigDecimal asignada,
            BigDecimal disponibleAcopio, BigDecimal disponibleDistribucion, BigDecimal enTransito,
            BigDecimal diferencia, BigDecimal reservaHistoricaSinDistribucion) {}
}
