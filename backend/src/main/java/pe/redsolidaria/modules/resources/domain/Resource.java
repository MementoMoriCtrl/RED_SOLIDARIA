package pe.redsolidaria.modules.resources.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "recurso")
public class Resource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_recurso", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_unidad_medida", nullable = false) private MeasurementUnit unidadMedida;
    @Column(nullable = false, length = 100) private String nombre;
    @Column(nullable = false, length = 100) private String categoria;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @Column(nullable = false, length = 30) private String estado;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(name = "actualizado_en", insertable = false, updatable = false) private LocalDateTime actualizadoEn;
    @OneToMany(mappedBy = "recurso") private List<pe.redsolidaria.modules.donations.domain.DonationLine> donaciones = new ArrayList<>();
    @OneToMany(mappedBy = "recurso") private List<pe.redsolidaria.modules.needs.domain.Need> necesidades = new ArrayList<>();
    protected Resource() {}
    public Resource(MeasurementUnit unidadMedida, String nombre, String categoria, String descripcion, String estado) {
        this.unidadMedida = unidadMedida;
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.estado = estado;
    }
    public void actualizar(MeasurementUnit unidadMedida, String nombre, String categoria, String descripcion) {
        this.unidadMedida = unidadMedida;
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
    }
    public void cambiarEstado(String estado) { this.estado = estado; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }
    public String getDescripcion() { return descripcion; }
    public String getEstado() { return estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public MeasurementUnit getUnidadMedida() { return unidadMedida; }
}
