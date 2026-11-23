package pe.redsolidaria.modules.access.api;

import static pe.redsolidaria.modules.access.api.dto.AccessDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import pe.redsolidaria.modules.access.application.AccessManagementService;

@RestController
@RequestMapping("/api/admin")
public class AccessManagementController {
    private final AccessManagementService service;
    public AccessManagementController(AccessManagementService service) { this.service = service; }

    @GetMapping("/usuarios") public List<UserResponse> users() { return service.users(); }
    @GetMapping("/usuarios/{id}") public UserResponse user(@PathVariable long id) { return service.getUser(id); }
    @PutMapping("/usuarios/{id}") public UserResponse updateUser(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request, Authentication actor) { return service.updateUser(id, request, actor.getName()); }
    @PostMapping("/usuarios") public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request, Authentication actor) {
        UserResponse created = service.createUser(request, actor.getName());
        return ResponseEntity.created(URI.create("/api/admin/usuarios/" + created.id())).body(created);
    }
    @PatchMapping("/usuarios/{id}/estado") public UserResponse changeState(@PathVariable long id, @Valid @RequestBody ChangeUserStateRequest request, Authentication actor) { return service.changeUserState(id, request, actor.getName()); }
    @PutMapping("/usuarios/{id}/rol") public UserResponse changeRole(@PathVariable long id, @Valid @RequestBody ChangeUserRoleRequest request, Authentication actor) { return service.changeUserRole(id, request, actor.getName()); }

    @GetMapping("/roles") public List<RoleResponse> roles() { return service.roles(); }
    @GetMapping("/roles/{id}") public RoleResponse role(@PathVariable long id) { return service.getRole(id); }
    @PutMapping("/roles/{id}") public RoleResponse updateRole(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest request, Authentication actor) { return service.updateRole(id, request, actor.getName()); }
    @PostMapping("/roles") public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request, Authentication actor) {
        RoleResponse created = service.createRole(request, actor.getName());
        return ResponseEntity.created(URI.create("/api/admin/roles/" + created.id())).body(created);
    }
    @PutMapping("/roles/{id}/permisos") public RoleResponse replacePermissions(@PathVariable long id, @Valid @RequestBody ReplaceRolePermissionsRequest request, Authentication actor) { return service.replaceRolePermissions(id, request, actor.getName()); }

    @GetMapping("/permisos") public List<PermissionResponse> permissions() { return service.permissions(); }
    @PostMapping("/permisos") public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request, Authentication actor) {
        PermissionResponse created = service.createPermission(request, actor.getName());
        return ResponseEntity.created(URI.create("/api/admin/permisos/" + created.id())).body(created);
    }
}
