package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ControlledTestMySqlConditionTest {
    Map<String,String> valid() { return new HashMap<>(Map.of("TEST_DB_URL",ControlledTestMySqlCondition.URL,
        "TEST_DB_USERNAME","rs_test","TEST_DB_PASSWORD","test-secret-never-log")); }
    Connection identity(String url,String database,String account,String login) throws Exception {
        Connection c=mock(Connection.class); DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        Statement s=mock(Statement.class); ResultSet r=mock(ResultSet.class);
        when(c.getMetaData()).thenReturn(metadata); when(metadata.getURL()).thenReturn(url);
        when(c.createStatement()).thenReturn(s); when(s.executeQuery("SELECT DATABASE(), CURRENT_USER(), USER()")).thenReturn(r);
        when(r.next()).thenReturn(true); when(r.getString(1)).thenReturn(database);
        when(r.getString(2)).thenReturn(account); when(r.getString(3)).thenReturn(login);
        return c;
    }
    @ParameterizedTest @ValueSource(strings={"root","ROOT","rs_test@127.0.0.1","other"," rs_test",""})
    void rejectsEveryUsernameExceptTheExactTestUserBeforeConnecting(String username) throws Exception {
        var env=valid(); env.put("TEST_DB_USERNAME",username);
        var connector=mock(ControlledTestMySqlCondition.Connector.class);
        assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.verifyEnvironment(env,connector));
        verifyNoInteractions(connector);
    }
    @ParameterizedTest @ValueSource(strings={"jdbc:mysql://localhost:3306/red_solidaria_test",
        "jdbc:mysql://127.0.0.1:3306/red_solidaria","jdbc:mysql://127.0.0.1:3306/red_solidaria_restore_check",
        "jdbc:mysql://127.0.0.1:3307/red_solidaria_test","jdbc:mysql://127.0.0.1:3306/red_solidaria_test?user=root"})
    void rejectsAnyDifferentUrlBeforeConnecting(String url) throws Exception {
        var env=valid(); env.put("TEST_DB_URL",url);
        var connector=mock(ControlledTestMySqlCondition.Connector.class);
        assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.verifyEnvironment(env,connector));
        verifyNoInteractions(connector);
    }
    @Test void neverFallsBackToProductionVariablesOrMissingPassword() {
        assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.settings(Map.of(
            "DB_URL",ControlledTestMySqlCondition.URL,"DB_USERNAME","rs_test","DB_PASSWORD","test-secret-never-log")));
        var env=valid(); env.remove("TEST_DB_PASSWORD");
        assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.settings(env));
        env.put("TEST_DB_PASSWORD","  "); assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.settings(env));
    }
    @Test void verifiesThePhysicalConnectionUsingOnlyASelectAndClosesIt() throws Exception {
        var c=identity(ControlledTestMySqlCondition.URL,"red_solidaria_test","rs_test@127.0.0.1","rs_test@localhost");
        ControlledTestMySqlCondition.verifyEnvironment(valid(),s -> c);
        verify(c).setReadOnly(true); verify(c).close();
        verify(c.createStatement()).setQueryTimeout(5);
        verify(c.createStatement()).executeQuery("SELECT DATABASE(), CURRENT_USER(), USER()");
        verify(c.createStatement(),never()).executeUpdate(anyString());
        assertFalse(ControlledTestMySqlCondition.settings(valid()).toString().contains("test-secret-never-log"));
    }
    @ParameterizedTest @ValueSource(strings={"root@127.0.0.1","rs_test@localhost","rs_test@%","other@127.0.0.1"})
    void rejectsUnexpectedAuthenticatedAccounts(String account) throws Exception {
        var c=identity(ControlledTestMySqlCondition.URL,"red_solidaria_test",account,"rs_test@localhost");
        assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.verifyEnvironment(valid(),s -> c));
        verify(c).close();
    }
    @Test void rejectsWrongDatabasePhysicalUrlAndLoginAndSuppressesDriverSecrets() throws Exception {
        for (var values:List.of(new String[]{ControlledTestMySqlCondition.URL,"red_solidaria","rs_test@localhost"},
                new String[]{"jdbc:mysql://127.0.0.1:3306/red_solidaria","red_solidaria_test","rs_test@localhost"},
                new String[]{ControlledTestMySqlCondition.URL,"red_solidaria_test","root@localhost"})) {
            var c=identity(values[0],values[1],"rs_test@127.0.0.1",values[2]);
            assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.verifyEnvironment(valid(),s -> c));
        }
        var failure=assertThrows(IllegalStateException.class,() -> ControlledTestMySqlCondition.verifyEnvironment(valid(),s -> {
            throw new SQLException("test-secret-never-log","28000",1045);
        }));
        assertFalse(failure.toString().contains("test-secret-never-log")); assertNull(failure.getCause());
    }
}
