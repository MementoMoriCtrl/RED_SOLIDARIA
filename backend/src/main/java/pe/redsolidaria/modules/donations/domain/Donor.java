package pe.redsolidaria.modules.donations.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "donante")
public class Donor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_donante", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @OneToMany(mappedBy = "donante") private List<Donation> donaciones = new ArrayList<>();
    protected Donor() {}
    public Donor(String nombre) { this.nombre = nombre; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
