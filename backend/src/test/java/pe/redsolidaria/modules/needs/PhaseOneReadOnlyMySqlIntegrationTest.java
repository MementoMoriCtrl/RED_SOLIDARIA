package pe.redsolidaria.modules.needs;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.donations.persistence.DonationLineRepository;
import pe.redsolidaria.modules.needs.persistence.NeedRepository;
import pe.redsolidaria.test.ControlledTestMySqlCondition;

// SELECT-only verification: never applies Flyway or creates/updates schema or records.
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@ExtendWith(ControlledTestMySqlCondition.class)
@Transactional(readOnly = true)
class PhaseOneReadOnlyMySqlIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @Autowired NeedRepository needs;
    @Autowired DonationLineRepository donations;
    @Autowired DeliveryLineRepository deliveries;
    @Autowired JdbcTemplate jdbc;
    @Autowired pe.redsolidaria.modules.emergencies.persistence.EmergencyBalanceRepository balances;
    @Autowired pe.redsolidaria.modules.donations.persistence.ReceiptOperationRepository operations;

    @Test void validatesAppliedSchemaAndExecutesBalanceQueriesWithoutFixtures() {
        assertEquals("red_solidaria_test", jdbc.queryForObject("select database()", String.class));
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '1' and success = 1", Long.class) > 0);
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '2' and success = 1", Long.class) > 0);
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '3' and success = 1", Long.class) > 0);
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '4' and success = 1", Long.class) > 0);
        assertTrue(balances.balance(0L).isEmpty());
        assertTrue(operations.findByDonacion_IdAndOperacionId(0L, "00000000-0000-0000-0000-000000000000").isEmpty());
        assertNotNull(needs.findAllByZona_IdOrderByIdDesc(0L, PageRequest.of(0, 20)));
        assertNotNull(donations.findSources(0L, 0L, PageRequest.of(0, 20)));
        assertEquals(0, deliveries.assignedToNeed(0L).signum());
        assertEquals(0, deliveries.deliveredToNeed(0L).signum());
        assertEquals(0, deliveries.assignedToDonation(0L).signum());
        assertEquals(0, deliveries.assignedToPair(0L, 0L).signum());
        assertEquals(0, deliveries.deliveredToPair(0L, 0L).signum());
    }
}
