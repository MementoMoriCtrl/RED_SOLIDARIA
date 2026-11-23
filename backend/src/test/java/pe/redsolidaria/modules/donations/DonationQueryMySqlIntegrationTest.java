package pe.redsolidaria.modules.donations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Set;
import pe.redsolidaria.test.PhaseOneConfirmedRows;

/** Public query verification against a dedicated synthetic test fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class DonationQueryMySqlIntegrationTest extends ControlledDonationMySqlSupport {
    private String trackingCode;

    @Autowired MockMvc mvc;
    @Override protected Map<String,Set<String>> requiredExistingRows() {
        return PhaseOneConfirmedRows.loadAllConfirmed();
    }

    @Override protected void prepareOperationFixtures() {
        var operation=new TransactionTemplate(tx);
        operation.setName("donation-query.presented-movement");
        operation.executeWithoutResult(status -> fixtureAudit.insertPresentedDonationMovement());
        fixtureAudit.requireReadyForScenario();
        trackingCode=jdbc.queryForObject("select codigo_trazabilidad from donacion where id_donacion=?",
                String.class,donationId);
    }

    @Test
    @WithAnonymousUser
    void readsKnownDonationFromLocalMySqlByTrackingCode() throws Exception {
        mvc.perform(get("/api/donaciones/{codigoTrazabilidad}", trackingCode).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoTrazabilidad").value(trackingCode))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.detalles").isArray())
                .andExpect(jsonPath("$.movimientos").isArray())
                .andExpect(jsonPath("$.movimientos.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.donante").doesNotExist())
                .andExpect(jsonPath("$.observaciones").doesNotExist())
                .andExpect(jsonPath("$.movimientos[*].responsableNombre").doesNotExist());
    }
}
