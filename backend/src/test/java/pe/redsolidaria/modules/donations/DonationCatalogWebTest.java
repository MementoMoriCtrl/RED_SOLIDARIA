package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.donations.api.DonationCatalogController;
import pe.redsolidaria.modules.donations.api.dto.DonationCatalogDtos.*;
import pe.redsolidaria.modules.donations.application.DonationCatalogService;
import pe.redsolidaria.modules.emergencies.api.EmergencyBalanceController;
import pe.redsolidaria.modules.emergencies.persistence.*;

@WebMvcTest({DonationCatalogController.class, EmergencyBalanceController.class})
@Import({SecurityConfiguration.class, DonationCatalogWebTest.Dependencies.class})
class DonationCatalogWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationCatalogService catalogs;
    @Autowired EmergencyRepository emergencies;
    @Autowired EmergencyBalanceRepository balances;
    @Test void protectsCatalogReadsAndAdministrativeWrites() throws Exception {
        mvc.perform(get("/api/donaciones/catalogos/donantes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/donaciones/catalogos/donantes").with(user("no-permission")))
                .andExpect(status().isForbidden());
        when(catalogs.list("donantes", "", 0)).thenReturn(new CatalogPage(List.of(new CatalogItem(1L, "Fixture", null)), 0, 1));
        mvc.perform(get("/api/donaciones/catalogos/donantes").with(user("registrar").authorities(() -> "DONACION_REGISTRAR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].nombre").value("Fixture"));
        for (String kind : List.of("donantes", "centros", "unidades")) {
            mvc.perform(post("/api/admin/catalogos-donaciones/" + kind).with(user("registrar").authorities(() -> "DONACION_REGISTRAR"))
                    .contentType("application/json").content("{\"nombre\":\"Fixture\"}"))
                    .andExpect(status().isForbidden());
        }
    }
    @Test void validatesCatalogDataAndAllowsAdministratorCreation() throws Exception {
        mvc.perform(post("/api/admin/catalogos-donaciones/donantes").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/catalogos-donaciones/centros").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"nombre\":\"Fixture\",\"ubicacion\":\"Local\"}"))
                .andExpect(status().isCreated());
        verify(catalogs).create(eq("centros"), eq("admin"), any());
        mvc.perform(post("/api/admin/catalogos-donaciones/unidades").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))
                .contentType("application/json").content("{\"nombre\":\"Litro\",\"codigo\":\"L\"}"))
                .andExpect(status().isCreated());
        verify(catalogs).createUnit(eq("admin"), any());
    }
    @Test void protectsEmergencyBalancesAndReturnsNotFoundForUnknownEmergency() throws Exception {
        mvc.perform(get("/api/admin/emergencias/1/balance").with(user("registrar").authorities(() -> "DONACION_REGISTRAR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/emergencias/999/balance").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
                .andExpect(status().isNotFound());
        when(emergencies.existsById(1L)).thenReturn(true);
        when(balances.balance(1L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/emergencias/1/balance").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @TestConfiguration static class Dependencies {
        @Bean DonationCatalogService donationCatalogService() { return mock(DonationCatalogService.class); }
        @Bean EmergencyRepository emergencyRepository() { return mock(EmergencyRepository.class); }
        @Bean EmergencyBalanceRepository emergencyBalanceRepository() { return mock(EmergencyBalanceRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
}
