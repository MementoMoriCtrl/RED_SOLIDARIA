package pe.redsolidaria.modules.needs;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.needs.api.NeedManagementController;
import pe.redsolidaria.modules.needs.api.dto.NeedDtos.NeedPage;
import pe.redsolidaria.modules.needs.application.NeedManagementService;
import pe.redsolidaria.modules.deliveries.api.DeliveryManagementController;
import pe.redsolidaria.modules.deliveries.application.DeliveryManagementService;

@WebMvcTest({NeedManagementController.class, DeliveryManagementController.class})
@Import({SecurityConfiguration.class, NeedManagementWebTest.Dependencies.class})
class NeedManagementWebTest {
    @Autowired MockMvc mvc;
    @Autowired NeedManagementService needs;
    @Autowired DeliveryManagementService deliveries;
    static final String ZONE = "/api/admin/emergencias/1/zonas/2/necesidades";
    static final String DELIVERY = "/api/admin/necesidades/3/donaciones/4/entrega";
    @BeforeEach void clearCalls() { clearInvocations(needs, deliveries); }

    @Test void protectsReadsAndWritesFromAnonymousAndNonAdministrators() throws Exception {
        for (var request : List.of(get(ZONE), post(ZONE), get("/api/admin/necesidades/3/donaciones"), put(DELIVERY))) {
            mvc.perform(request.contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
            mvc.perform(request.with(user("operator").authorities(() -> "DONACION_REGISTRAR")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(needs, deliveries);
    }

    @Test void adminCanListCreateAndDeliver() throws Exception {
        when(needs.list(1, 2, 0)).thenReturn(new NeedPage(List.of(), 0, 0, 0));
        mvc.perform(get(ZONE).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post(ZONE).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"recursoId\":5,\"cantidad\":2.5,\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isCreated());
        verify(needs).create(eq(1L), eq(2L), eq("admin"), any());
        mvc.perform(put(DELIVERY).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"totalAsignado\":2.5,\"totalEntregado\":1}"))
                .andExpect(status().isOk());
        verify(deliveries).save(eq(3L), eq(4L), eq("admin"), any());
    }

    @Test void validatesPaginationResourceAndDecimalPrecision() throws Exception {
        mvc.perform(get(ZONE).param("page", "-1").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{\"recursoId\":0,\"cantidad\":1,\"prioridad\":\"ALTA\"}",
                "{\"recursoId\":1,\"cantidad\":0.0001,\"prioridad\":\"ALTA\"}",
                "{\"recursoId\":1,\"cantidad\":1,\"prioridad\":\" \"}")) {
            mvc.perform(post(ZONE).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put(DELIVERY).with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"totalAsignado\":-1,\"totalEntregado\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(needs, deliveries);
    }
    @TestConfiguration static class Dependencies {
        @Bean NeedManagementService needManagementService() { return mock(NeedManagementService.class); }
        @Bean DeliveryManagementService deliveryManagementService() { return mock(DeliveryManagementService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
