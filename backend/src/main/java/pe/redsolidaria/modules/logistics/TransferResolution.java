package pe.redsolidaria.modules.logistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "resolucion_traslado")
public class TransferResolution {
    @Id @Column(name="operacion_id", length=36) private String operacionId;
    @Column(name="traslado_id", nullable=false, unique=true, length=36) private String trasladoId;
    @Column(name="id_usuario", nullable=false, columnDefinition="BIGINT UNSIGNED") private Long actorId;
    @Column(name="fecha_resolucion", nullable=false) private LocalDateTime fecha;
    @Column(nullable=false, length=40) private String tipo;
    @Column(nullable=false, length=2000) private String motivo;
    @Column(length=2000) private String evidencia;
    @Column(name="cantidad_enviada", nullable=false, precision=14, scale=3) private BigDecimal enviada;
    @Column(name="cantidad_recibida", nullable=false, precision=14, scale=3) private BigDecimal recibida;
    @Column(nullable=false, precision=14, scale=3) private BigDecimal diferencia;
    protected TransferResolution() {}
    public TransferResolution(String operation, String transfer, Long actor, LocalDateTime date, String type, String reason,
            String evidence, BigDecimal sent, BigDecimal received) {
        operacionId=operation; trasladoId=transfer; actorId=actor; fecha=date; tipo=type; motivo=reason;
        evidencia=evidence; enviada=sent; recibida=received; diferencia=sent.subtract(received);
    }
    public ResolutionDtos.Resolution dto() { return new ResolutionDtos.Resolution(operacionId,trasladoId,actorId,fecha,tipo,motivo,evidencia,enviada,recibida,diferencia); }
}
