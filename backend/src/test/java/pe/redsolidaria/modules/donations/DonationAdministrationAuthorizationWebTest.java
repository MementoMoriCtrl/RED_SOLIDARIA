package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.donations.api.DonationAdministrationController;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.AdministrativeDonationListItem;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonorSummary;
import pe.redsolidaria.modules.donations.application.DonationQueryService;

@WebMvcTest(DonationAdministrationController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, DonationAdministrationAuthorizationWebTest.MockDependencies.class})
class DonationAdministrationAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationQueryService service;

    @BeforeEach
    void clearPreviousMockInvocations() { clearInvocations(service); }

    @Test
    void rejectsAnonymousAdministrativeListing() throws Exception {
        mvc.perform(get("/api/donaciones/administracion/listado"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAuthenticatedUserWithoutAdministrativePermission() throws Exception {
        mvc.perform(get("/api/donaciones/administracion/listado").with(user("operador").authorities(
                        new SimpleGrantedAuthority("DONACION_REGISTRAR"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void allowsAdministratorToListFilteredDonations() throws Exception {
        when(service.listAdministrative("RS-TEST", "REGISTRADA")).thenReturn(List.of(
                new AdministrativeDonationListItem(7L, "RS-TEST", LocalDateTime.parse("2026-04-02T10:00:00"),
                        "REGISTRADA", new DonorSummary(2L, "Donante"), null, null, null)));

        mvc.perform(get("/api/donaciones/administracion/listado")
                        .param("codigo", "RS-TEST").param("estado", "REGISTRADA")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoTrazabilidad").value("RS-TEST"))
                .andExpect(jsonPath("$[0].donante.nombre").value("Donante"));
        verify(service).listAdministrative("RS-TEST", "REGISTRADA");
    }

    @Test
    void allowsAdministratorToRequestAdministrativeDetail() throws Exception {
        clearInvocations(service);
        when(service.findAdministrativeById(12L)).thenReturn(
                new pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonationTraceResponse(
                        12L, "RS-DETAIL", LocalDateTime.parse("2026-04-02T10:00:00"), null,
                        "REGISTRADA", null, new DonorSummary(2L, "Donante"), null, null,
                        null, List.of(), List.of()));
        mvc.perform(get("/api/donaciones/administracion/12").with(user("admin").authorities(
                        new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.donante.nombre").value("Donante"));
        verify(service).findAdministrativeById(12L);
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean DonationQueryService donationQueryService() { return mock(DonationQueryService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
