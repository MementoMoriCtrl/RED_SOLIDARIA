package pe.redsolidaria.modules.logistics;

import static org.mockito.Mockito.*;
import static pe.redsolidaria.test.SessionWebTestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@WebMvcTest(TransferResolutionController.class)
@Import({SecurityConfiguration.class,TransferResolutionAuthorizationWebTest.Dependencies.class})
class TransferResolutionAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired TransferResolutionService service;
    @Test void requiresSessionPermissionAndCsrfAndValidatesBody() throws Exception {
        String path="/api/admin/logistica/traslados/transfer/resolucion";
        String body="{\"operacionId\":\"42f5dc0e-ec90-48aa-8ba8-b130b2aec489\",\"tipo\":\"PERDIDA\",\"motivo\":\"Acta\"}";
        mvc.perform(post(path).with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user("registrar").authorities(() -> "DONACION_REGISTRAR")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(request -> {
            var authenticated = user("admin").authorities(() -> "ACCESO_ADMINISTRAR").postProcessRequest(request);
            // The shared identity fixture adds a valid CSRF parameter; remove it for this negative case.
            authenticated.removeParameter("_csrf");
            return authenticated;
        }).contentType("application/json").content(body)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(service);
        mvc.perform(post(path).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post(path).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")).with(csrf()).contentType("application/json").content(body.replace("PERDIDA","INVALIDO"))).andExpect(status().isBadRequest());
    }
    @TestConfiguration static class Dependencies {
        @Bean TransferResolutionService service() { return mock(TransferResolutionService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
}
