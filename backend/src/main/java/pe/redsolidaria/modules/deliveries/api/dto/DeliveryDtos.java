package pe.redsolidaria.modules.deliveries.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class DeliveryDtos {
    private DeliveryDtos() {}
    // Absolute cumulative totals for this donation-line/need pair, never operation increments.
    public record SaveDeliveryRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal totalAsignado,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal totalEntregado) {}
    public record DeliveryResponse(Long entregaId, BigDecimal totalAsignado, BigDecimal totalEntregado, boolean repetida) {}
}
