package pe.redsolidaria.modules.donations.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "recepcion_operacion", uniqueConstraints = @UniqueConstraint(
        name = "uk_recepcion_operacion", columnNames = {"id_donacion", "operacion_id"}))
public class ReceiptOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recepcion_operacion", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_donacion", nullable = false)
    private Donation donacion;
    @Column(name = "operacion_id", nullable = false, length = 36, columnDefinition = "CHAR(36)") private String operacionId;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)") private String huella;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private String respuesta;
    protected ReceiptOperation() {}
    public ReceiptOperation(Donation donation, String operationId, String fingerprint, String response) {
        donacion = donation; operacionId = operationId; huella = fingerprint; respuesta = response;
    }
    public String getHuella() { return huella; }
    public String getRespuesta() { return respuesta; }
    public String getOperacionId() { return operacionId; }
}
