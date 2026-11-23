package pe.redsolidaria.modules.access.application;

import java.util.Set;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;

/** Account capability is separate from privileges inherited from a role. */
public final class PrivilegedRolePolicy {
    public static final String ADMIN_ACCESS = "ACCESO_ADMINISTRAR";
    public static final String DONATION_REGISTRATION = "DONACION_REGISTRAR";
    private PrivilegedRolePolicy() {}

    public static boolean privileged(Role role) {
        return role.getAsignaciones().stream().anyMatch(a -> ADMIN_ACCESS.equals(a.getPermiso().getCodigo()));
    }

    public static boolean healthyMaster(UserAccount actor) {
        return actor.tieneMarcaAdministradorMaestro() && "ACTIVO".equalsIgnoreCase(actor.getEstado())
                && codes(actor.getRol()).containsAll(Set.of(ADMIN_ACCESS, DONATION_REGISTRATION));
    }

    private static Set<String> codes(Role role) {
        return role.getAsignaciones().stream().map(a -> a.getPermiso().getCodigo()).collect(java.util.stream.Collectors.toSet());
    }
}
