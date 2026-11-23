package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.redsolidaria.modules.access.api.dto.AccessDtos.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class MasterAccountProtectionServiceTest {
    MasterAccountProtectionFixture f;
    @BeforeEach void setup() { f=new MasterAccountProtectionFixture(); }
    @ParameterizedTest @ValueSource(strings={"INACTIVO","BLOQUEADO","SUSPENDIDO"})
    void ordinaryAdministratorsCannotDisableTheMarkedAccount(String state) {
        var failure=assertThrows(ResponseStatusException.class,() -> f.service.changeUserState(17,
            new ChangeUserStateRequest(state),f.ordinary.getCorreo()));
        assertEquals(403,failure.getStatusCode().value()); assertMasterIntact();
        verify(f.users,never()).save(any());
    }
    @ParameterizedTest @ValueSource(longs={102,103})
    void ordinaryAdministratorsCannotReassignEvenAnEquallyCapableMasterRole(long role) {
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.changeUserRole(17,
            new ChangeUserRoleRequest(role),f.ordinary.getCorreo())).getStatusCode().value());
        assertSame(f.shared,f.master.getRol()); verify(f.users,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"ACCESS","DONATION","BOTH"})
    void sharedRoleCannotLoseEitherEssentialPermission(String remove) {
        Set<Long> ids=remove.equals("ACCESS") ? Set.of(2L) : remove.equals("DONATION") ? Set.of(1L) : Set.of();
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(101,
            new ReplaceRolePermissionsRequest(ids),f.ordinary.getCorreo())).getStatusCode().value());
        assertMasterIntact(); verify(f.roles,never()).save(any());
    }
    @Test void markedAccountRemainsProtectedWhenInactiveOrMissingEffectivePrivileges() {
        f.master.cambiarEstado("INACTIVO");
        assertTrue(f.master.tieneMarcaAdministradorMaestro()); assertFalse(f.master.esAdministradorMaestro());
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.changeUserRole(17,
            new ChangeUserRoleRequest(102L),f.ordinary.getCorreo())).getStatusCode().value());
        f.master.cambiarEstado("ACTIVO"); f.master.cambiarRol(f.unrelated);
        assertFalse(f.master.esAdministradorMaestro());
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(104,
            new ReplaceRolePermissionsRequest(Set.of()),f.ordinary.getCorreo())).getStatusCode().value());
        verify(f.users,never()).save(any()); verify(f.roles,never()).save(any());
    }
    @Test void masterCanManageOtherUsersRolesAndPermissions() {
        f.service.changeUserState(31,new ChangeUserStateRequest("INACTIVO"),f.master.getCorreo());
        f.service.changeUserRole(31,new ChangeUserRoleRequest(103L),f.master.getCorreo());
        f.service.replaceRolePermissions(104,new ReplaceRolePermissionsRequest(Set.of()),f.master.getCorreo());
        var created=f.service.createUser(new CreateUserRequest(104L,"Nuevo","Usuario","new@example.test","test-password-not-production","ACTIVO"),f.master.getCorreo());
        assertEquals("INACTIVO",f.other.getEstado()); assertSame(f.restricted,f.other.getRol());
        assertTrue(f.unrelated.getAsignaciones().isEmpty()); assertEquals(99L,created.id());
        var user=org.mockito.ArgumentCaptor.forClass(pe.redsolidaria.modules.access.domain.UserAccount.class);
        verify(f.users,times(3)).save(user.capture());
        assertFalse(user.getAllValues().getLast().tieneMarcaAdministradorMaestro());
        assertMasterIntact();
    }
    @Test void masterCannotChangeItsOwnRole() {
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> f.service.changeUserRole(17,
            new ChangeUserRoleRequest(102L),f.master.getCorreo())).getStatusCode().value());
        assertMasterIntact(); assertSame(f.shared,f.master.getRol());
    }
    @Test void ordinaryAdministratorsCannotChangePrivilegedSharedRoles() {
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(101,
            new ReplaceRolePermissionsRequest(Set.of(1L,2L,3L)),f.ordinary.getCorreo())).getStatusCode().value());
        assertMasterIntact(); verify(f.roles,never()).save(any());
    }
    @Test void masterCannotAccidentallyRemoveItsOwnEssentialCapabilities() {
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> f.service.changeUserState(17,
            new ChangeUserStateRequest("INACTIVO"),f.master.getCorreo())).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> f.service.changeUserRole(17,
            new ChangeUserRoleRequest(103L),f.master.getCorreo())).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(101,
            new ReplaceRolePermissionsRequest(Set.of(1L)),f.master.getCorreo())).getStatusCode().value());
        assertMasterIntact(); verify(f.users,never()).save(any()); verify(f.roles,never()).save(any());
    }
    @Test void changesAcquireTheCommonMasterLockBeforeResolvingAndChangingAccounts() {
        f.service.changeUserRole(31,new ChangeUserRoleRequest(103L),f.master.getCorreo());
        var order=inOrder(f.users,f.roles);
        order.verify(f.users).findMarkedMasterForUpdate();
        order.verify(f.users).findByCorreo(f.master.getCorreo());
        order.verify(f.users).findByIdForUpdate(31L);
        order.verify(f.roles).findByIdForUpdate(103L);
        order.verify(f.roles).findByIdForUpdate(104L);
        order.verify(f.users).save(f.other);
    }
    @Test void ordinaryCreatesOnlyNonprivilegedUsers() {
        var request = new CreateUserRequest(104L,"Nuevo","Usuario","new@example.test","test-password-not-production","ACTIVO");
        assertNotNull(f.service.createUser(request,f.ordinary.getCorreo()));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> f.service.createUser(
            new CreateUserRequest(101L,"Otro","Usuario","other2@example.test","test-password-not-production","ACTIVO"),f.ordinary.getCorreo())).getStatusCode().value());
    }
    @Test void masterMayCreateAndAssignPrivilegedRolesToOtherAccounts() {
        assertNotNull(f.service.createUser(new CreateUserRequest(101L,"Nuevo","Usuario","new@example.test","test-password-not-production","ACTIVO"),f.master.getCorreo()));
        f.service.changeUserRole(31,new ChangeUserRoleRequest(101L),f.master.getCorreo());
        f.service.changeUserRole(31,new ChangeUserRoleRequest(104L),f.master.getCorreo());
        assertSame(f.unrelated,f.other.getRol());
    }
    @Test void ordinaryCannotPromoteOrModifyPrivilegedAccounts() {
        assertEquals(403,assertThrows(ResponseStatusException.class, () -> f.service.changeUserRole(31,new ChangeUserRoleRequest(101L),f.ordinary.getCorreo())).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class, () -> f.service.changeUserRole(29,new ChangeUserRoleRequest(104L),f.ordinary.getCorreo())).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class, () -> f.service.changeUserState(29,new ChangeUserStateRequest("INACTIVO"),f.ordinary.getCorreo())).getStatusCode().value());
        verify(f.users,never()).save(any());
    }
    @Test void ordinaryMayAssignAnOperationalRole() {
        var operational = new pe.redsolidaria.modules.access.domain.Role("LOGISTICA", "Logística", null);
        org.springframework.test.util.ReflectionTestUtils.setField(operational,"id",105L);
        operational.asignar(f.catalog.get(2L));
        when(f.roles.findByIdForUpdate(105L)).thenReturn(java.util.Optional.of(operational));
        f.service.changeUserRole(31,new ChangeUserRoleRequest(105L),f.ordinary.getCorreo());
        assertSame(operational,f.other.getRol());
        assertFalse(pe.redsolidaria.modules.access.application.PrivilegedRolePolicy.privileged(operational));
    }
    @Test void ordinaryMayManageOperationalPermissionsButNotPrivilegedRoles() {
        f.service.replaceRolePermissions(104,new ReplaceRolePermissionsRequest(Set.of(2L)),f.ordinary.getCorreo());
        assertEquals(Set.of("DONACION_REGISTRAR"),MasterAccountProtectionFixture.codes(f.unrelated));
        assertFalse(pe.redsolidaria.modules.access.application.PrivilegedRolePolicy.privileged(f.unrelated));
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(104,
            new ReplaceRolePermissionsRequest(Set.of(1L,2L)),f.ordinary.getCorreo())).getStatusCode().value());
    }
    @Test void masterMayGrantAndRemoveAdminFromOtherRole() {
        f.service.replaceRolePermissions(104,new ReplaceRolePermissionsRequest(Set.of(1L,3L)),f.master.getCorreo());
        assertTrue(MasterAccountProtectionFixture.codes(f.unrelated).contains("ACCESO_ADMINISTRAR"));
        f.service.replaceRolePermissions(104,new ReplaceRolePermissionsRequest(Set.of(3L)),f.master.getCorreo());
        assertFalse(MasterAccountProtectionFixture.codes(f.unrelated).contains("ACCESO_ADMINISTRAR"));
    }
    @Test void markedAccountWithoutDonationPermissionCannotExerciseMasterAuthority() {
        f.shared.reemplazarPermisos(java.util.List.of(f.catalog.get(1L)));
        assertTrue(f.master.tieneMarcaAdministradorMaestro()); assertFalse(f.master.esAdministradorMaestro());
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> f.service.replaceRolePermissions(104,
            new ReplaceRolePermissionsRequest(Set.of(1L)),f.master.getCorreo())).getStatusCode().value());
    }
    private void assertMasterIntact() {
        assertEquals("ACTIVO",f.master.getEstado()); assertTrue(f.master.tieneMarcaAdministradorMaestro());
        assertTrue(f.master.esAdministradorMaestro());
        assertTrue(f.master.permisosEfectivos().containsAll(Set.of("ACCESO_ADMINISTRAR","DONACION_REGISTRAR","ADMIN_MAESTRO")));
    }
}
