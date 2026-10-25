package pe.redsolidaria.modules.emergencies;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.emergencies.api.EmergencyManagementController;
import pe.redsolidaria.modules.emergencies.api.dto.EmergencyDtos.EmergencyPageResponse;
import pe.redsolidaria.modules.emergencies.application.EmergencyManagementService;

@WebMvcTest(EmergencyManagementController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, EmergencyManagementAuthorizationWebTest.Dependencies.class})
class EmergencyManagementAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired EmergencyManagementService service;

    @BeforeEach void clearPreviousInvocations() { Mockito.clearInvocations(service); }

    @Test void blocksAnonymousAndAuthenticatedNonAdminFromEmergencyListing() throws Exception {
        mvc.perform(get("/api/admin/emergencias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/emergencias").with(user("organization").authorities(
                new SimpleGrantedAuthority("DONACION_REGISTRAR")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void allowsAdministratorToListAndCreateEmergency() throws Exception {
        when(service.list(null, null, 0, 20)).thenReturn(new EmergencyPageResponse(List.of(), 0, 20, 0, 0, true));
        mvc.perform(get("/api/admin/emergencias").with(user("admin").authorities(
                new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/admin/emergencias").contentType("application/json")
                .content("""
                        {"nombre":"Inundación","tipo":"INUNDACION","fechaInicio":"2026-06-01","estado":"ACTIVA"}
                        """).with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isCreated());
    }

    @Test void blocksEveryMutationForAnonymousAndNonAdministratorUsers() throws Exception {
        var methods = List.of(org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.POST,
                org.springframework.http.HttpMethod.PUT);
        var paths = List.of("/api/admin/emergencias", "/api/admin/emergencias/1",
                "/api/admin/emergencias/1/estado", "/api/admin/emergencias/1/zonas",
                "/api/admin/emergencias/1/zonas/9");
        for (int index = 0; index < paths.size(); index++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(methods.get(index), paths.get(index))
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(methods.get(index), paths.get(index))
                    .contentType("application/json").content("{}")
                    .with(user("operator").authorities(new SimpleGrantedAuthority("DONACION_REGISTRAR"))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void rejectsInvalidPaginationAndBlankZoneBeforeCallingService() throws Exception {
        mvc.perform(get("/api/admin/emergencias").param("size", "101")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/emergencias/1/zonas").contentType("application/json")
                .content("{\"nombre\":\" \"}")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @TestConfiguration
    static class Dependencies {
        @Bean EmergencyManagementService emergencyManagementService() { return mock(EmergencyManagementService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
