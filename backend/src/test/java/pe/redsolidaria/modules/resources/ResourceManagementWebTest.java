package pe.redsolidaria.modules.resources;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.resources.api.MeasurementUnitController;
import pe.redsolidaria.modules.resources.api.ResourceManagementController;
import pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse;
import pe.redsolidaria.modules.resources.api.dto.ResourceDtos.ResourceResponse;
import pe.redsolidaria.modules.resources.application.ResourceManagementService;

@WebMvcTest(controllers = {ResourceManagementController.class, MeasurementUnitController.class})
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, ResourceManagementWebTest.MockDependencies.class})
class ResourceManagementWebTest {
    @Autowired MockMvc mvc;
    @Autowired ResourceManagementService service;

    @Test
    void requiresAuthenticationToReadResourcesAndMeasurementUnits() throws Exception {
        mvc.perform(get("/api/recursos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/unidades-medida")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsResourcesAndMeasurementUnitsToAnAuthenticatedUser() throws Exception {
        when(service.resources()).thenReturn(List.of(new ResourceResponse(5L, "Agua", "Bebidas", null,
                "ACTIVO", new MeasurementUnitResponse(2L, "L", "Litro", "L"))));
        when(service.measurementUnits()).thenReturn(List.of(new MeasurementUnitResponse(2L, "L", "Litro", "L")));

        mvc.perform(get("/api/recursos").with(user("organizacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Agua"))
                .andExpect(jsonPath("$[0].unidadMedida.simbolo").value("L"));
        mvc.perform(get("/api/unidades-medida").with(user("organizacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("L"));
    }

    @Test
    void protectsCreateWithExistingAdminPermission() throws Exception {
        mvc.perform(post("/api/recursos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Agua\",\"categoria\":\"Bebidas\",\"unidadMedidaId\":2}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/recursos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Agua","categoria":"Bebidas","descripcion":null,"unidadMedidaId":2}
                                """).with(user("operador").authorities(new SimpleGrantedAuthority("DONACION_REGISTRAR"))))
                .andExpect(status().isForbidden());

        ResourceResponse created = new ResourceResponse(9L, "Agua", "Bebidas", null, "ACTIVO",
                new MeasurementUnitResponse(2L, "L", "Litro", "L"));
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(created);
        mvc.perform(post("/api/recursos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Agua","categoria":"Bebidas","descripcion":null,"unidadMedidaId":2}
                                """).with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
        verify(service).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validatesSchemaLengthsBeforeCallingTheService() throws Exception {
        String tooLongName = "x".repeat(101);
        mvc.perform(post("/api/recursos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + tooLongName + "\",\"categoria\":\"Bebidas\",\"unidadMedidaId\":2}")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void doesNotExposePhysicalDeleteEndpoint() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/recursos/9")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))))
                .andExpect(status().isMethodNotAllowed());
    }

    @TestConfiguration
    static class MockDependencies {
        @Bean ResourceManagementService resourceManagementService() { return mock(ResourceManagementService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
