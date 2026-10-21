package pe.redsolidaria.modules.access.api;

import static pe.redsolidaria.modules.access.api.dto.AccessDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.redsolidaria.modules.access.application.AccessManagementService;

@RestController
@RequestMapping("/api/admin")
public class AccessManagementController {
    private final AccessManagementService service;
    public AccessManagementController(AccessManagementService service) { this.service = service; }

    @GetMapping("/usuarios") public List<UserResponse> users() { return service.users(); }
    @PostMapping("/usuarios") public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = service.createUser(request);
        return ResponseEntity.created(URI.create("/api/admin/usuarios/" + created.id())).body(created);
    }
    @PatchMapping("/usuarios/{id}/estado") public UserResponse changeState(@PathVariable long id, @Valid @RequestBody ChangeUserStateRequest request) { return service.changeUserState(id, request); }
    @PutMapping("/usuarios/{id}/rol") public UserResponse changeRole(@PathVariable long id, @Valid @RequestBody ChangeUserRoleRequest request) { return service.changeUserRole(id, request); }

    @GetMapping("/roles") public List<RoleResponse> roles() { return service.roles(); }
    @PostMapping("/roles") public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse created = service.createRole(request);
        return ResponseEntity.created(URI.create("/api/admin/roles/" + created.id())).body(created);
    }
    @PutMapping("/roles/{id}/permisos") public RoleResponse replacePermissions(@PathVariable long id, @Valid @RequestBody ReplaceRolePermissionsRequest request) { return service.replaceRolePermissions(id, request); }

    @GetMapping("/permisos") public List<PermissionResponse> permissions() { return service.permissions(); }
    @PostMapping("/permisos") public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse created = service.createPermission(request);
        return ResponseEntity.created(URI.create("/api/admin/permisos/" + created.id())).body(created);
    }
}
