package pe.redsolidaria.modules.resources.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "unidad_medida")
public class MeasurementUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_unidad_medida", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @Column(nullable = false, unique = true, length = 30) private String codigo;
    @Column(nullable = false, length = 80) private String nombre;
    @Column(length = 20) private String simbolo;
    @OneToMany(mappedBy = "unidadMedida") private List<Resource> recursos = new ArrayList<>();
    protected MeasurementUnit() {}
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getSimbolo() { return simbolo; }
}
