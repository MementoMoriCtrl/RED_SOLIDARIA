package pe.redsolidaria.modules.access.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "rol_permiso")
public class RolePermission {
    @EmbeddedId
    private RolePermissionId id;
    @MapsId("rolId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Role rol;
    @MapsId("permisoId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_permiso", nullable = false)
    private Permission permiso;
    protected RolePermission() {}
    RolePermission(Role rol, Permission permiso) { this.rol = rol; this.permiso = permiso; this.id = new RolePermissionId(rol.getId(), permiso.getId()); }
    public Role getRol() { return rol; }
    public Permission getPermiso() { return permiso; }
}
