package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import pe.redsolidaria.modules.donations.persistence.DonationCustodyRepository;

/** Real MySQL SELECTs only: no application context, Flyway, fixture inserts or session collation changes. */
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME",matches="rs_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DonationCustodyReadOnlyMySqlIntegrationTest {
    Connection connection;
    JdbcClient jdbc;
    DonationCustodyRepository repository;

    @BeforeAll void connectExclusivelyToTheTestDatabase() throws Exception {
        String url=System.getenv("TEST_DB_URL");
        String password=System.getenv("TEST_DB_PASSWORD");
        assertNotNull(url,"TEST_DB_URL required; no fallback");
        assertTrue(url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):3306/red_solidaria_test"),
            "Only local red_solidaria_test on port 3306 is authorized");
        assertEquals("rs_test",System.getenv("TEST_DB_USERNAME"));
        assertNotNull(password,"TEST_DB_PASSWORD required"); assertFalse(password.isBlank());
        connection=DriverManager.getConnection(url,"rs_test",password);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        jdbc=JdbcClient.create(new SingleConnectionDataSource(connection,true));
        assertEquals("red_solidaria_test",jdbc.sql("SELECT DATABASE()").query(String.class).single());
        assertEquals("rs_test",jdbc.sql("SELECT SUBSTRING_INDEX(CURRENT_USER(),'@',1)").query(String.class).single());
        assertEquals("rs_test",jdbc.sql("SELECT SUBSTRING_INDEX(USER(),'@',1)").query(String.class).single());
        repository=new DonationCustodyRepository(jdbc);
    }

    @AfterAll void releaseReadOnlyTransaction() throws Exception {
        if (connection!=null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test void fullProjectionCanResolveTheAuditJoinEvenWithoutMatchingRows() {
        assertTrue(repository.events(0L).isEmpty());
    }

    @Test void reproducesTheReportedCollationEnvironmentAndRunsTheFullProjection() {
        assertEquals("utf8mb4_unicode_ci",jdbc.sql("""
            SELECT COLLATION_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='registro_auditoria' AND COLUMN_NAME='id_registro'
            """).query(String.class).single());
        assertEquals("utf8mb4_0900_ai_ci",jdbc.sql("SELECT COLLATION(CAST(1 AS CHAR))").query(String.class).single(),
            "The reported mixed-collation environment is required; no session settings will be changed");
        assertTrue(repository.events(0L).isEmpty());
    }

    @Test void fullProjectionReadsExistingSyntheticDonationsWithoutCreatingFixtures() {
        List<Long> donations=jdbc.sql("""
            SELECT id_donacion FROM donacion
            WHERE codigo_trazabilidad LIKE 'IT-%'
            ORDER BY id_donacion DESC LIMIT 20
            """).query(Long.class).list();
        assertFalse(donations.isEmpty(),"An existing IT-* fixture is required; this test never creates one");
        for (long id:donations) {
            var lineIds=jdbc.sql("SELECT id_detalle_donacion FROM detalle_donacion WHERE id_donacion=:id")
                .param("id",id).query(Long.class).list();
            for (var row:repository.events(id)) {
                assertTrue(lineIds.contains(row.lineId()),"Projection must stay within the requested donation");
            }
        }
    }
}
