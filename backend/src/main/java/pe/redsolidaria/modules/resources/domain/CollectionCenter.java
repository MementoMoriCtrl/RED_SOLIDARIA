package pe.redsolidaria.modules.resources.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "centro_acopio")
public class CollectionCenter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_centro_acopio", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(length = 200) private String ubicacion;
    @Column(columnDefinition = "TEXT") private String descripcion;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @OneToMany(mappedBy = "centroAcopio") private List<pe.redsolidaria.modules.donations.domain.Donation> donaciones = new ArrayList<>();
    protected CollectionCenter() {}
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
}
