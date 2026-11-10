package pe.redsolidaria.modules.logistics;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
@WebMvcTest(LogisticsController.class)
@Import({SecurityConfiguration.class,LogisticsAuthorizationWebTest.Dependencies.class})
class LogisticsAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @Autowired LogisticsService service;
    @Test void protectsAllLogisticsOperationsOnBackend() throws Exception {
        for(String path : new String[]{"/existencias","/traslados","/centros-distribucion"}) {
            mvc.perform(get("/api/admin/logistica"+path)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/logistica"+path).with(user("registrar").authorities(() -> "DONACION_REGISTRAR"))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/logistica"+path).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/admin/logistica/traslados").with(user("registrar").authorities(() -> "DONACION_REGISTRAR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/logistica/traslados/test/recepcion").with(user("registrar").authorities(() -> "DONACION_REGISTRAR"))).andExpect(status().isForbidden());
    }
    @Test void validatesUuidQuantitiesAndDispatchContract() throws Exception {
        String body="{\"operacionId\":\"42f5dc0e-ec90-48aa-8ba8-b130b2aec489\",\"detalleDonacionId\":1,\"centroDistribucionId\":2,\"cantidad\":10}";
        mvc.perform(post("/api/admin/logistica/traslados").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")).contentType("application/json").content(body)).andExpect(status().isOk());
        verify(service).dispatch(any(),eq("admin"));
        mvc.perform(post("/api/admin/logistica/traslados").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")).contentType("application/json").content(body.replace("10}","0}"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/logistica/traslados/x/recepcion").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")).contentType("application/json").content("{\"cantidad\":1}")).andExpect(status().isBadRequest());
    }
    @TestConfiguration static class Dependencies {
        @Bean LogisticsService service() { return mock(LogisticsService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
}
