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
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal cantidad,
            @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal examinada,
            @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal noAceptada,
            @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal excedente, Boolean cerrar) {
        public boolean cierre() { return Boolean.TRUE.equals(cerrar); }
        public ReceiptLineRequest(Long recursoId, BigDecimal cantidad) { this(recursoId,cantidad,null,null,null,false); }
    }
    public record RegisterReceiptRequest(@NotEmpty List<@Valid ReceiptLineRequest> detalles,
            @Size(max = 200) String ubicacion, String observaciones, @NotNull java.util.UUID operacionId, @Positive Long centroAcopioId) {
        public RegisterReceiptRequest(List<ReceiptLineRequest> detalles, String ubicacion, String observaciones, java.util.UUID operacionId) {
            this(detalles, ubicacion, observaciones, operacionId, null);
        }
        public RegisterReceiptRequest(List<ReceiptLineRequest> detalles, String ubicacion, String observaciones) {
            this(detalles, ubicacion, observaciones, java.util.UUID.randomUUID(), null);
        }
    }
    public record ReceiptLineResult(Long recursoId, String recursoNombre, BigDecimal cantidadRecibidaOperacion,
            BigDecimal cantidadRecibidaAcumulada, BigDecimal cantidadPendiente,
            BigDecimal examinada, BigDecimal noAceptada, BigDecimal excedente, Boolean cerrada) {
        public ReceiptLineResult(Long recursoId,String recursoNombre,BigDecimal cantidadRecibidaOperacion,
                BigDecimal cantidadRecibidaAcumulada,BigDecimal cantidadPendiente) {
            this(recursoId,recursoNombre,cantidadRecibidaOperacion,cantidadRecibidaAcumulada,cantidadPendiente,null,null,null,false);
        }
    }
    public record RegisterReceiptResponse(Long donacionId, String estado, LocalDateTime fechaHora,
            String ubicacion, String observaciones, List<ReceiptLineResult> detalles,
            Long responsableId, Long centroAcopioId, Long registradorId, Boolean autoverificacionMaestro) {
        public RegisterReceiptResponse(Long donacionId,String estado,LocalDateTime fechaHora,String ubicacion,
                String observaciones,List<ReceiptLineResult> detalles,Long responsableId,Long centroAcopioId) {
            this(donacionId,estado,fechaHora,ubicacion,observaciones,detalles,responsableId,centroAcopioId,null,null);
        }
        public RegisterReceiptResponse(Long donacionId,String estado,LocalDateTime fechaHora,String ubicacion,
                String observaciones,List<ReceiptLineResult> detalles) {
            this(donacionId,estado,fechaHora,ubicacion,observaciones,detalles,null,null);
        }
    }
    public record DecideSurplusRequest(@NotNull java.util.UUID operacionId,@NotNull Boolean aprobar,
            @jakarta.validation.constraints.NotBlank @Size(max=2000) String observaciones) {}
    public record VerificationProgress(Long recursoId,String recursoNombre,String unidad,BigDecimal declarada,
            BigDecimal aceptada,BigDecimal examinada,BigDecimal noAceptada,BigDecimal excedentePendiente,
            BigDecimal excedenteAprobado,boolean cerrada,BigDecimal diferencia,String avance,Long registradorId,boolean puedeVerificar) {}
}
