package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Test-only connection settings; never falls back to application datasource defaults. */
@ExtendWith(StrictTestMySqlSupport.DedicatedDatabaseCondition.class)
public abstract class StrictTestMySqlSupport {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/red_solidaria_test";
    @Autowired private JdbcTemplate destinationCheck;

    @DynamicPropertySource
    static void dedicatedDatabase(DynamicPropertyRegistry registry) {
        requireEnvironment();
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    protected final void verifyTestDatabase() {
        assertEquals("red_solidaria_test", destinationCheck.queryForObject("SELECT DATABASE()", String.class));
    }

    private static boolean configured() {
        String username = System.getenv("TEST_DB_USERNAME");
        String password = System.getenv("TEST_DB_PASSWORD");
        return URL.equals(System.getenv("TEST_DB_URL"))
                && username != null && !username.isBlank() && !"root".equalsIgnoreCase(username.trim())
                && password != null && !password.isBlank();
    }

    private static void requireEnvironment() {
        if (!configured()) throw new IllegalStateException("Requires exact local red_solidaria_test URL and non-root TEST_DB_* credentials");
    }

    public static final class DedicatedDatabaseCondition implements ExecutionCondition {
        @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return configured() ? ConditionEvaluationResult.enabled("Dedicated test database configured")
                    : ConditionEvaluationResult.disabled("Requires TEST_DB_URL exactly jdbc:mysql://127.0.0.1:3306/red_solidaria_test and non-root TEST_DB_USERNAME / TEST_DB_PASSWORD");
        }
    }
}
