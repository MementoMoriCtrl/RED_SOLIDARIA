package pe.redsolidaria.modules.donations.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DonationReceiptDtos {
    private DonationReceiptDtos() {}
    public record ReceiptLineRequest(@NotNull @Positive Long recursoId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {}
    public record RegisterReceiptRequest(@NotEmpty List<@Valid ReceiptLineRequest> detalles,
            @Size(max = 200) String ubicacion, String observaciones, @NotNull java.util.UUID operacionId) {
        public RegisterReceiptRequest(List<ReceiptLineRequest> detalles, String ubicacion, String observaciones) {
            this(detalles, ubicacion, observaciones, java.util.UUID.randomUUID());
        }
    }
    public record ReceiptLineResult(Long recursoId, String recursoNombre, BigDecimal cantidadRecibidaOperacion,
            BigDecimal cantidadRecibidaAcumulada, BigDecimal cantidadPendiente) {}
    public record RegisterReceiptResponse(Long donacionId, String estado, LocalDateTime fechaHora,
            String ubicacion, String observaciones, List<ReceiptLineResult> detalles) {}
}
