package pe.redsolidaria.modules.needs.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.redsolidaria.modules.emergencies.domain.AffectedZone;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;

@Entity @Table(name = "necesidad")
public class Need {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_necesidad", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_zona_afectada", nullable = false) private AffectedZone zona;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_recurso", nullable = false) private Resource recurso;
    @Column(name = "cantidad_requerida", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadRequerida;
    @Column(nullable = false, length = 30) private String prioridad;
    @Column(nullable = false, length = 30) private String estado;
    @Column(name = "fecha_registro", nullable = false, insertable = false, updatable = false) private LocalDateTime fechaRegistro;
    @Column(columnDefinition = "TEXT") private String observaciones;
    @OneToMany(mappedBy = "necesidad") private List<DeliveryLine> entregas = new ArrayList<>();
    protected Need() {}
}
