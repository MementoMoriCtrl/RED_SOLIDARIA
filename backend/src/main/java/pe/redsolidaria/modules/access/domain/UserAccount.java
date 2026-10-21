package pe.redsolidaria.modules.access.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario", columnDefinition = "BIGINT UNSIGNED")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Role rol;
    @Column(nullable = false, length = 100) private String nombre;
    @Column(nullable = false, length = 100) private String apellido;
    @Column(nullable = false, unique = true, length = 150) private String correo;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false, length = 20) private String estado;
    @Column(name = "creado_en", insertable = false, updatable = false) private LocalDateTime creadoEn;
    @Column(name = "actualizado_en", insertable = false, updatable = false) private LocalDateTime actualizadoEn;

    protected UserAccount() {}
    public UserAccount(Role rol, String nombre, String apellido, String correo, String passwordHash, String estado) {
        this.rol = rol; this.nombre = nombre; this.apellido = apellido; this.correo = correo; this.passwordHash = passwordHash; this.estado = estado;
    }
    public void cambiarEstado(String estado) { this.estado = estado; }
    public void cambiarRol(Role rol) { this.rol = rol; }
    public Long getId() { return id; }
    public Role getRol() { return rol; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCorreo() { return correo; }
    public String getPasswordHash() { return passwordHash; }
    public String getEstado() { return estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
