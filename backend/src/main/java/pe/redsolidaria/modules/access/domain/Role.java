package pe.redsolidaria.modules.access.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rol")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol", columnDefinition = "BIGINT UNSIGNED")
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(length = 255)
    private String descripcion;
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;
    @OneToMany(mappedBy = "rol")
    private List<UserAccount> usuarios = new ArrayList<>();
    @OneToMany(mappedBy = "rol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RolePermission> asignaciones = new ArrayList<>();

    protected Role() {}
    public Role(String codigo, String nombre, String descripcion) { this.codigo = codigo; this.nombre = nombre; this.descripcion = descripcion; }
    public void asignar(Permission permiso) { asignaciones.add(new RolePermission(this, permiso)); }
    public void reemplazarPermisos(List<Permission> permisos) { asignaciones.clear(); permisos.forEach(this::asignar); }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public List<RolePermission> getAsignaciones() { return asignaciones; }
}
