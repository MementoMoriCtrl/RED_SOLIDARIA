package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.redsolidaria.modules.access.api.dto.AccessDtos.CreateUserRequest;
import pe.redsolidaria.modules.access.application.AccessManagementService;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccessManagementServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;

    @Test
    void createUserStoresOnlyBcryptHashAndResponseDoesNotContainPassword() {
        var encoder = new BCryptPasswordEncoder(4);
        var service = new AccessManagementService(users, roles, permissions, encoder);
        var role = new Role("OPERADOR", "Operador", null);
        when(roles.findById(7L)).thenReturn(java.util.Optional.of(role));
        when(users.existsByCorreo("operador@example.test")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createUser(new CreateUserRequest(7L, "Ana", "Pérez",
                "operador@example.test", "una-clave-segura-de-prueba", "ACTIVO"));

        var captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(captor.capture());
        assertNotEquals("una-clave-segura-de-prueba", captor.getValue().getPasswordHash());
        assertTrue(encoder.matches("una-clave-segura-de-prueba", captor.getValue().getPasswordHash()));
        assertFalse(response.toString().contains("una-clave-segura-de-prueba"));
    }

    @Test
    void normalRolePermissionUpdateCannotGrantAdministrativeAccess() {
        var role = mock(Role.class);
        var adminPermission = mock(Permission.class);
        when(roles.findById(9L)).thenReturn(java.util.Optional.of(role));
        when(role.getAsignaciones()).thenReturn(java.util.List.of());
        when(adminPermission.getCodigo()).thenReturn("ACCESO_ADMINISTRAR");
        when(permissions.findAllById(java.util.Set.of(4L))).thenReturn(java.util.List.of(adminPermission));
        var service = new AccessManagementService(users, roles, permissions, new BCryptPasswordEncoder(4));

        var failure = assertThrows(ResponseStatusException.class,
                () -> service.replaceRolePermissions(9L,
                        new pe.redsolidaria.modules.access.api.dto.AccessDtos.ReplaceRolePermissionsRequest(java.util.Set.of(4L))));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(role, never()).reemplazarPermisos(any());
    }
}
