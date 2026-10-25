package pe.redsolidaria.test;

import org.junit.jupiter.api.extension.*;

public class ControlledTestMySqlCondition implements ExecutionCondition {
    @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String url = System.getenv("TEST_DB_URL");
        boolean enabled = url != null && url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]{1,5})?/red_solidaria_test")
                && present("TEST_DB_USERNAME") && present("TEST_DB_PASSWORD");
        return enabled ? ConditionEvaluationResult.enabled("Dedicated local test database configured")
                : ConditionEvaluationResult.disabled("Requires TEST_DB_URL targeting local red_solidaria_test (no URL options), TEST_DB_USERNAME and TEST_DB_PASSWORD; never falls back to DB_* ");
    }
    private static boolean present(String key) { return System.getenv(key) != null && !System.getenv(key).isBlank(); }
}
