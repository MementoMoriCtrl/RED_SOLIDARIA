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
    public Emergency(String nombre, String tipo, String descripcion, LocalDate fechaInicio, LocalDate fechaFin, String estado) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
    }
    public void actualizar(String nombre, String tipo, String descripcion, LocalDate fechaInicio, LocalDate fechaFin) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }
    public void cambiarEstado(String estado) { this.estado = estado; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public String getEstado() { return estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public List<AffectedZone> getZonas() { return zonas; }
}
