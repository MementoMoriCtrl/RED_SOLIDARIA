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
    @Mock pe.redsolidaria.modules.audit.persistence.AuditRecordRepository audit;
    final tools.jackson.databind.json.JsonMapper json = new tools.jackson.databind.json.JsonMapper();
    private AccessManagementService service(org.springframework.security.crypto.password.PasswordEncoder encoder) { return new AccessManagementService(users, roles, permissions, encoder, audit, json); }
    private void mockActor() {
        var r = new Role("ADMIN", "Admin", null); r.asignar(new Permission("ACCESO_ADMINISTRAR", null));
        lenient().when(users.findByCorreo("actor@example.test")).thenReturn(java.util.Optional.of(new UserAccount(r, "Actor", "Test", "actor@example.test", "unused", "ACTIVO")));
    }

    @Test void normalizesEmailBeforeCheckingDuplicates() {
        mockActor();
        var service = service(new BCryptPasswordEncoder(4));
        when(users.existsByCorreo("ana@example.test")).thenReturn(true);
        var failure = assertThrows(ResponseStatusException.class, () -> service.createUser(
            new CreateUserRequest(1L, "Ana", "Prueba", " ANA@EXAMPLE.TEST ", "password-de-prueba", "activo"), "actor@example.test"));
        assertEquals(409, failure.getStatusCode().value());
        verify(users, never()).save(any());
    }

    @Test void rejectsUnknownStatesBeforeCreatingUser() {
        mockActor();
        var service = service(new BCryptPasswordEncoder(4));
        var failure = assertThrows(ResponseStatusException.class, () -> service.createUser(
            new CreateUserRequest(1L, "Ana", "Prueba", "ana@example.test", "password-de-prueba", "DESCONOCIDO"), "actor@example.test"));
        assertEquals(400, failure.getStatusCode().value());
        verify(users, never()).save(any());
    }

    @Test void individualQueriesValidateIdsAndReturnNotFound() {
        var service = service(new BCryptPasswordEncoder(4));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getUser(0)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getUser(99)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getRole(-1)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getRole(99)).getStatusCode().value());
    }

    @Test void auditFailurePropagatesFromTransactionalCreation() throws Exception {
        mockActor();
        var role = new Role("OPERADOR", "Operador", null);
        org.springframework.test.util.ReflectionTestUtils.setField(role, "id", 7L);
        when(roles.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(role));
        when(users.save(any(UserAccount.class))).thenAnswer(call -> {
            UserAccount account = call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(account, "id", 15L);
            return account;
        });
        var failure = new IllegalStateException("audit unavailable");
        when(audit.saveAndFlush(any())).thenThrow(failure);
        var service = service(new BCryptPasswordEncoder(4));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.createUser(
            new CreateUserRequest(7L, "Ana", "Prueba", "ana@example.test", "password-de-prueba", "ACTIVO"),
            "actor@example.test")));
        assertNotNull(AccessManagementService.class.getMethod("createUser", CreateUserRequest.class, String.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void authenticatedProfileReturnsRoleAndEffectivePermissionsWithoutSensitiveFields() {
        var role = new Role("ADMINISTRADOR", "Administrador", null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR", "Administrar acceso"));
        role.asignar(new Permission("DONACION_REGISTRAR", "Registrar donaciones"));
        var account = new UserAccount(role, "Ana", "Pérez", "ana@example.test", "hash-no-debe-exponerse", "ACTIVO");
        when(users.findByCorreo("ana@example.test")).thenReturn(java.util.Optional.of(account));
        var service = service(new BCryptPasswordEncoder(4));

        var profile = service.authenticatedProfile("ana@example.test");

        assertEquals("ana@example.test", profile.correo());
        assertEquals("ADMINISTRADOR", profile.rolCodigo());
        assertEquals(java.util.Set.of("ACCESO_ADMINISTRAR", "DONACION_REGISTRAR"), profile.permisos());
        assertFalse(profile.toString().contains("hash-no-debe-exponerse"));
    }

    @Test
    void createUserStoresOnlyBcryptHashAndResponseDoesNotContainPassword() {
        mockActor();
        var encoder = new BCryptPasswordEncoder(4);
        var service = service(encoder);
        var role = new Role("OPERADOR", "Operador", null);
        org.springframework.test.util.ReflectionTestUtils.setField(role, "id", 7L);
        when(roles.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(role));
        when(users.existsByCorreo("operador@example.test")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> { UserAccount a = invocation.getArgument(0); org.springframework.test.util.ReflectionTestUtils.setField(a, "id", 1L); return a; });

        var response = service.createUser(new CreateUserRequest(7L, "Ana", "Pérez",
                "operador@example.test", "una-clave-segura-de-prueba", "ACTIVO"), "actor@example.test");

        var captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(captor.capture());
        assertNotEquals("una-clave-segura-de-prueba", captor.getValue().getPasswordHash());
        assertTrue(encoder.matches("una-clave-segura-de-prueba", captor.getValue().getPasswordHash()));
        assertFalse(response.toString().contains("una-clave-segura-de-prueba"));
    }

    @Test
    void normalRolePermissionUpdateCannotGrantAdministrativeAccess() {
        var actorRole=new Role("ADMINISTRADOR","Administrador",null);
        actorRole.asignar(new Permission("ACCESO_ADMINISTRAR",null));
        when(users.findByCorreo("actor@example.test")).thenReturn(java.util.Optional.of(
            new UserAccount(actorRole,"Actor","Prueba","actor@example.test","unused","ACTIVO")));
        var role = mock(Role.class);
        var adminPermission = mock(Permission.class);
        when(roles.findByIdForUpdate(9L)).thenReturn(java.util.Optional.of(role));
        when(role.getAsignaciones()).thenReturn(java.util.List.of());
        when(adminPermission.getCodigo()).thenReturn("ACCESO_ADMINISTRAR");
        when(permissions.findAllById(java.util.Set.of(4L))).thenReturn(java.util.List.of(adminPermission));
        var service = service(new BCryptPasswordEncoder(4));

        var failure = assertThrows(ResponseStatusException.class,
                () -> service.replaceRolePermissions(9L,
                        new pe.redsolidaria.modules.access.api.dto.AccessDtos.ReplaceRolePermissionsRequest(java.util.Set.of(4L)),"actor@example.test"));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(role, never()).reemplazarPermisos(any());
    }
    @Test void reservedMasterPermissionCannotBeCreatedOrAssignedToAnyRole() {
        mockActor();
        var service = service(new BCryptPasswordEncoder(4));
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> service.createPermission(
            new pe.redsolidaria.modules.access.api.dto.AccessDtos.CreatePermissionRequest("ADMIN_MAESTRO",null), "actor@example.test")).getStatusCode().value());
        var role = new Role("ADMINISTRADOR","Administrador",null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR",null));
        when(users.findByCorreo("actor@example.test")).thenReturn(java.util.Optional.of(
            new UserAccount(role,"Actor","Prueba","actor@example.test","unused","ACTIVO")));
        when(roles.findByIdForUpdate(4L)).thenReturn(java.util.Optional.of(role));
        when(permissions.findAllById(java.util.Set.of(5L))).thenReturn(java.util.List.of(new Permission("ADMIN_MAESTRO",null)));
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> service.replaceRolePermissions(4L,
            new pe.redsolidaria.modules.access.api.dto.AccessDtos.ReplaceRolePermissionsRequest(java.util.Set.of(5L)),"actor@example.test")).getStatusCode().value());
        verify(roles,never()).save(any());
    }
}
