package pe.redsolidaria.modules.access;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.api.AccessManagementController;
import pe.redsolidaria.modules.access.application.AccessManagementService;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@WebMvcTest(AccessManagementController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, AccessAuthorizationWebTest.MockDependencies.class})
class AccessAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired AccessManagementService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void rejectsAuthenticatedUserWithoutAdminPermission() throws Exception {
        mvc.perform(get("/api/admin/usuarios")
                .with(user("operador").authorities(new SimpleGrantedAuthority("DONACIONES_CONSULTAR"))))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void allowsRequestWithRequiredPermission() throws Exception {
        when(service.users()).thenReturn(List.of());
        mvc.perform(get("/api/admin/usuarios")
                .with(user("administrador").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isOk());
        verify(service).users();
    }

    @Test
    void allowsHttpBasicCredentialsLoadedFromUserRepository() throws Exception {
        Role role = new Role("ADMINISTRADOR", "Administrador", null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR", "Administrar acceso"));
        UserAccount account = new UserAccount(role, "Admin", "Prueba", "admin@example.test",
                passwordEncoder.encode("clave-de-prueba-segura"), "ACTIVO");
        when(users.findByCorreo("admin@example.test")).thenReturn(java.util.Optional.of(account));
        when(service.roles()).thenReturn(List.of());

        mvc.perform(get("/api/admin/roles")
                        .with(httpBasic("admin@example.test", "clave-de-prueba-segura")))
                .andExpect(status().isOk());
        verify(users).findByCorreo("admin@example.test");
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean AccessManagementService accessManagementService() { return mock(AccessManagementService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
