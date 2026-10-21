package pe.redsolidaria.modules.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RolePermissionId implements Serializable {
    @Column(name = "id_rol", columnDefinition = "BIGINT UNSIGNED")
    private Long rolId;
    @Column(name = "id_permiso", columnDefinition = "BIGINT UNSIGNED")
    private Long permisoId;
    protected RolePermissionId() {}
    RolePermissionId(Long rolId, Long permisoId) { this.rolId = rolId; this.permisoId = permisoId; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof RolePermissionId that)) return false; return Objects.equals(rolId, that.rolId) && Objects.equals(permisoId, that.permisoId); }
    @Override public int hashCode() { return Objects.hash(rolId, permisoId); }
}
