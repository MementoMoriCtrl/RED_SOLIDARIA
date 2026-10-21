package pe.redsolidaria.bootstrap;

import java.nio.CharBuffer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.application.PasswordPolicy;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@Service
@Profile("local-bootstrap")
public class BootstrapAdminService {
    public static final String ROLE_CODE = "ADMINISTRADOR";
    public static final String ROLE_NAME = "Administrador";
    public static final String ADMIN_PERMISSION = "ACCESO_ADMINISTRAR";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminService(UserRepository users, RoleRepository roles,
                                 PermissionRepository permissions, PasswordEncoder passwordEncoder) {
        this.users = users; this.roles = roles; this.permissions = permissions; this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean administratorExists() {
        return users.existsAdministratorWithPermission("ACTIVO", ADMIN_PERMISSION);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BootstrapAdminResult createAdministrator(BootstrapAdminInput input) {
        if (administratorExists()) throw new IllegalStateException("Ya existe un administrador activo; bootstrap cancelado");

        String email = required(input.correo(), 150, "usuario/correo").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("El usuario debe ser un correo válido");
        String nombre = required(input.nombre(), 100, "nombre");
        String apellido = required(input.apellido(), 100, "apellido");
        PasswordPolicy.validate(CharBuffer.wrap(input.password()));
        if (users.existsByCorreo(email)) throw new IllegalStateException("El correo ya existe; no se modificó ningún registro");
        if (roles.existsByCodigo(ROLE_CODE)) throw new IllegalStateException("Ya existe el rol ADMINISTRADOR; requiere revisión manual y no será reutilizado");

        Permission permission = permissions.findByCodigo(ADMIN_PERMISSION)
                .orElseGet(() -> permissions.saveAndFlush(new Permission(ADMIN_PERMISSION, "Administración de usuarios, roles y permisos")));
        Role administratorRole = new Role(ROLE_CODE, ROLE_NAME, "Rol inicial creado por bootstrap local");
        administratorRole.asignar(permission);
        administratorRole = roles.saveAndFlush(administratorRole);

        String hash = passwordEncoder.encode(CharBuffer.wrap(input.password()));
        UserAccount account = users.saveAndFlush(new UserAccount(administratorRole, nombre, apellido, email, hash, "ACTIVO"));
        return new BootstrapAdminResult(account.getId());
    }

    private static String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El campo " + label + " es obligatorio");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("El campo " + label + " excede su longitud permitida");
        return normalized;
    }
}
