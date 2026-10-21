package pe.redsolidaria.modules.emergencies.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.redsolidaria.modules.donations.domain.Donation;

@Entity @Table(name = "emergencia")
public class Emergency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_emergencia", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(nullable = false, length = 100) private String tipo;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "fecha_fin") private LocalDate fechaFin;
    @Column(nullable = false, length = 30) private String estado;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(name = "actualizado_en", insertable = false, updatable = false) private LocalDateTime actualizadoEn;
    @OneToMany(mappedBy = "emergencia") private List<AffectedZone> zonas = new ArrayList<>();
    @OneToMany(mappedBy = "emergencia") private List<Donation> donaciones = new ArrayList<>();
    protected Emergency() {}
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
