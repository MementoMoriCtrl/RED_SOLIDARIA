package pe.redsolidaria.modules.access.application;

import static pe.redsolidaria.modules.access.api.dto.AccessDtos.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
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
    private static final Set<String> MASTER_ESSENTIAL_PERMISSIONS = Set.of(ADMIN_ACCESS_PERMISSION,"DONACION_REGISTRAR");
    private static final Set<String> USER_STATES = Set.of("ACTIVO", "INACTIVO", "BLOQUEADO", "SUSPENDIDO");
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecordRepository audit;
    private final JsonMapper json;

    public AccessManagementService(UserRepository users, RoleRepository roles,
                                   PermissionRepository permissions, PasswordEncoder passwordEncoder, AuditRecordRepository audit, JsonMapper json) {
        this.users = users; this.roles = roles; this.permissions = permissions; this.passwordEncoder = passwordEncoder; this.audit = audit; this.json = json;
    }

    public List<UserResponse> users() { return users.findAllByOrderByIdAsc().stream().map(this::userResponse).toList(); }
    public List<RoleResponse> roles() { return roles.findAllByOrderByIdAsc().stream().map(this::roleResponse).toList(); }
    public List<PermissionResponse> permissions() { return permissions.findAllByOrderByIdAsc().stream().map(this::permissionResponse).toList(); }
    public UserResponse getUser(long id) { return userResponse(user(id)); }
    public RoleResponse getRole(long id) { return roleResponse(role(id)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updateUser(long id, UpdateUserRequest request, String actorEmail) {
        users.findMarkedMasterForUpdate();
        var actor = administrator(actorEmail);
        var target = userForUpdate(id);
        if (target.tieneMarcaAdministradorMaestro())
            throw protectedMaster(actor, "Los datos de la cuenta maestra no se editan mediante esta operación");
        requireRoleAccess(actor, roleForUpdate(target.getRol().getId()));
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        if (users.existsByCorreoAndIdNot(correo, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        var changed = new java.util.ArrayList<String>();
        if (!target.getNombre().equals(request.nombre().trim())) changed.add("nombre");
        if (!target.getApellido().equals(request.apellido().trim())) changed.add("apellido");
        if (!target.getCorreo().equals(correo)) changed.add("correo");
        target.actualizarDatos(request.nombre().trim(), request.apellido().trim(), correo);
        var saved = users.save(target);
        audit(actor, "USUARIO", saved.getId(), "EDICION_USUARIO", null, Map.of("camposModificados", changed));
        return userResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleResponse updateRole(long id, UpdateRoleRequest request, String actorEmail) {
        users.findMarkedMasterForUpdate();
        var actor = administrator(actorEmail);
        var target = roleForUpdate(id);
        requireRoleAccess(actor, target);
        var before = new java.util.LinkedHashMap<String, Object>();
        before.put("nombre", target.getNombre()); before.put("descripcion", target.getDescripcion());
        target.actualizarDatos(request.nombre().trim(), request.descripcion());
        var saved = roles.save(target);
        var after = new java.util.LinkedHashMap<String, Object>();
        after.put("nombre", saved.getNombre()); after.put("descripcion", saved.getDescripcion());
        audit(actor, "ROL", saved.getId(), "EDICION_ROL", before, after);
        return roleResponse(saved);
    }

    public AuthenticatedProfileResponse authenticatedProfile(String correo) {
        UserAccount user = users.findByCorreo(correo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Role role = user.getRol();
        Set<String> effectivePermissions = user.permisosEfectivos();
        return new AuthenticatedProfileResponse(user.getNombre(), user.getApellido(), user.getCorreo(),
                role.getCodigo(), role.getNombre(), effectivePermissions);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse createUser(CreateUserRequest request, String actorEmail) {
        users.findMarkedMasterForUpdate();
        UserAccount actor = administrator(actorEmail);
        try {
            PasswordPolicy.validate(request.password());
        } catch (IllegalArgumentException invalidPassword) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidPassword.getMessage());
        }
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        String estado = userState(request.estado());
        if (users.existsByCorreo(correo)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        Role role = roleForUpdate(request.rolId());
        requireRoleAccess(actor, role);
        UserAccount saved = users.save(new UserAccount(role, request.nombre().trim(), request.apellido().trim(),
                correo, passwordEncoder.encode(request.password()), estado));
        audit(actor, "USUARIO", saved.getId(), "CREACION_USUARIO", null, Map.of("estado", saved.getEstado(), "rolId", saved.getRol().getId()));
        return userResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse changeUserState(long id, ChangeUserStateRequest request, String actorEmail) {
        users.findMarkedMasterForUpdate();
        var actor=administrator(actorEmail);
        UserAccount user = userForUpdate(id);
        if (user.tieneMarcaAdministradorMaestro())
            throw protectedMaster(actor,"La cuenta maestra no se modifica mediante esta operación");
        requireRoleAccess(actor, roleForUpdate(user.getRol().getId()));
        String previous = user.getEstado();
        user.cambiarEstado(userState(request.estado()));
        UserAccount saved = users.save(user);
        audit(actor, "USUARIO", saved.getId(), "CAMBIO_ESTADO_USUARIO", Map.of("estado", previous), Map.of("estado", saved.getEstado()));
        return userResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse changeUserRole(long id, ChangeUserRoleRequest request, String actorEmail) {
        users.findMarkedMasterForUpdate();
        var actor=administrator(actorEmail);
        UserAccount user = userForUpdate(id);
        if (user.tieneMarcaAdministradorMaestro())
            throw protectedMaster(actor,"La cuenta maestra no se modifica mediante esta operación");
        long currentId = user.getRol().getId();
        long destinationId = request.rolId();
        // All assignment paths lock roles in ascending ID order after the target account.
        var first = roleForUpdate(Math.min(currentId, destinationId));
        var second = currentId == destinationId ? first : roleForUpdate(Math.max(currentId, destinationId));
        var current = currentId == first.getId() ? first : second;
        var destination = destinationId == first.getId() ? first : second;
        requireRoleAccess(actor, current);
        requireRoleAccess(actor, destination);
        Long previousRole = user.getRol().getId();
        user.cambiarRol(destination);
        UserAccount saved = users.save(user);
        audit(actor, "USUARIO", saved.getId(), "CAMBIO_ROL_USUARIO", Map.of("rolId", previousRole), Map.of("rolId", destination.getId()));
        return userResponse(saved);
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, String actorEmail) {
        UserAccount actor = administrator(actorEmail);
        String codigo = request.codigo().trim();
        if (roles.existsByCodigo(codigo)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de rol ya existe");
        Role saved = roles.save(new Role(codigo, request.nombre().trim(), request.descripcion()));
        audit(actor, "ROL", saved.getId(), "CREACION_ROL", null, Map.of("codigo", saved.getCodigo(), "nombre", saved.getNombre()));
        return roleResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleResponse replaceRolePermissions(long id, ReplaceRolePermissionsRequest request, String actorEmail) {
        var master=users.findMarkedMasterForUpdate();
        var actor=administrator(actorEmail);
        Role role = roleForUpdate(id);
        List<Permission> found = permissions.findAllById(request.permisoIds());
        if (found.size() != request.permisoIds().size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más permisos no existen");
        if (found.stream().anyMatch(p -> UserAccount.MASTER_PERMISSION.equalsIgnoreCase(p.getCodigo().trim()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_MAESTRO es exclusivo de la cuenta y no se asigna a roles");
        }
        var requestedCodes=found.stream().map(Permission::getCodigo).collect(Collectors.toSet());
        if (master.filter(account -> account.getRol().getId().equals(id)).isPresent()
                && !requestedCodes.containsAll(MASTER_ESSENTIAL_PERMISSIONS))
            throw protectedMaster(actor,"El rol utilizado por la cuenta maestra debe conservar ACCESO_ADMINISTRAR y DONACION_REGISTRAR");
        boolean alreadyHasAdminAccess = role.getAsignaciones().stream()
                .anyMatch(assignment -> ADMIN_ACCESS_PERMISSION.equals(assignment.getPermiso().getCodigo()));
        boolean requestGrantsAdminAccess = found.stream()
                .anyMatch(permission -> ADMIN_ACCESS_PERMISSION.equals(permission.getCodigo()));
        if (!PrivilegedRolePolicy.healthyMaster(actor) && (alreadyHasAdminAccess || requestGrantsAdminAccess)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el administrador maestro activo y con permisos esenciales puede administrar roles privilegiados");
        }
        Set<String> previous = Set.copyOf(permissionCodes(role));
        role.reemplazarPermisos(found);
        Role saved = roles.save(role);
        audit(actor, "ROL", saved.getId(), "CAMBIO_PERMISOS_ROL", Map.of("permisos", previous), Map.of("permisos", requestedCodes));
        return roleResponse(saved);
    }

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request, String actorEmail) {
        UserAccount actor = administrator(actorEmail);
        if (UserAccount.MASTER_PERMISSION.equalsIgnoreCase(request.codigo().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permiso reservado al procedimiento controlado");
        }
        String codigo = request.codigo().trim();
        if (permissions.existsByCodigo(codigo)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de permiso ya existe");
        Permission saved = permissions.save(new Permission(codigo, request.descripcion()));
        audit(actor, "PERMISO", saved.getId(), "CREACION_PERMISO", null, Map.of("codigo", saved.getCodigo()));
        return permissionResponse(saved);
    }

    private void audit(UserAccount actor, String entity, Long id, String action, Map<String, ?> before, Map<String, ?> after) {
        var result = new java.util.LinkedHashMap<String, Object>(after);
        result.put("resultado", "EXITO");
        audit.saveAndFlush(new AuditRecord(actor, entity, String.valueOf(java.util.Objects.requireNonNull(id)), action,
                before == null ? null : json.writeValueAsString(before), json.writeValueAsString(result)));
    }

    private static String userState(String value) {
        String state = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!USER_STATES.contains(state)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de usuario no válido");
        return state;
    }
    private static void validateId(long id) { if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador debe ser positivo"); }
    private UserAccount user(long id) { validateId(id); return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")); }
    private UserAccount userForUpdate(long id) { validateId(id); return users.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")); }
    private Role roleForUpdate(long id) { validateId(id); return roles.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado")); }
    private static void requireRoleAccess(UserAccount actor, Role role) {
        if (PrivilegedRolePolicy.privileged(role) && !PrivilegedRolePolicy.healthyMaster(actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador maestro activo y con permisos esenciales puede administrar roles privilegiados");
    }
    private UserAccount administrator(String email) {
        var actor=users.findByCorreo(email.trim().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!"ACTIVO".equalsIgnoreCase(actor.getEstado()) || !actor.permisosEfectivos().contains(ADMIN_ACCESS_PERMISSION))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La operación requiere un administrador activo");
        return actor;
    }
    private static Set<String> permissionCodes(Role role) {
        return role.getAsignaciones().stream().map(a -> a.getPermiso().getCodigo()).collect(Collectors.toSet());
    }
    private static ResponseStatusException protectedMaster(UserAccount actor,String reason) {
        return new ResponseStatusException(PrivilegedRolePolicy.healthyMaster(actor) ? HttpStatus.CONFLICT : HttpStatus.FORBIDDEN,reason);
    }
    private Role role(long id) { validateId(id); return roles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado")); }
    private UserResponse userResponse(UserAccount user) {
        Role role = user.getRol();
        return new UserResponse(user.getId(), user.getNombre(), user.getApellido(), user.getCorreo(), user.getEstado(),
                role.getId(), role.getCodigo(), role.getNombre(), user.getCreadoEn(), user.getActualizadoEn(), user.tieneMarcaAdministradorMaestro());
    }
    private RoleResponse roleResponse(Role role) {
        Set<String> codes = role.getAsignaciones().stream().map(a -> a.getPermiso().getCodigo()).collect(Collectors.toUnmodifiableSet());
        return new RoleResponse(role.getId(), role.getCodigo(), role.getNombre(), role.getDescripcion(), role.getCreadoEn(), codes);
    }
    private PermissionResponse permissionResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCodigo(), permission.getDescripcion(), permission.getCreadoEn());
    }
}
