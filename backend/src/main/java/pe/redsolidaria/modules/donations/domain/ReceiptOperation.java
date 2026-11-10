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
    @Column(length=20) private String decision;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario_decision") private pe.redsolidaria.modules.access.domain.UserAccount decisor;
    @Column(name="fecha_decision") private java.time.LocalDateTime fechaDecision;
    @Column(name="observaciones_decision",length=2000) private String observacionesDecision;
    @Column(name="uuid_decision",length=36,columnDefinition="CHAR(36)") private String uuidDecision;
    @Column(name="huella_decision",length=64,columnDefinition="CHAR(64)") private String huellaDecision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="respuesta_decision",columnDefinition="json") private String respuestaDecision;
    public String getDecision() { return decision; }
    public String getUuidDecision() { return uuidDecision; }
    public String getHuellaDecision() { return huellaDecision; }
    public String getRespuestaDecision() { return respuestaDecision; }
    public Long getDecisorId() { return decisor==null ? null : decisor.getId(); }
    public java.time.LocalDateTime getFechaDecision() { return fechaDecision; }
    public String getObservacionesDecision() { return observacionesDecision; }
    public void decidir(boolean aprobar,pe.redsolidaria.modules.access.domain.UserAccount actor,String uuid,String huella,String observaciones,String respuesta) {
        if (decision!=null) throw new IllegalStateException("Decisión ya registrada");
        decision=aprobar ? "APROBADO" : "RECHAZADO"; decisor=actor; fechaDecision=java.time.LocalDateTime.now();
        uuidDecision=uuid; huellaDecision=huella; observacionesDecision=observaciones; respuestaDecision=respuesta;
    }
    protected ReceiptOperation() {}
    public ReceiptOperation(Donation donation, String operationId, String fingerprint, String response) {
        donacion = donation; operacionId = operationId; huella = fingerprint; respuesta = response;
    }
    public String getHuella() { return huella; }
    public String getRespuesta() { return respuesta; }
    public String getOperacionId() { return operacionId; }
}
