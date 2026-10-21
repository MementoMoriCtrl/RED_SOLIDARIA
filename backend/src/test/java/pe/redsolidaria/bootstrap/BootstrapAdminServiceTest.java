package pe.redsolidaria.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;

    @Test
    void cancelsWhenAnActiveAdministratorAlreadyExistsBeforeAnyWrite() {
        when(users.existsAdministratorWithPermission("ACTIVO", "ACCESO_ADMINISTRAR")).thenReturn(true);
        var service = service(new BCryptPasswordEncoder(4));

        assertThrows(IllegalStateException.class, () -> service.createAdministrator(input("clave-local-segura")));
        verifyNoInteractions(roles, permissions);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void createsDedicatedRoleWithOnlyAdminPermissionAndStoresOnlyBcryptHash() {
        var encoder = new BCryptPasswordEncoder(4);
        when(users.existsAdministratorWithPermission("ACTIVO", "ACCESO_ADMINISTRAR")).thenReturn(false);
        when(users.existsByCorreo("admin@example.test")).thenReturn(false);
        when(roles.existsByCodigo("ADMINISTRADOR")).thenReturn(false);
        when(permissions.findByCodigo("ACCESO_ADMINISTRAR")).thenReturn(Optional.empty());
        when(permissions.saveAndFlush(any(Permission.class))).thenAnswer(call -> call.getArgument(0));
        when(roles.saveAndFlush(any(Role.class))).thenAnswer(call -> call.getArgument(0));
        UserAccount saved = mock(UserAccount.class);
        when(saved.getId()).thenReturn(81L);
        when(users.saveAndFlush(any(UserAccount.class))).thenReturn(saved);
        var service = service(encoder);
        char[] secret = "clave-local-segura".toCharArray();
        var input = new BootstrapAdminInput("admin@example.test", "Ana", "Local", secret);
        assertFalse(input.toString().contains("clave-local-segura"));

        try {
            var result = service.createAdministrator(input);
            assertEquals(81L, result.usuarioId());
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roles).saveAndFlush(roleCaptor.capture());
        Role createdRole = roleCaptor.getValue();
        assertEquals("ADMINISTRADOR", createdRole.getCodigo());
        assertEquals(1, createdRole.getAsignaciones().size());
        assertEquals("ACCESO_ADMINISTRAR", createdRole.getAsignaciones().getFirst().getPermiso().getCodigo());

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(userCaptor.capture());
        UserAccount createdUser = userCaptor.getValue();
        assertNotEquals("clave-local-segura", createdUser.getPasswordHash());
        assertTrue(encoder.matches("clave-local-segura", createdUser.getPasswordHash()));
    }

    @Test
    void refusesToReuseAnExistingAdministratorRole() {
        when(users.existsAdministratorWithPermission("ACTIVO", "ACCESO_ADMINISTRAR")).thenReturn(false);
        when(users.existsByCorreo("admin@example.test")).thenReturn(false);
        when(roles.existsByCodigo("ADMINISTRADOR")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service(new BCryptPasswordEncoder(4))
                .createAdministrator(input("clave-local-segura")));
        verifyNoInteractions(permissions);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPasswordsExceedingBcryptUtf8LimitBeforeWrites() {
        when(users.existsAdministratorWithPermission("ACTIVO", "ACCESO_ADMINISTRAR")).thenReturn(false);
        char[] oversized = "\uD83D\uDD10".repeat(20).toCharArray();
        try {
            assertThrows(IllegalArgumentException.class, () -> service(new BCryptPasswordEncoder(4))
                    .createAdministrator(new BootstrapAdminInput("admin@example.test", "Ana", "Local", oversized)));
            verifyNoInteractions(roles, permissions);
        } finally {
            java.util.Arrays.fill(oversized, '\0');
        }
    }

    private BootstrapAdminService service(BCryptPasswordEncoder encoder) {
        return new BootstrapAdminService(users, roles, permissions, encoder);
    }
    private static BootstrapAdminInput input(String password) {
        return new BootstrapAdminInput("admin@example.test", "Ana", "Local", password.toCharArray());
    }
}
