package pe.redsolidaria.modules.reports;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;

/** Existing rows only. All physical connections are verified and guarded before JPA can use them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false" })
@Import(ReportsReadOnlyMySqlIntegrationTest.ReadOnlyConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("red_solidaria_test")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ReportsReadOnlyMySqlIntegrationTest {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/red_solidaria_test";
    @Autowired JdbcTemplate jdbc;
    @Autowired DeliveryReportRepository deliveries;
    @Autowired DonationRepository donations;
    @Autowired EmergencyRepository emergencies;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        requireSettings();
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "rs_test");
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    private static void requireSettings() {
        String password = System.getenv("TEST_DB_PASSWORD");
        if (!URL.equals(System.getenv("TEST_DB_URL")) || !"rs_test".equals(System.getenv("TEST_DB_USERNAME"))
                || password == null || password.isBlank())
            throw new IllegalStateException("Blocked: exclusive local test URL and rs_test credentials required");
    }

    @Test void deliveryProjectionAndDateBoundariesMatchSql() {
        var sample = jdbc.query("""
            SELECT e.fecha_hora FROM detalle_entrega de JOIN entrega e ON e.id_entrega=de.id_entrega
            WHERE de.cantidad_entregada>0 ORDER BY e.fecha_hora DESC,de.id_detalle_entrega DESC LIMIT 1
            """, (rs, row) -> rs.getTimestamp(1).toLocalDateTime());
        assumeTrue(!sample.isEmpty(), "PENDIENTE: no hay entregas positivas para validar proyección y fechas");
        var from = sample.getFirst(); var to = from.plusNanos(1000);
        var expected = jdbc.query("""
            SELECT de.id_detalle_entrega,e.fecha_hora,em.nombre,z.nombre,n.id_necesidad,r.nombre,u.codigo,
                   d.codigo_trazabilidad,de.cantidad_asignada,de.cantidad_entregada
            FROM detalle_entrega de JOIN entrega e ON e.id_entrega=de.id_entrega
            JOIN necesidad n ON n.id_necesidad=de.id_necesidad JOIN zona_afectada z ON z.id_zona_afectada=n.id_zona_afectada
            JOIN emergencia em ON em.id_emergencia=z.id_emergencia JOIN recurso r ON r.id_recurso=n.id_recurso
            JOIN unidad_medida u ON u.id_unidad_medida=r.id_unidad_medida
            JOIN detalle_donacion dd ON dd.id_detalle_donacion=de.id_detalle_donacion
            JOIN donacion d ON d.id_donacion=dd.id_donacion
            WHERE de.cantidad_entregada>0 AND e.fecha_hora>=? AND e.fecha_hora<?
            ORDER BY e.fecha_hora DESC,de.id_detalle_entrega DESC LIMIT 2
            """, (rs, row) -> new DeliveryReportItem(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getBigDecimal(9), rs.getBigDecimal(10)), from, to);
        var actual = deliveries.findDeliveries(from, to, PageRequest.of(0, 2));
        assertFalse(expected.isEmpty(), "La referencia debe incluir la fecha elegida");
        assertEquals(expected.size(), actual.getContent().size());
        for (int i = 0; i < expected.size(); i++) {
            var a = actual.getContent().get(i); var e = expected.get(i);
            assertEquals(e.id(), a.id()); assertEquals(e.fecha(), a.fecha()); assertEquals(e.emergencia(), a.emergencia());
            assertEquals(e.zona(), a.zona()); assertEquals(e.necesidadId(), a.necesidadId());
            assertEquals(e.recurso(), a.recurso()); assertEquals(e.unidad(), a.unidad());
            assertEquals(e.codigoDonacion(), a.codigoDonacion());
            assertEquals(0, e.cantidadAsignada().compareTo(a.cantidadAsignada()));
            assertEquals(0, e.cantidadEntregada().compareTo(a.cantidadEntregada()));
            assertTrue(a.cantidadEntregada().signum() > 0);
        }
        assertEquals(0, deliveries.findDeliveries(from, from, PageRequest.of(0, 1)).getTotalElements());
    }

    @Test void deliveryPaginationAndCountMatchSql() {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM detalle_entrega WHERE cantidad_entregada>0", Long.class);
        assumeTrue(count >= 2, "PENDIENTE: se requieren dos entregas positivas para comprobar dos páginas");
        var expected = jdbc.query("""
            SELECT de.id_detalle_entrega FROM detalle_entrega de JOIN entrega e ON e.id_entrega=de.id_entrega
            WHERE de.cantidad_entregada>0 ORDER BY e.fecha_hora DESC,de.id_detalle_entrega DESC LIMIT 2
            """, (rs, row) -> rs.getLong(1));
        var first = deliveries.findDeliveries(null, null, PageRequest.of(0, 1));
        var second = deliveries.findDeliveries(null, null, PageRequest.of(1, 1));
        assertEquals(count, first.getTotalElements()); assertEquals(count, second.getTotalElements());
        assertEquals(expected.get(0), first.getContent().getFirst().id());
        assertEquals(expected.get(1), second.getContent().getFirst().id());
    }

    @Test void donationFiltersAndDateBoundariesMatchSql() {
        var sample = jdbc.query("SELECT codigo_trazabilidad,estado,fecha_registro FROM donacion ORDER BY id_donacion DESC LIMIT 1",
                (rs, row) -> new DonationSample(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()));
        assumeTrue(!sample.isEmpty(), "PENDIENTE: no hay donaciones para validar filtros y fechas");
        var s = sample.getFirst(); var end = s.fecha().plusNanos(1000);
        var expected = jdbc.query("""
            SELECT id_donacion FROM donacion WHERE LOWER(codigo_trazabilidad) LIKE LOWER(CONCAT('%',?,'%'))
              AND UPPER(estado)=UPPER(?) AND fecha_registro>=? AND fecha_registro<?
            ORDER BY fecha_registro DESC,id_donacion DESC
            """, (rs, row) -> rs.getLong(1), s.codigo(), s.estado(), s.fecha(), end);
        var actual = donations.findAdministrativeListByDate(s.codigo(), s.estado(), s.fecha(), end);
        assertFalse(expected.isEmpty()); assertEquals(expected, actual.stream().map(d -> d.getId()).toList());
        assertTrue(donations.findAdministrativeListByDate(s.codigo(), s.estado(), s.fecha(), s.fecha()).isEmpty());
        var allStates = jdbc.query("""
            SELECT id_donacion FROM donacion WHERE LOWER(codigo_trazabilidad) LIKE LOWER(CONCAT('%',?,'%'))
            ORDER BY fecha_registro DESC,id_donacion DESC
            """, (rs, row) -> rs.getLong(1), s.codigo());
        assertEquals(allStates, donations.findAdministrativeListByDate(s.codigo(), null, null, null).stream().map(d -> d.getId()).toList());
    }

    @Test void emergencySecondPageMatchesSqlWhenAvailable() {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM emergencia", Long.class);
        assumeTrue(count >= 2, "PENDIENTE: se requieren dos emergencias para comprobar dos páginas");
        var ids = jdbc.query("SELECT id_emergencia FROM emergencia ORDER BY fecha_inicio DESC,id_emergencia DESC LIMIT 2",
                (rs, row) -> rs.getLong(1));
        var order = Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"));
        var first = emergencies.searchByDate(null, null, null, null, PageRequest.of(0, 1, order));
        var second = emergencies.searchByDate(null, null, null, null, PageRequest.of(1, 1, order));
        assertEquals(count, first.getTotalElements()); assertEquals(count, second.getTotalElements());
        assertEquals(ids.get(0), first.getContent().getFirst().getId());
        assertEquals(ids.get(1), second.getContent().getFirst().getId());
    }

    @Test void donationStateFilteringWhenDifferentStatesExist() {
        var states = jdbc.query("SELECT DISTINCT estado FROM donacion ORDER BY estado LIMIT 2", (rs, row) -> rs.getString(1));
        assumeTrue(states.size() >= 2, "PENDIENTE: se requieren dos estados de donación para comprobar exclusión por estado");
        String state = states.getFirst();
        var ids = jdbc.query("SELECT id_donacion FROM donacion WHERE UPPER(estado)=UPPER(?) ORDER BY fecha_registro DESC,id_donacion DESC",
                (rs, row) -> rs.getLong(1), state);
        assertEquals(ids, donations.findAdministrativeListByDate(null, state, null, null).stream().map(d -> d.getId()).toList());
        assertTrue(donations.findAdministrativeListByDate(null, state, null, null).stream()
                .allMatch(d -> state.equalsIgnoreCase(d.getEstado())));
    }

    @Test void emergencyStateFilteringWhenDifferentStatesExist() {
        var states = jdbc.query("SELECT DISTINCT estado FROM emergencia ORDER BY estado LIMIT 2", (rs, row) -> rs.getString(1));
        assumeTrue(states.size() >= 2, "PENDIENTE: se requieren dos estados de emergencia para comprobar exclusión por estado");
        String state = states.getFirst();
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM emergencia WHERE UPPER(estado)=UPPER(?)", Long.class, state);
        var expected = jdbc.query("SELECT id_emergencia FROM emergencia WHERE UPPER(estado)=UPPER(?) ORDER BY fecha_inicio DESC,id_emergencia DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), state);
        var order = Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"));
        var actual = emergencies.searchByDate(null, state, null, null, PageRequest.of(0, 1, order));
        assertEquals(count, actual.getTotalElements());
        assertEquals(expected, actual.getContent().stream().map(e -> e.getId()).toList());
        assertTrue(actual.getContent().stream().allMatch(e -> state.equalsIgnoreCase(e.getEstado())));
    }

    @Test void emergencyFiltersDatesAndPaginationMatchSql() {
        var sample = jdbc.query("SELECT nombre,estado,fecha_inicio FROM emergencia ORDER BY id_emergencia DESC LIMIT 1",
                (rs, row) -> new EmergencySample(rs.getString(1), rs.getString(2), rs.getDate(3).toLocalDate()));
        assumeTrue(!sample.isEmpty(), "PENDIENTE: no hay emergencias para validar filtros y fechas");
        var s = sample.getFirst();
        long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM emergencia WHERE (LOWER(nombre) LIKE LOWER(CONCAT('%',?,'%'))
              OR LOWER(tipo) LIKE LOWER(CONCAT('%',?,'%'))) AND UPPER(estado)=UPPER(?) AND fecha_inicio>=? AND fecha_inicio<=?
            """, Long.class, s.nombre(), s.nombre(), s.estado(), s.fecha(), s.fecha());
        var expected = jdbc.query("""
            SELECT id_emergencia FROM emergencia WHERE (LOWER(nombre) LIKE LOWER(CONCAT('%',?,'%'))
              OR LOWER(tipo) LIKE LOWER(CONCAT('%',?,'%'))) AND UPPER(estado)=UPPER(?) AND fecha_inicio>=? AND fecha_inicio<=?
            ORDER BY fecha_inicio DESC,id_emergencia DESC LIMIT 1
            """, (rs, row) -> rs.getLong(1), s.nombre(), s.nombre(), s.estado(), s.fecha(), s.fecha());
        var order = Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"));
        var actual = emergencies.searchByDate(s.nombre(), s.estado(), s.fecha(), s.fecha(), PageRequest.of(0, 1, order));
        assertTrue(count > 0); assertEquals(count, actual.getTotalElements());
        assertEquals(expected, actual.getContent().stream().map(e -> e.getId()).toList());
    }

    @Test void deliveryZeroQuantityExclusionWhenExisting() {
        long zero = jdbc.queryForObject("SELECT COUNT(*) FROM detalle_entrega WHERE cantidad_entregada=0", Long.class);
        assumeTrue(zero > 0, "PENDIENTE: no hay detalles con entrega cero para comprobar exclusión");
        var rows = jdbc.query("""
                SELECT de.id_detalle_entrega,e.fecha_hora FROM detalle_entrega de
                JOIN entrega e ON e.id_entrega=de.id_entrega WHERE de.cantidad_entregada=0
                ORDER BY de.id_detalle_entrega LIMIT 1
                """, (rs, row) -> new ZeroDelivery(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime()));
        var selected = rows.getFirst();
        var page = deliveries.findDeliveries(selected.fecha(), selected.fecha().plusNanos(1000), PageRequest.of(0, 1));
        long matching = jdbc.queryForObject("""
                SELECT COUNT(*) FROM detalle_entrega de JOIN entrega e ON e.id_entrega=de.id_entrega
                WHERE de.cantidad_entregada>0 AND e.fecha_hora>=? AND e.fecha_hora<?
                """, Long.class, selected.fecha(), selected.fecha().plusNanos(1000));
        assertEquals(matching, page.getTotalElements());
        for (int index = 0; index < page.getTotalPages(); index++)
            assertNotEquals(selected.id(), deliveries.findDeliveries(selected.fecha(), selected.fecha().plusNanos(1000),
                    PageRequest.of(index, 1)).getContent().getFirst().id());
        long total = deliveries.findDeliveries(null, null, PageRequest.of(0, 1)).getTotalElements();
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM detalle_entrega WHERE cantidad_entregada>0", Long.class).longValue(), total);
    }

    record DonationSample(String codigo, String estado, LocalDateTime fecha) {}
    record EmergencySample(String nombre, String estado, LocalDate fecha) {}
    record ZeroDelivery(long id, LocalDateTime fecha) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class ReadOnlyConfiguration {
        @Bean @Primary DataSource reportsDataSource() { return new VerifiedReadOnlyDataSource(); }
        @Bean HibernatePropertiesCustomizer reportsHibernateSafety() {
            return properties -> {
                for (String key : properties.keySet()) {
                    String normalized = key.toLowerCase(java.util.Locale.ROOT);
                    if (normalized.startsWith("hibernate.connection.") || normalized.startsWith("hibernate.hikari.")
                            || normalized.startsWith("hibernate.c3p0.") || normalized.startsWith("jakarta.persistence.jdbc.")
                            || normalized.startsWith("javax.persistence.jdbc.") || normalized.contains("datasource")
                            || normalized.contains("multi_tenant_connection_provider") || normalized.contains("schema-generation"))
                        throw new IllegalStateException("Blocked: alternate Hibernate connection or schema generation setting");
                }
                properties.put("hibernate.hbm2ddl.auto", "validate");
            };
        }
    }

    /** No pool, alternate credentials, unwrapping, DDL or mutating statement is exposed. */
    static final class VerifiedReadOnlyDataSource extends AbstractDataSource {
        @Override public Connection getConnection() throws SQLException {
            requireSettings();
            Connection raw = DriverManager.getConnection(URL, "rs_test", System.getenv("TEST_DB_PASSWORD"));
            try {
                if (!URL.equals(raw.getMetaData().getURL())) throw new SQLException("Unexpected physical JDBC URL");
                try (Statement s = raw.createStatement(); ResultSet r = s.executeQuery("SELECT DATABASE(), CURRENT_USER(), USER()")) {
                    if (!r.next() || !"red_solidaria_test".equals(r.getString(1)) || !"rs_test@127.0.0.1".equals(r.getString(2))
                            || r.getString(3) == null || !"rs_test".equals(r.getString(3).split("@", 2)[0]))
                        throw new SQLException("Unexpected physical database identity");
                }
                raw.setReadOnly(true);
                if (!raw.isReadOnly()) throw new SQLException("Read-only connection required");
                try (Statement s = raw.createStatement(); ResultSet r = s.executeQuery("SELECT @@session.transaction_read_only")) {
                    if (!r.next() || r.getInt(1) != 1) throw new SQLException("Server-side read-only mode required");
                }
                return connectionProxy(raw);
            } catch (SQLException | RuntimeException failure) { raw.close(); throw failure; }
        }
        @Override public Connection getConnection(String username, String password) throws SQLException { throw new SQLException("Alternate credentials forbidden"); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("Unwrapping forbidden"); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
    }

    private static Connection connectionProxy(Connection raw) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("unwrap") || name.equals("prepareCall") || name.equals("setCatalog") || name.equals("setSchema"))
                throw new SQLException("Connection operation forbidden in read-only test");
            if (name.equals("isWrapperFor")) return false;
            if (name.equals("setReadOnly") && Boolean.FALSE.equals(args[0])) return null;
            if (name.equals("prepareStatement")) requireSelect((String) args[0]);
            Object value = invoke(raw, method, args);
            if (value instanceof PreparedStatement prepared) return statementProxy(prepared, PreparedStatement.class, (Connection) proxy);
            if (value instanceof Statement statement) return statementProxy(statement, Statement.class, (Connection) proxy);
            if (value instanceof DatabaseMetaData metadata) return metadataProxy(metadata, (Connection) proxy);
            return value;
        });
    }
    private static Object statementProxy(Statement raw, Class<?> type, Connection connection) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("unwrap") || name.equals("addBatch") || name.equals("executeBatch")
                    || name.equals("executeLargeBatch") || name.equals("executeUpdate") || name.equals("executeLargeUpdate"))
                throw new SQLException("Writing or unwrapping statements is forbidden");
            if (name.equals("isWrapperFor")) return false;
            if (name.equals("getConnection")) return connection;
            if ((name.equals("execute") || name.equals("executeQuery")) && args != null && args.length > 0 && args[0] instanceof String sql)
                requireSelect(sql);
            Object value = invoke(raw, method, args);
            return value instanceof ResultSet result ? resultSetProxy(result, (Statement) proxy) : value;
        });
    }
    private static DatabaseMetaData metadataProxy(DatabaseMetaData raw, Connection connection) {
        return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, (proxy, method, args) -> {
            if (method.getName().equals("getConnection")) return connection;
            if (method.getName().equals("unwrap")) throw new SQLException("Unwrapping forbidden");
            if (method.getName().equals("isWrapperFor")) return false;
            Object value = invoke(raw, method, args);
            return value instanceof ResultSet result ? resultSetProxy(result, null) : value;
        });
    }
    private static ResultSet resultSetProxy(ResultSet raw, Statement statement) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("getStatement")) return statement;
            if (method.getName().equals("unwrap")) throw new SQLException("Unwrapping forbidden");
            if (method.getName().equals("isWrapperFor")) return false;
            if (method.getName().startsWith("update") || method.getName().equals("insertRow")
                    || method.getName().equals("deleteRow") || method.getName().equals("moveToInsertRow"))
                throw new SQLException("Result set writes forbidden");
            return invoke(raw, method, args);
        });
    }
    private static void requireSelect(String sql) throws SQLException {
        String normalized = sql.stripLeading().replaceFirst("(?s)^/\\*.*?\\*/\\s*", "").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("(?s)^select\\b.*") || normalized.contains(";")
                || normalized.matches("(?s).*\\b(for\\s+update|for\\s+share|into\\s+outfile|into\\s+dumpfile|lock\\s+in\\s+share\\s+mode)\\b.*"))
            throw new SQLException("Only non-locking SELECT statements are allowed");
    }
    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
