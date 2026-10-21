package pe.redsolidaria.modules.access.application;

import static pe.redsolidaria.modules.access.api.dto.AccessDtos.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@Service
@Transactional(readOnly = true)
public class AccessManagementService {
    public static final String ADMIN_ACCESS_PERMISSION = "ACCESO_ADMINISTRAR";
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;

    public AccessManagementService(UserRepository users, RoleRepository roles,
                                   PermissionRepository permissions, PasswordEncoder passwordEncoder) {
        this.users = users; this.roles = roles; this.permissions = permissions; this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> users() { return users.findAllByOrderByIdAsc().stream().map(this::userResponse).toList(); }
    public List<RoleResponse> roles() { return roles.findAllByOrderByIdAsc().stream().map(this::roleResponse).toList(); }
    public List<PermissionResponse> permissions() { return permissions.findAllByOrderByIdAsc().stream().map(this::permissionResponse).toList(); }

    public AuthenticatedProfileResponse authenticatedProfile(String correo) {
        UserAccount user = users.findByCorreo(correo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Role role = user.getRol();
        Set<String> effectivePermissions = role.getAsignaciones().stream()
                .map(assignment -> assignment.getPermiso().getCodigo())
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedProfileResponse(user.getNombre(), user.getApellido(), user.getCorreo(),
                role.getCodigo(), role.getNombre(), effectivePermissions);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        try {
            PasswordPolicy.validate(request.password());
        } catch (IllegalArgumentException invalidPassword) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidPassword.getMessage());
        }
        if (users.existsByCorreo(request.correo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        Role role = role(request.rolId());
        UserAccount saved = users.save(new UserAccount(role, request.nombre().trim(), request.apellido().trim(),
                request.correo().trim().toLowerCase(), passwordEncoder.encode(request.password()), request.estado().trim()));
        return userResponse(saved);
    }

    @Transactional
    public UserResponse changeUserState(long id, ChangeUserStateRequest request) {
        UserAccount user = user(id); user.cambiarEstado(request.estado().trim());
        return userResponse(users.save(user));
    }

    @Transactional
    public UserResponse changeUserRole(long id, ChangeUserRoleRequest request) {
        UserAccount user = user(id); user.cambiarRol(role(request.rolId()));
        return userResponse(users.save(user));
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roles.existsByCodigo(request.codigo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de rol ya existe");
        return roleResponse(roles.save(new Role(request.codigo().trim(), request.nombre().trim(), request.descripcion())));
    }

    @Transactional
    public RoleResponse replaceRolePermissions(long id, ReplaceRolePermissionsRequest request) {
        Role role = role(id);
        List<Permission> found = permissions.findAllById(request.permisoIds());
        if (found.size() != request.permisoIds().size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más permisos no existen");
        boolean alreadyHasAdminAccess = role.getAsignaciones().stream()
                .anyMatch(assignment -> ADMIN_ACCESS_PERMISSION.equals(assignment.getPermiso().getCodigo()));
        boolean requestGrantsAdminAccess = found.stream()
                .anyMatch(permission -> ADMIN_ACCESS_PERMISSION.equals(permission.getCodigo()));
        if (requestGrantsAdminAccess && !alreadyHasAdminAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ACCESO_ADMINISTRAR solo puede asignarse mediante el procedimiento de inicialización controlado");
        }
        role.reemplazarPermisos(found);
        return roleResponse(roles.save(role));
    }

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissions.existsByCodigo(request.codigo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de permiso ya existe");
        return permissionResponse(permissions.save(new Permission(request.codigo().trim(), request.descripcion())));
    }

    private UserAccount user(long id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")); }
    private Role role(long id) { return roles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado")); }
    private UserResponse userResponse(UserAccount user) {
        Role role = user.getRol();
        return new UserResponse(user.getId(), user.getNombre(), user.getApellido(), user.getCorreo(), user.getEstado(),
                role.getId(), role.getCodigo(), role.getNombre(), user.getCreadoEn(), user.getActualizadoEn());
    }
    private RoleResponse roleResponse(Role role) {
        Set<String> codes = role.getAsignaciones().stream().map(a -> a.getPermiso().getCodigo()).collect(Collectors.toUnmodifiableSet());
        return new RoleResponse(role.getId(), role.getCodigo(), role.getNombre(), role.getDescripcion(), role.getCreadoEn(), codes);
    }
    private PermissionResponse permissionResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCodigo(), permission.getDescripcion(), permission.getCreadoEn());
    }
}
