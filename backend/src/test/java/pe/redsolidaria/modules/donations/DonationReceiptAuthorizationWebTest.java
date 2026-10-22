package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import pe.redsolidaria.modules.donations.api.DonationReceiptController;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;

@WebMvcTest(DonationReceiptController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, DonationReceiptAuthorizationWebTest.Dependencies.class})
class DonationReceiptAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationReceiptService service;

    @Test void rejectsAnonymousReceipt() throws Exception {
        mvc.perform(post("/api/donaciones/administracion/15/recepciones").contentType("application/json")
                .content("{\"detalles\":[{\"recursoId\":3,\"cantidad\":1}] }"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void rejectsAuthenticatedNonAdministrator() throws Exception {
        mvc.perform(post("/api/donaciones/administracion/15/recepciones").with(user("operador").authorities(
                        new SimpleGrantedAuthority("DONACION_REGISTRAR")))
                .contentType("application/json").content("{\"detalles\":[{\"recursoId\":3,\"cantidad\":1}] }"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void requiresAdministratorPermission() throws Exception {
        mvc.perform(post("/api/donaciones/administracion/15/recepciones").with(user("admin").authorities(
                        new SimpleGrantedAuthority("ACCESO_ADMINISTRAR")))
                .contentType("application/json").content("{\"detalles\":[{\"recursoId\":3,\"cantidad\":1}] }"))
                .andExpect(status().isOk());
        verify(service).register(org.mockito.ArgumentMatchers.eq(15L), org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.any());
    }

    @TestConfiguration static class Dependencies {
        @Bean DonationReceiptService receiptService() { return mock(DonationReceiptService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
