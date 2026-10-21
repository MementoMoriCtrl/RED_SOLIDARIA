package pe.redsolidaria.modules.donations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
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
import pe.redsolidaria.modules.donations.api.DonationRegistrationController;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonationResponse;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;

@WebMvcTest(DonationRegistrationController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, DonationRegistrationAuthorizationWebTest.MockDependencies.class})
class DonationRegistrationAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationRegistrationService service;
    private static final String BODY = """
            {"donanteId":11,"centroAcopioId":3,"detalles":[{"recursoId":7,"cantidad":1.000}]}
            """;

    @Test
    void rejectsUnauthenticatedRegistration() throws Exception {
        mvc.perform(post("/api/donaciones").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedUserWithoutRegistrationPermission() throws Exception {
        clearInvocations(service);
        mvc.perform(post("/api/donaciones").with(user("operador").authorities(
                        new SimpleGrantedAuthority("ACCESO_ADMINISTRAR")))
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void allowsUserWithSpecificRegistrationPermission() throws Exception {
        when(service.register(any(), eq("operador"))).thenReturn(
                new DonationResponse(9L, "RS-TEST", LocalDateTime.now(), LocalDateTime.now(),
                        "REGISTRADA", 11L, "Donante", null, 3L, List.of()));
        mvc.perform(post("/api/donaciones").with(user("operador").authorities(
                        new SimpleGrantedAuthority("DONACION_REGISTRAR")))
                .contentType("application/json").content(BODY)).andExpect(status().isCreated());
        verify(service).register(any(), eq("operador"));
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean DonationRegistrationService donationRegistrationService() { return mock(DonationRegistrationService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
