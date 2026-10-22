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

public final class DonationDtos {
    private DonationDtos() {}

    public record DonationLineRequest(
            @NotNull @Positive Long recursoId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {}

    public record CreateDonationRequest(
            @NotNull @Positive Long donanteId,
            @Positive Long emergenciaId,
            @Positive Long centroAcopioId,
            @Size(max = 200) String ubicacion,
            String observaciones,
            @NotEmpty List<@Valid DonationLineRequest> detalles) {}

    public record DonationLineResponse(Long recursoId, String recursoNombre, BigDecimal cantidad) {}
    public record DonationResponse(Long id, String codigoTrazabilidad, LocalDateTime fechaRegistro,
                                   LocalDateTime fechaIngreso, String estado, Long donanteId,
                                   String donanteNombre, Long emergenciaId, Long centroAcopioId,
                                   List<DonationLineResponse> detalles) {}

    public record AdministrativeDonationListItem(Long id, String codigoTrazabilidad,
                                                 LocalDateTime fechaRegistro, String estado,
                                                 DonorSummary donante, Long emergenciaId,
                                                 String emergenciaNombre,
                                                 CollectionCenterSummary centroAcopio) {}

    public record DonorSummary(Long id, String nombre) {}
    public record CollectionCenterSummary(Long id, String nombre, String ubicacion) {}
    public record DonationDetailSummary(Long recursoId, String recursoNombre, BigDecimal cantidadDonada,
                                        BigDecimal cantidadRecibidaAcopio, String unidadCodigo,
                                        String unidadNombre, String unidadSimbolo) {}
    public record DonationMovementSummary(String estadoAnterior, String estadoNuevo, LocalDateTime fechaHora,
                                          String ubicacion, String observaciones, String responsableNombre) {}
    public record DonationTraceResponse(Long id, String codigoTrazabilidad, LocalDateTime fechaRegistro,
                                        LocalDateTime fechaIngreso, String estado, String observaciones,
                                        DonorSummary donante, Long emergenciaId, String emergenciaNombre,
                                        CollectionCenterSummary centroAcopio,
                                        List<DonationDetailSummary> detalles,
                                        List<DonationMovementSummary> movimientos) {}

    public record PublicCollectionCenterSummary(String nombre, String ubicacion) {}
    public record PublicDonationDetailSummary(String recursoNombre, BigDecimal cantidadDonada,
                                              BigDecimal cantidadRecibidaAcopio, String unidadCodigo,
                                              String unidadNombre, String unidadSimbolo) {}
    public record PublicDonationMovementSummary(String estadoAnterior, String estadoNuevo,
                                                LocalDateTime fechaHora, String ubicacion) {}
    public record PublicDonationTraceResponse(String codigoTrazabilidad, LocalDateTime fechaRegistro,
                                             LocalDateTime fechaIngreso, String estado,
                                             PublicCollectionCenterSummary centroAcopio,
                                             List<PublicDonationDetailSummary> detalles,
                                             List<PublicDonationMovementSummary> movimientos) {}
}
