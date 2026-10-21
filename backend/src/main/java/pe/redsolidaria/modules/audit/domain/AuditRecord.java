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
}
