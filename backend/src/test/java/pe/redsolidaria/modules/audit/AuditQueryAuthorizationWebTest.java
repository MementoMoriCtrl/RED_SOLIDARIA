package pe.redsolidaria.modules.audit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.audit.api.AuditQueryController;
import pe.redsolidaria.modules.audit.api.dto.AuditDtos.AuditEventPageResponse;
import pe.redsolidaria.modules.audit.api.dto.AuditDtos.AuditEventResponse;
import pe.redsolidaria.modules.audit.application.AuditQueryService;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@WebMvcTest(AuditQueryController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, AuditQueryAuthorizationWebTest.MockDependencies.class})
class AuditQueryAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired AuditQueryService service;

    @BeforeEach void clearPreviousInvocations() { clearInvocations(service); }

    @Test void rejectsAnonymousAndNonAdministratorUsers() throws Exception {
        mvc.perform(get("/api/admin/auditoria")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auditoria").with(user("operator").authorities(
                new SimpleGrantedAuthority("DONACION_REGISTRAR")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void rejectsInvalidPaginationParameters() throws Exception {
        mvc.perform(get("/api/admin/auditoria").param("size", "0")
                .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isBadRequest());
    }

    @Test void administratorGetsPagedMetadataWithoutAuditJsonOrAccountSecrets() throws Exception {
        when(service.search("RECEPCION_DONACION", "DONACION", null, "Ana", null, null, 0, 20))
                .thenReturn(new AuditEventPageResponse(List.of(new AuditEventResponse(4L,
                        LocalDateTime.parse("2026-05-02T11:30:00"), "RECEPCION_DONACION", "DONACION",
                        "22", "Ana Ruiz")), 0, 20, 1, 1, true));

        mvc.perform(get("/api/admin/auditoria").param("accion", "RECEPCION_DONACION")
                        .param("entidad", "DONACION").param("usuario", "Ana")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accion").value("RECEPCION_DONACION"))
                .andExpect(jsonPath("$.content[0].responsableNombre").value("Ana Ruiz"))
                .andExpect(jsonPath("$.content[0].datosAnteriores").doesNotExist())
                .andExpect(jsonPath("$.content[0].datosNuevos").doesNotExist())
                .andExpect(jsonPath("$.content[0].usuarioId").doesNotExist())
                .andExpect(jsonPath("$.content[0].correo").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean AuditQueryService auditQueryService() { return mock(AuditQueryService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
