package pe.redsolidaria.modules.audit.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.redsolidaria.modules.access.domain.UserAccount;

@Entity @Table(name = "registro_auditoria")
public class AuditRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_registro_auditoria", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario", nullable = false) private UserAccount usuario;
    @Column(nullable = false, length = 100) private String entidad;
    @Column(name = "id_registro", nullable = false, length = 100) private String idRegistro;
    @Column(nullable = false, length = 50) private String accion;
    @Column(name = "fecha_hora", nullable = false, insertable = false, updatable = false) private LocalDateTime fechaHora;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "datos_anteriores", columnDefinition = "json") private String datosAnteriores;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "datos_nuevos", columnDefinition = "json") private String datosNuevos;
    protected AuditRecord() {}
    public AuditRecord(UserAccount usuario, String entidad, String idRegistro, String accion,
                       String datosAnteriores, String datosNuevos) {
        this.usuario = java.util.Objects.requireNonNull(usuario);
        this.entidad = java.util.Objects.requireNonNull(entidad);
        this.idRegistro = java.util.Objects.requireNonNull(idRegistro);
        this.accion = java.util.Objects.requireNonNull(accion);
        this.datosAnteriores = datosAnteriores;
        this.datosNuevos = datosNuevos;
    }
    public Long getId() { return id; }
    public UserAccount getUsuario() { return usuario; }
    public String getEntidad() { return entidad; }
    public String getIdRegistro() { return idRegistro; }
    public String getAccion() { return accion; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getDatosAnteriores() { return datosAnteriores; }
    public String getDatosNuevos() { return datosNuevos; }
}
