package pe.redsolidaria.modules.reports;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pe.redsolidaria.test.SessionWebTestUsers.user;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@WebMvcTest(DeliveryReportController.class)
@Import({SecurityConfiguration.class, DeliveryReportWebTest.Dependencies.class})
class DeliveryReportWebTest {
    @Autowired MockMvc mvc;
    @Autowired DeliveryReportRepository reports;
    @BeforeEach void resetReports() { reset(reports); }

    @Test void requiresAdministrativePermission() throws Exception {
        mvc.perform(get("/api/admin/reportes/entregas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reportes/entregas").with(user("operator").authorities(() -> "DONACION_REGISTRAR")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }
    @Test void preservesEventQuantitiesAndInclusiveDateRange() throws Exception {
        var item = new DeliveryReportItem(4L, LocalDateTime.parse("2026-01-03T12:00:00"), "E", "Z", 7L,
                "Agua", "L", "RS-1", new BigDecimal("1.000"), new BigDecimal("0.500"));
        when(reports.findDeliveries(any(), any(), any())).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 20), 21));
        mvc.perform(get("/api/admin/reportes/entregas").param("fechaDesde", "2026-01-01").param("fechaHasta", "2026-01-03")
                .param("page", "1").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.totalElements").value(21)).andExpect(jsonPath("$.content[0].cantidadEntregada").value(0.5))
            .andExpect(jsonPath("$.content[0].unidad").value("L"));
        verify(reports).findDeliveries(eq(LocalDateTime.parse("2026-01-01T00:00:00")),
                eq(LocalDateTime.parse("2026-01-04T00:00:00")), eq(PageRequest.of(1, 20)));
    }
    @Test void rejectsInvalidRangeAndPage() throws Exception {
        mvc.perform(get("/api/admin/reportes/entregas").param("fechaDesde", "2026-02-01").param("fechaHasta", "2026-01-01")
                .with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/reportes/entregas").param("page", "-1")
                .with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR"))).andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }
    @TestConfiguration static class Dependencies {
        @Bean DeliveryReportRepository reports() { return mock(DeliveryReportRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
}
