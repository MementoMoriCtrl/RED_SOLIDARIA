package pe.redsolidaria.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BootstrapDatabaseGuardTest {
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metadata;
    private BootstrapDatabaseGuard guard;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("red_solidaria");
        when(connection.isValid(3)).thenReturn(true);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        guard = new BootstrapDatabaseGuard(dataSource);
    }

    @Test
    void acceptsOnlyTheExpectedLocalSchema() throws Exception {
        when(metadata.getURL()).thenReturn("jdbc:mysql://localhost:3306/red_solidaria");
        assertDoesNotThrow(guard::verify);
    }

    @Test
    void rejectsOtherSchemaRemoteHostAndNonMysqlCatalog() throws Exception {
        when(metadata.getURL()).thenReturn("jdbc:mysql://localhost:3306/another_database");
        assertThrows(IllegalStateException.class, guard::verify);

        when(metadata.getURL()).thenReturn("jdbc:mysql://db.example.net:3306/red_solidaria");
        assertThrows(IllegalStateException.class, guard::verify);

        when(metadata.getURL()).thenReturn("jdbc:mysql://localhost:3306/red_solidaria?password=hidden");
        assertThrows(IllegalStateException.class, guard::verify);

        when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/red_solidaria");
        when(connection.getCatalog()).thenReturn("other_schema");
        assertThrows(IllegalStateException.class, guard::verify);
    }
}
