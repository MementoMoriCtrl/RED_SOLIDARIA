package pe.redsolidaria.modules.donations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import pe.redsolidaria.test.RequiresLocalRedSolidariaMySql;

/** Read-only verification against the known local donation fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@RequiresLocalRedSolidariaMySql
class DonationQueryMySqlIntegrationTest {
    private static final String TRACKING_CODE = "RS-1BC366C7BC08456E9D19831387D7194C";

    @Autowired MockMvc mvc;

    @Test
    void readsKnownDonationFromLocalMySqlByTrackingCode() throws Exception {
        mvc.perform(get("/api/donaciones/{codigoTrazabilidad}", TRACKING_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoTrazabilidad").value(TRACKING_CODE))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.detalles").isArray())
                .andExpect(jsonPath("$.movimientos").isArray())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.donante").doesNotExist())
                .andExpect(jsonPath("$.observaciones").doesNotExist())
                .andExpect(jsonPath("$.movimientos[0].responsableNombre").doesNotExist());
    }
}
