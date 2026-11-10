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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.redsolidaria.test.StrictTestMySqlSupport;

/** Public query verification against a dedicated synthetic test fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class DonationQueryMySqlIntegrationTest extends StrictTestMySqlSupport {
    private String trackingCode;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;

    @BeforeEach
    void prepareSyntheticDonation() {
        var fixture = new PhaseOneControlledMySqlIntegrationTest();
        fixture.jdbc = jdbc;
        fixture.tx = tx;
        fixture.fixturesInDedicatedDatabaseOnly();
        trackingCode = jdbc.queryForObject("select codigo_trazabilidad from donacion where id_donacion=?",
                String.class, fixture.donationId);
        jdbc.update("""
                insert into movimiento_donacion(id_donacion,id_usuario_responsable,estado_nuevo,observaciones,tipo_evento)
                select id_donacion,id_usuario_registro,estado,?,? from donacion where id_donacion=?
                """, "Private synthetic observation", "DONACION_PRESENTADA", fixture.donationId);
        System.out.printf("DONATION_QUERY_FIXTURE donation=%d line=%d resource=%d tracking=%s%n",
                fixture.donationId, fixture.lineId, fixture.resourceId, trackingCode);
    }

    @Test
    void readsKnownDonationFromLocalMySqlByTrackingCode() throws Exception {
        mvc.perform(get("/api/donaciones/{codigoTrazabilidad}", trackingCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoTrazabilidad").value(trackingCode))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.detalles").isArray())
                .andExpect(jsonPath("$.movimientos").isArray())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.donante").doesNotExist())
                .andExpect(jsonPath("$.observaciones").doesNotExist())
                .andExpect(jsonPath("$.movimientos[0].responsableNombre").doesNotExist());
    }
}
