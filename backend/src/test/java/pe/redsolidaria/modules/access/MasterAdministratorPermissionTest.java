package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.domain.*;
import pe.redsolidaria.modules.access.persistence.UserRepository;

class MasterAdministratorPermissionTest {
    @Test void accountCapabilityIsExclusiveEvenWhenUsersShareTheirRole() {
        var role = new Role("ADMINISTRADOR", "Administrador", null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR", null));
        // Even a malformed role assignment cannot grant the reserved authority.
        role.asignar(new Permission("ADMIN_MAESTRO", null));
        var master = new UserAccount(role,"M","A","master@example.test","unused","ACTIVO");
        var other = new UserAccount(role,"N","A","other@example.test","unused","ACTIVO");
        ReflectionTestUtils.setField(master,"adminMaestro",true);
        assertTrue(master.esAdministradorMaestro());
        assertFalse(other.esAdministradorMaestro());
        assertFalse(other.permisosEfectivos().contains("ADMIN_MAESTRO"));
        var users = mock(UserRepository.class);
        when(users.findByCorreo(master.getCorreo())).thenReturn(java.util.Optional.of(master));
        var details = new SecurityConfiguration().userDetailsService(users).loadUserByUsername(master.getCorreo());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN_MAESTRO")));
        var service = new pe.redsolidaria.modules.access.application.AccessManagementService(users,null,null,null);
        assertTrue(service.authenticatedProfile(master.getCorreo()).permisos().contains("ADMIN_MAESTRO"));
        master.cambiarEstado("INACTIVO");
        assertFalse(master.esAdministradorMaestro());
        master.cambiarEstado("ACTIVO");
        master.cambiarRol(new Role("OPERADOR","Operador",null));
        assertFalse(master.esAdministradorMaestro());
    }
}
