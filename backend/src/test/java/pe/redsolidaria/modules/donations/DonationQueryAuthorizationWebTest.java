package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.donations.api.DonationQueryController;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.PublicCollectionCenterSummary;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.PublicDonationMovementSummary;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.PublicDonationTraceResponse;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.PublicDonationDetailSummary;
import pe.redsolidaria.modules.donations.application.DonationQueryService;

@WebMvcTest(DonationQueryController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, DonationQueryAuthorizationWebTest.MockDependencies.class})
class DonationQueryAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationQueryService service;

    @Test
    void permitsAnonymousPublicQueryAndReturnsOnlyPublicFields() throws Exception {
        when(service.findPublicByTrackingCode("RS-TRACE-1")).thenReturn(response());

        mvc.perform(get("/api/donaciones/RS-TRACE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoTrazabilidad").value("RS-TRACE-1"))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.centroAcopio.ubicacion").value("Miraflores"))
                .andExpect(jsonPath("$.detalles[0].recursoNombre").value("Agua"))
                .andExpect(jsonPath("$.detalles[0].recursoId").doesNotExist())
                .andExpect(jsonPath("$.movimientos[0].estadoNuevo").value("REGISTRADA"))
                .andExpect(jsonPath("$.donante").doesNotExist())
                .andExpect(jsonPath("$.emergenciaNombre").doesNotExist())
                .andExpect(jsonPath("$.observaciones").doesNotExist())
                .andExpect(jsonPath("$.movimientos[0].responsableNombre").doesNotExist())
                .andExpect(jsonPath("$.movimientos[0].observaciones").doesNotExist());
        verify(service).findPublicByTrackingCode("RS-TRACE-1");
    }

    @Test
    void doesNotMakeNestedDonationRoutesPublic() throws Exception {
        clearInvocations(service);
        mvc.perform(get("/api/donaciones/RS-TRACE-1/movimientos"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void publicQueryDoesNotRequirePermissionsEvenForAnAuthenticatedUser() throws Exception {
        when(service.findPublicByTrackingCode("RS-TRACE-1")).thenReturn(response());

        mvc.perform(get("/api/donaciones/RS-TRACE-1").with(user("operador").authorities(
                        new SimpleGrantedAuthority("SIN_PERMISO"))))
                .andExpect(status().isOk());
    }

    @Test
    void returnsNotFoundForUnknownTrackingCodeWithoutAuthentication() throws Exception {
        when(service.findPublicByTrackingCode("RS-MISSING"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/donaciones/RS-MISSING"))
                .andExpect(status().isNotFound());
    }

    private static PublicDonationTraceResponse response() {
        LocalDateTime now = LocalDateTime.parse("2026-04-02T10:00:00");
        return new PublicDonationTraceResponse("RS-TRACE-1", now, null, "REGISTRADA",
                new PublicCollectionCenterSummary("Centro", "Miraflores"),
                List.of(new PublicDonationDetailSummary("Agua", java.math.BigDecimal.TEN,
                        null, "L", "Litro", "L")),
                List.of(new PublicDonationMovementSummary(null, "REGISTRADA", now, "Miraflores")));
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean DonationQueryService donationQueryService() { return mock(DonationQueryService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
