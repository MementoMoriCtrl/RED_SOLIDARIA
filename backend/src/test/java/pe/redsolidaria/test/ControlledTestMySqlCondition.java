package pe.redsolidaria.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.*;

public class ControlledTestMySqlCondition implements ExecutionCondition {
    static final String URL="jdbc:mysql://127.0.0.1:3306/red_solidaria_test";
    record Settings(String url,String username,String password) {
        @Override public String toString() { return "Settings[red_solidaria_test, rs_test, password=REDACTED]"; }
    }
    @FunctionalInterface interface Connector { Connection open(Settings settings) throws SQLException; }

    @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        requireVerifiedEnvironment();
        return ConditionEvaluationResult.enabled("Exact TEST_DB_* configured; real datasource identity checked before fixtures");
    }
    // Fail rather than report a successful Maven run with silently omitted safety checks.
    public static void requireVerifiedEnvironment() {
        settings(System.getenv());
    }
    static Settings settings(Map<String,String> environment) {
        String password=environment.get("TEST_DB_PASSWORD");
        if (!URL.equals(environment.get("TEST_DB_URL")) || !"rs_test".equals(environment.get("TEST_DB_USERNAME"))
                || password==null || password.isBlank())
            throw new IllegalStateException("Blocked: exact TEST_DB_URL, TEST_DB_USERNAME=rs_test and TEST_DB_PASSWORD required; no fallback");
        return new Settings(URL,"rs_test",password);
    }
    static void verifyEnvironment(Map<String,String> environment,Connector connector) {
        Settings settings=settings(environment);
        try (Connection connection=connector.open(settings)) {
            connection.setReadOnly(true);
            verifyIdentity(connection);
        } catch (SQLException failure) { throw sanitized(failure); }
    }
    public static void verifyDataSource(DataSource source) {
        if (source==null) throw new IllegalStateException("Missing test datasource");
        try (Connection connection=source.getConnection()) { verifyIdentity(connection); }
        catch (SQLException failure) { throw sanitized(failure); }
    }
    private static void verifyIdentity(Connection connection) throws SQLException {
        // Also validate the physical pool URL, not only the environment or selected catalog.
        if (!URL.equals(connection.getMetaData().getURL())) throw new IllegalStateException("Blocked: unexpected JDBC destination");
        try (var statement=connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var row=statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), USER()")) {
                if (!row.next() || !"red_solidaria_test".equals(row.getString(1))
                        || !"rs_test@127.0.0.1".equals(row.getString(2))
                        || row.getString(3)==null || !"rs_test".equals(row.getString(3).split("@",2)[0]))
                    throw new IllegalStateException("Blocked: requires DATABASE()=red_solidaria_test and CURRENT_USER()=rs_test@127.0.0.1");
            }
        }
    }
    private static IllegalStateException sanitized(SQLException failure) {
        return new IllegalStateException("Test identity verification failed (SQLState="+failure.getSQLState()+", code="+failure.getErrorCode()+")");
    }
}
