package pe.redsolidaria.modules.access;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.api.AuthenticationValidationController;
import pe.redsolidaria.modules.access.api.dto.AccessDtos.AuthenticatedProfileResponse;
import pe.redsolidaria.modules.access.application.AccessManagementService;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@WebMvcTest(AuthenticationValidationController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, AuthenticationValidationWebTest.MockDependencies.class})
class AuthenticationValidationWebTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AccessManagementService service;

    @Test
    void validatesHttpBasicCredentialsWithoutRequiringAFunctionalPermission() throws Exception {
        Role role = new Role("ORGANIZACION", "Organización", null);
        role.asignar(new Permission("DONACION_REGISTRAR", "Registrar donaciones"));
        UserAccount account = new UserAccount(role, "Organización", "Prueba", "org@example.test",
                passwordEncoder.encode("clave-segura-de-prueba"), "ACTIVO");
        when(users.findByCorreo("org@example.test")).thenReturn(Optional.of(account));

        mvc.perform(get("/api/auth/validate").with(httpBasic("org@example.test", "clave-segura-de-prueba")))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsInvalidHttpBasicCredentials() throws Exception {
        mvc.perform(get("/api/auth/validate").with(httpBasic("org@example.test", "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/api/auth/validate")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheAuthenticatedProfileAndEffectivePermissions() throws Exception {
        Role role = new Role("ADMINISTRADOR", "Administrador", null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR", "Administrar acceso"));
        UserAccount account = new UserAccount(role, "Admin", "Prueba", "admin@example.test",
                passwordEncoder.encode("clave-segura-de-prueba"), "ACTIVO");
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(account));
        when(service.authenticatedProfile("admin@example.test")).thenReturn(
                new AuthenticatedProfileResponse("Admin", "Prueba", "admin@example.test", "ADMINISTRADOR",
                        "Administrador", java.util.Set.of("ACCESO_ADMINISTRAR")));

        mvc.perform(get("/api/auth/me").with(httpBasic("admin@example.test", "clave-segura-de-prueba")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("admin@example.test"))
                .andExpect(jsonPath("$.rolCodigo").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.permisos[0]").value("ACCESO_ADMINISTRAR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void allowsAnAuthenticatedNonAdminToReadOnlyTheirOwnProfile() throws Exception {
        Role role = new Role("ORGANIZACION", "Organización", null);
        role.asignar(new Permission("DONACION_REGISTRAR", "Registrar donaciones"));
        UserAccount account = new UserAccount(role, "Organización", "Prueba", "org@example.test",
                passwordEncoder.encode("clave-segura-de-prueba"), "ACTIVO");
        when(users.findByCorreo("org@example.test")).thenReturn(Optional.of(account));
        when(service.authenticatedProfile("org@example.test")).thenReturn(
                new AuthenticatedProfileResponse("Organización", "Prueba", "org@example.test", "ORGANIZACION",
                        "Organización", java.util.Set.of("DONACION_REGISTRAR")));

        mvc.perform(get("/api/auth/me").with(httpBasic("org@example.test", "clave-segura-de-prueba")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolCodigo").value("ORGANIZACION"))
                .andExpect(jsonPath("$.permisos[0]").value("DONACION_REGISTRAR"));
    }

    @Test
    void rejectsAnonymousProfileRequest() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean UserRepository userRepository() { return org.mockito.Mockito.mock(UserRepository.class); }
        @Bean AccessManagementService accessManagementService() { return mock(AccessManagementService.class); }
    }
}
