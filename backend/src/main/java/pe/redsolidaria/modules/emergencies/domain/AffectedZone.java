package pe.redsolidaria.modules.emergencies.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "zona_afectada")
public class AffectedZone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_zona_afectada", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_emergencia", nullable = false) private Emergency emergencia;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(length = 200) private String ubicacion;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @OneToMany(mappedBy = "zona") private List<pe.redsolidaria.modules.needs.domain.Need> necesidades = new ArrayList<>();
    protected AffectedZone() {}
}
