package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.redsolidaria.test.SessionWebTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.api.AccessManagementController;
import pe.redsolidaria.modules.access.application.AccessManagementService;
import pe.redsolidaria.modules.access.persistence.*;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;

/** HTTP integration with the real service and security filters; no datasource or migrations. */
@WebMvcTest(AccessManagementController.class)
@Import({SecurityConfiguration.class,AccessManagementService.class,MasterAccountProtectionWebTest.Dependencies.class})
class MasterAccountProtectionWebTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired AuditRecordRepository audit;
    @Autowired PasswordEncoder encoder;
    MasterAccountProtectionFixture f;
    @BeforeEach void setup() {
        reset(users,roles,permissions,audit);
        f=new MasterAccountProtectionFixture(users,roles,permissions,encoder);
    }
    @ParameterizedTest @ValueSource(strings={"INACTIVO","BLOQUEADO","SUSPENDIDO"})
    void ordinaryCannotDisableMasterThroughApi(String state) throws Exception {
        invoke("PATCH","/usuarios/17/estado","{\"estado\":\""+state+"\"}",false,403);
        assertEquals("ACTIVO",f.master.getEstado()); verify(users,never()).save(any()); assertAudited(29,403);
    }
    @ParameterizedTest @ValueSource(longs={102,103})
    void ordinaryCannotChangeMasterRoleThroughApi(long role) throws Exception {
        invoke("PUT","/usuarios/17/rol","{\"rolId\":"+role+"}",false,403);
        assertSame(f.shared,f.master.getRol()); verify(users,never()).save(any()); assertAudited(29,403);
    }
    @ParameterizedTest @ValueSource(strings={"[1]","[2]","[]"})
    void indirectEssentialPermissionRemovalIsRejectedAndAudited(String ids) throws Exception {
        invoke("PUT","/roles/101/permisos","{\"permisoIds\":"+ids+"}",false,403);
        assertTrue(f.master.permisosEfectivos().containsAll(java.util.Set.of("ACCESO_ADMINISTRAR","DONACION_REGISTRAR")));
        verify(roles,never()).save(any()); assertAudited(29,403);
    }
    @Test void masterCannotReassignOwnRole() throws Exception {
        invoke("PUT","/usuarios/17/rol","{\"rolId\":102}",true,409);
        assertSame(f.shared,f.master.getRol()); verify(roles,never()).save(any()); assertAudited(17,409);
    }
    @Test void masterCanManageOtherAccountsAndUnrelatedRoles() throws Exception {
        invoke("PATCH","/usuarios/31/estado","{\"estado\":\"INACTIVO\"}",true,200);
        invoke("PUT","/usuarios/31/rol","{\"rolId\":103}",true,200);
        invoke("PUT","/roles/104/permisos","{\"permisoIds\":[]}",true,200);
        assertEquals("INACTIVO",f.other.getEstado()); assertSame(f.restricted,f.other.getRol());
        assertTrue(f.master.esAdministradorMaestro());
        var events=ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit,times(3)).saveAndFlush(events.capture());
        assertEquals(java.util.List.of("CAMBIO_ESTADO_USUARIO", "CAMBIO_ROL_USUARIO", "CAMBIO_PERMISOS_ROL"),
            events.getAllValues().stream().map(AuditRecord::getAccion).toList());
        for (var event:events.getAllValues()) {
            assertEquals(f.master.getId(),event.getUsuario().getId());
            assertNotNull(event.getDatosAnteriores()); assertNotNull(event.getDatosNuevos());
        }
    }
    @Test void ordinaryCannotChangePrivilegedSharedRole() throws Exception {
        invoke("PUT","/roles/101/permisos","{\"permisoIds\":[1,2,3]}",false,403);
        assertTrue(f.master.esAdministradorMaestro()); verify(roles,never()).save(any()); assertAudited(29,403);
    }
    @Test void ordinaryCannotCreateOrAssignPrivilegedAccounts() throws Exception {
        invoke("POST","/usuarios","{\"rolId\":101,\"nombre\":\"Nuevo\",\"apellido\":\"Usuario\",\"correo\":\"new@example.test\",\"password\":\"test-password-not-production\",\"estado\":\"ACTIVO\"}",false,403);
        invoke("PUT","/usuarios/31/rol","{\"rolId\":101}",false,403);
        invoke("PUT","/roles/101","{\"nombre\":\"Cambio\"}",false,403);
        verify(users,never()).save(any()); verify(roles,never()).save(any()); verify(audit,times(3)).saveAndFlush(any());
    }
    @Test void editsOrdinaryUserAndAuditsChangedFieldsWithoutPersonalData() throws Exception {
        invoke("PUT","/usuarios/31","{\"nombre\":\"Nuevo\",\"apellido\":\"Apellido\",\"correo\":\"NEW@EXAMPLE.TEST\"}",true,200);
        assertEquals("new@example.test",f.other.getCorreo());
        var event=ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(event.capture());
        assertEquals("EDICION_USUARIO",event.getValue().getAccion());
        assertEquals(f.master.getId(),event.getValue().getUsuario().getId());
        assertFalse(event.getValue().getDatosNuevos().contains("new@example.test"));
    }
    @Test void rejectsDuplicateEmailAndProtectsMasterPersonalData() throws Exception {
        when(users.existsByCorreoAndIdNot("taken@example.test",31L)).thenReturn(true);
        invoke("PUT","/usuarios/31","{\"nombre\":\"Nuevo\",\"apellido\":\"Apellido\",\"correo\":\"TAKEN@EXAMPLE.TEST\"}",true,409);
        invoke("PUT","/usuarios/17","{\"nombre\":\"Nuevo\",\"apellido\":\"Apellido\",\"correo\":\"test@example.test\"}",false,403);
        verify(users,never()).save(any());
        assertEquals("master@example.test",f.master.getCorreo());
    }
    @Test void editRolePreservesPermissionsAndAuditsChanges() throws Exception {
        var before=MasterAccountProtectionFixture.codes(f.shared);
        invoke("PUT","/roles/101","{\"nombre\":\"Nuevo nombre\",\"descripcion\":\"Actualizada\"}",true,200);
        assertEquals(before,MasterAccountProtectionFixture.codes(f.shared));
        var event=ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(event.capture());
        assertEquals("EDICION_ROL",event.getValue().getAccion());
        assertTrue(event.getValue().getDatosNuevos().contains("Nuevo nombre"));
    }
    @Test void editingEndpointsRequireAdmin() throws Exception {
        mvc.perform(put("/api/admin/usuarios/31").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"A\",\"apellido\":\"B\",\"correo\":\"a@example.test\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/roles/101").with(user("operator").authorities(() -> "DONACION_REGISTRAR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"A\"}")).andExpect(status().isForbidden());
    }
    @Test void auditFailureIsPropagatedOnUserEdit() {
        var failure=new IllegalStateException("audit unavailable");
        when(audit.saveAndFlush(any())).thenThrow(failure);
        var service=new AccessManagementService(users,roles,permissions,encoder,audit,new tools.jackson.databind.json.JsonMapper());
        assertSame(failure,assertThrows(IllegalStateException.class,() -> service.updateUser(31,
            new pe.redsolidaria.modules.access.api.dto.AccessDtos.UpdateUserRequest("Nuevo","Apellido","a@example.test"),f.master.getCorreo())));
    }
    @Test void masterCanCreateOrdinaryUsersRolesAndPermissions() throws Exception {
        when(roles.save(any())).thenAnswer(c -> {
            var role=(pe.redsolidaria.modules.access.domain.Role)c.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(role,"id",201L); return role;
        });
        when(permissions.save(any())).thenAnswer(c -> {
            var permission=(pe.redsolidaria.modules.access.domain.Permission)c.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(permission,"id",202L); return permission;
        });
        invoke("POST","/usuarios","{\"rolId\":104,\"nombre\":\"Nuevo\",\"apellido\":\"Usuario\",\"correo\":\"new@example.test\",\"password\":\"test-password-not-production\",\"estado\":\"ACTIVO\"}",true,201);
        invoke("POST","/roles","{\"codigo\":\"NUEVO\",\"nombre\":\"Nuevo\"}",true,201);
        invoke("POST","/permisos","{\"codigo\":\"REPORTE_CONSULTAR\",\"descripcion\":\"Consultar\"}",true,201);
        var account=ArgumentCaptor.forClass(pe.redsolidaria.modules.access.domain.UserAccount.class);
        verify(users).save(account.capture()); assertFalse(account.getValue().tieneMarcaAdministradorMaestro());
        verify(roles).save(any()); verify(permissions).save(any());
        var events=ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit,times(3)).saveAndFlush(events.capture());
        assertEquals(java.util.Set.of("CREACION_USUARIO", "CREACION_ROL", "CREACION_PERMISO"),
            events.getAllValues().stream().map(AuditRecord::getAccion).collect(java.util.stream.Collectors.toSet()));
        for (var event:events.getAllValues()) {
            assertEquals(f.master.getId(),event.getUsuario().getId());
            assertFalse(event.getDatosNuevos().contains("password"));
            assertFalse(event.getDatosNuevos().contains("test-password-not-production"));
            assertFalse(event.getDatosNuevos().contains(account.getValue().getPasswordHash()));
        }
    }
    @Test void rawMarkProtectsAnInactiveMasterAndStaleAuthoritiesDoNotAuthorizeAnInactiveActor() throws Exception {
        f.master.cambiarEstado("INACTIVO");
        invoke("PUT","/usuarios/17/rol","{\"rolId\":102}",false,403);
        assertSame(f.shared,f.master.getRol());
        f.ordinary.cambiarEstado("INACTIVO");
        invoke("PUT","/usuarios/31/rol","{\"rolId\":103}",false,401);
        assertSame(f.unrelated,f.other.getRol());
        verify(users,never()).save(any()); verify(audit,times(1)).saveAndFlush(any());
    }
    @Test void masterCannotLockItselfOut() throws Exception {
        invoke("PATCH","/usuarios/17/estado","{\"estado\":\"INACTIVO\"}",true,409);
        invoke("PUT","/usuarios/17/rol","{\"rolId\":103}",true,409);
        invoke("PUT","/roles/101/permisos","{\"permisoIds\":[1]}",true,409);
        verify(audit,times(3)).saveAndFlush(any()); verify(users,never()).save(any()); verify(roles,never()).save(any());
        assertTrue(f.master.esAdministradorMaestro());
    }
    @Test void forgedPrincipalCapabilitiesAndBodyIdentityCannotMakeOrdinaryAccountMaster() throws Exception {
        mvc.perform(put("/api/admin/usuarios/17/rol").with(user(f.ordinary.getCorreo())
                .authorities(() -> "ACCESO_ADMINISTRAR",() -> "ADMIN_MAESTRO"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rolId\":102,\"actorEmail\":\"master@example.test\"}"))
            .andExpect(status().isForbidden());
        assertSame(f.shared,f.master.getRol()); assertAudited(29,403);
    }
    static Stream<Arguments> reservedRequests() {
        String[][] endpoints={
            {"PATCH","/usuarios/17/estado","\"estado\":\"ACTIVO\""},
            {"PUT","/usuarios/31/rol","\"rolId\":103"},
            {"POST","/usuarios","\"rolId\":104,\"nombre\":\"Nuevo\",\"apellido\":\"Usuario\",\"correo\":\"new@example.test\",\"password\":\"secret-not-to-audit\",\"estado\":\"ACTIVO\""},
            {"POST","/roles","\"codigo\":\"NUEVO\",\"nombre\":\"Nuevo\""},
            {"PUT","/roles/104/permisos","\"permisoIds\":[]"},
            {"POST","/permisos","\"codigo\":\"NUEVO\",\"descripcion\":\"Nuevo\""}};
        return Stream.of(endpoints).flatMap(e -> Stream.of("admin_maestro","adminMaestro")
            .map(flag -> Arguments.of(e[0],e[1],"{"+e[2]+",\""+flag+"\":true}")));
    }
    @ParameterizedTest @MethodSource("reservedRequests")
    void reservedFlagIsRejectedOnEveryOrdinaryWriteEndpoint(String method,String path,String body) throws Exception {
        invoke(method,path,body,false,400);
        verify(users,never()).save(any()); verify(roles,never()).save(any()); verify(permissions,never()).save(any());
        assertFalse(f.ordinary.tieneMarcaAdministradorMaestro()); assertTrue(f.master.tieneMarcaAdministradorMaestro());
        assertAudited(29,400);
    }
    @Test void existingReservedPermissionAndBootstrapRestrictionsArePreserved() throws Exception {
        invoke("POST","/permisos","{\"codigo\":\"ADMIN_MAESTRO\",\"descripcion\":\"Reservado\"}",false,403);
        invoke("PUT","/roles/104/permisos","{\"permisoIds\":[1,2]}",false,403);
        verify(permissions,never()).save(any()); verify(roles,never()).save(any()); verify(audit,times(2)).saveAndFlush(any());
    }
    @Test void securityFiltersRejectMissingOrInsufficientAuthentication() throws Exception {
        mvc.perform(patch("/api/admin/usuarios/17/estado").contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"INACTIVO\"}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/usuarios/17/estado").with(user("operator").authorities(() -> "DONACION_REGISTRAR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"INACTIVO\"}"))
            .andExpect(status().isForbidden());
        verify(users,never()).save(any()); verifyNoInteractions(audit);
    }
    private void invoke(String method,String path,String body,boolean master,int expected) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),"/api/admin"+path)
            .with(user(master ? f.master.getCorreo() : f.ordinary.getCorreo()).authorities(() -> "ACCESO_ADMINISTRAR"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }
    private void assertAudited(long actor,int status) {
        var captured=ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(captured.capture());
        var record=captured.getValue(); assertEquals(actor,record.getUsuario().getId());
        assertEquals("OPERACION_ACCESO_BLOQUEADA",record.getAccion());
        assertTrue(record.getDatosNuevos().contains("\"estadoHttp\":"+status));
        assertTrue(record.getDatosNuevos().contains("/api/admin/"));
        assertFalse(record.getDatosNuevos().contains("secret-not-to-audit"));
        assertFalse(record.getDatosNuevos().contains("password"));
    }
    @TestConfiguration static class Dependencies {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean PermissionRepository permissions() { return mock(PermissionRepository.class); }
        @Bean AuditRecordRepository audit() { return mock(AuditRecordRepository.class); }
    }
}
