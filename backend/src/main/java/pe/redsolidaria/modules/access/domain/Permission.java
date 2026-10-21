package pe.redsolidaria.modules.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "permiso")
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso", columnDefinition = "BIGINT UNSIGNED")
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String codigo;
    @Column(length = 255)
    private String descripcion;
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
    @OneToMany(mappedBy = "permiso")
    private List<RolePermission> asignaciones = new ArrayList<>();

    protected Permission() {}
    public Permission(String codigo, String descripcion) { this.codigo = codigo; this.descripcion = descripcion; }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
