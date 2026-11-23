package pe.redsolidaria.modules.logistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.test.ControlledTestMySqlCondition;
import tools.jackson.databind.json.JsonMapper;

/** Integration tests deliberately use one rollback-only transaction per test; no fixture cleanup or commits. */
@SpringBootTest
@Import(TransferResolutionMySqlIntegrationTest.DedicatedDatabase.class)
@ExtendWith(ControlledTestMySqlCondition.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("red_solidaria_test")
@Isolated("Rollback-only synthetic resolution tests cannot overlap other test classes")
@Transactional
class TransferResolutionMySqlIntegrationTest {
    private static final String URL="jdbc:mysql://127.0.0.1:3306/red_solidaria_test";
    private static final Map<String,String> FIXTURE_KEYS=Map.ofEntries(
        Map.entry("rol","id_rol"),Map.entry("usuario","id_usuario"),Map.entry("donante","id_donante"),
        Map.entry("centro_acopio","id_centro_acopio"),Map.entry("centro_distribucion","id_centro_distribucion"),
        Map.entry("unidad_medida","id_unidad_medida"),Map.entry("recurso","id_recurso"),
        Map.entry("donacion","id_donacion"),Map.entry("detalle_donacion","id_detalle_donacion"));
    private static final Set<String> WRITE_TABLES=Set.of("rol","usuario","donante","centro_acopio",
        "centro_distribucion","unidad_medida","recurso","donacion","detalle_donacion",
        "traslado_recurso","resolucion_traslado","registro_auditoria");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        ControlledTestMySqlCondition.requireVerifiedEnvironment();
        registry.add("spring.datasource.url",() -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username",() -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password",() -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled",() -> "false");
        registry.add("spring.sql.init.mode",() -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto",() -> "validate");
        registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action",() -> "none");
        registry.add("spring.transaction.default-timeout",() -> "10s");
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class DedicatedDatabase {
        @Bean(destroyMethod="close") VerifiedDataSource dataSource() { return new VerifiedDataSource(); }
        @Bean HibernatePropertiesCustomizer restrictHibernate() {
            return properties -> {
                for (String key:properties.keySet()) if (key.startsWith("hibernate.connection.")
                        || key.startsWith("hibernate.hikari.") || key.startsWith("hibernate.c3p0.")
                        || key.startsWith("jakarta.persistence.jdbc.") || key.startsWith("javax.persistence.jdbc.")
                        || key.endsWith("DataSource") || key.endsWith("schema-generation-connection")
                        || key.endsWith("schema-generation.connection") || key.equals("hibernate.multi_tenant_connection_provider"))
                    throw new IllegalStateException("Alternative Hibernate connection configuration forbidden: "+key);
                properties.remove("jakarta.persistence.schema-generation.database.action");
                properties.remove("javax.persistence.schema-generation.database.action");
                properties.put("hibernate.hbm2ddl.auto","validate");
                properties.put("jakarta.persistence.schema-generation.scripts.action","none");
            };
        }
    }
    /** Explicit pool; every physical checkout verifies URL, catalog and both identities before use. */
    static final class VerifiedDataSource extends AbstractDataSource implements AutoCloseable {
        private final HikariDataSource pool;
        VerifiedDataSource() {
            ControlledTestMySqlCondition.requireVerifiedEnvironment();
            pool=new HikariDataSource();
            pool.setJdbcUrl(URL); pool.setUsername("rs_test"); pool.setPassword(System.getenv("TEST_DB_PASSWORD"));
            pool.setDriverClassName("com.mysql.cj.jdbc.Driver"); pool.setMinimumIdle(0); pool.setMaximumPoolSize(4);
            pool.setConnectionTimeout(5000); pool.addDataSourceProperty("socketTimeout","10000");
        }
        @Override public Connection getConnection() throws SQLException {
            ControlledTestMySqlCondition.requireVerifiedEnvironment();
            Connection connection=pool.getConnection();
            try {
                if (!URL.equals(connection.getMetaData().getURL())) throw new SQLException("Unexpected JDBC URL");
                try (var statement=connection.createStatement()) {
                    statement.setQueryTimeout(5);
                    try (var result=statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), USER()")) {
                        if (!result.next() || !"red_solidaria_test".equals(result.getString(1))
                                || !"rs_test@127.0.0.1".equals(result.getString(2)) || result.getString(3)==null
                                || !"rs_test".equals(result.getString(3).split("@",2)[0]))
                            throw new SQLException("Unexpected test database identity");
                    }
                }
                return connection;
            } catch (SQLException | RuntimeException failure) { connection.close(); throw failure; }
        }
        @Override public Connection getConnection(String user,String password) { throw new IllegalStateException("Explicit credentials are forbidden"); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("Datasource unwrapping is forbidden"); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
        @Override public void close() { pool.close(); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TransferResolutionService service;
    @Autowired TransferResolutionRepository resolutions;
    @Autowired LogisticsRepository logistics;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JsonMapper json;
    private String transfer,other,inTransit,email,otherEmail,operation;
    private long actor,donation,line;
    private final List<String> observedOperations=new ArrayList<>();
    private final Map<String,List<Long>> fixtureIds=new LinkedHashMap<>();
    private final List<String> fixtureTransfers=new ArrayList<>();

    @BeforeEach void fixtures() {
        ControlledTestMySqlCondition.verifyDataSource(dataSource);
        assertEquals(6L,jdbc.queryForObject("SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6') AND success=1",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Long.class));
        for (String table:WRITE_TABLES) {
            assertEquals(1L,count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=? AND table_type='BASE TABLE' AND engine='InnoDB'",table),
                "La tabla requerida "+table+" no existe o no utiliza InnoDB; se detiene antes de crear fixtures");
        }
        String token=UUID.randomUUID().toString().replace("-",""); email=token+"@integration.test";
        long role=insert("rol","INSERT INTO rol(codigo,nombre) VALUES (?,?)","IT-"+token,"Synthetic role");
        actor=insert("usuario","INSERT INTO usuario(id_rol,nombre,apellido,correo,password_hash,estado) VALUES (?,?,?,?,?,?)",role,"Test","Actor",email,"not-a-login","ACTIVO");
        otherEmail="other-"+email;
        insert("usuario","INSERT INTO usuario(id_rol,nombre,apellido,correo,password_hash,estado) VALUES (?,?,?,?,?,?)",role,"Other","Actor",otherEmail,"not-a-login","ACTIVO");
        long donor=insert("donante","INSERT INTO donante(nombre) VALUES (?)","IT-"+token);
        long origin=insert("centro_acopio","INSERT INTO centro_acopio(nombre) VALUES (?)","IT-"+token);
        long destination=insert("centro_distribucion","INSERT INTO centro_distribucion(nombre) VALUES (?)","IT-"+token);
        long unit=insert("unidad_medida","INSERT INTO unidad_medida(codigo,nombre) VALUES (?,?)",token.substring(0,30),"Test unit");
        long resource=insert("recurso","INSERT INTO recurso(id_unidad_medida,nombre,categoria,estado) VALUES (?,?,?,?)",unit,"IT-"+token,"TEST","ACTIVO");
        donation=insert("donacion","INSERT INTO donacion(id_donante,id_centro_acopio,id_usuario_registro,codigo_trazabilidad,estado) VALUES (?,?,?,?,?)",donor,origin,actor,"IT-"+token,"RECIBIDA");
        line=insert("detalle_donacion","INSERT INTO detalle_donacion(id_donacion,id_recurso,cantidad_donada,cantidad_recibida_acopio) VALUES (?,?,?,?)",donation,resource,new BigDecimal("100.000"),new BigDecimal("100.000"));
        transfer=shipment(origin,destination,unit,true); other=shipment(origin,destination,unit,true); inTransit=shipment(origin,destination,unit,false);
        operation=UUID.randomUUID().toString(); observedOperations.add(operation);
        assertEquals(0L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",operation));
    }
    private long insert(String table,String sql,Object... values) {
        if (!FIXTURE_KEYS.containsKey(table)) throw new IllegalArgumentException("Tabla de fixture no admitida");
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); return statement; },keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();
        fixtureIds.computeIfAbsent(table,ignored -> new ArrayList<>()).add(id);
        return id;
    }
    private String shipment(long origin,long destination,long unit,boolean received) {
        String id=UUID.randomUUID().toString(); String hash="a".repeat(64);
        if (received) jdbc.update("INSERT INTO traslado_recurso(operacion_id,id_detalle_donacion,id_centro_acopio,id_centro_distribucion,id_unidad_medida,cantidad_enviada,cantidad_recibida,id_usuario_salida,id_usuario_recepcion,fecha_recepcion,estado,huella_salida,operacion_recepcion,huella_recepcion) VALUES (?,?,?,?,?,10,8,?,?,CURRENT_TIMESTAMP(6),'RECIBIDO_CON_DIFERENCIA',?,?,?)",id,line,origin,destination,unit,actor,actor,hash,UUID.randomUUID().toString(),hash);
        else jdbc.update("INSERT INTO traslado_recurso(operacion_id,id_detalle_donacion,id_centro_acopio,id_centro_distribucion,id_unidad_medida,cantidad_enviada,id_usuario_salida,estado,huella_salida) VALUES (?,?,?,?,?,10,?,'EN_TRANSITO',?)",id,line,origin,destination,unit,actor,hash);
        fixtureTransfers.add(id);
        return id;
    }
    private ResolutionDtos.Request request(String id,String reason) { return new ResolutionDtos.Request(UUID.fromString(id),"PERDIDA",reason,"Acta de prueba"); }
    private long count(String sql,Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
    private long auditCount(String id) { return count("SELECT COUNT(*) FROM registro_auditoria WHERE entidad='TRASLADO' AND id_registro=? AND accion='RESOLUCION_DIFERENCIA'",id); }
    private int status(Runnable action) { return assertThrows(ResponseStatusException.class,action::run).getStatusCode().value(); }

    @Test void recordsSnapshotAuditAndIdenticalReplayWithoutChangingHistoricalStock() {
        var before=jdbc.queryForMap("SELECT cantidad_enviada,cantidad_recibida,estado FROM traslado_recurso WHERE operacion_id=?",transfer);
        var stockBefore=jdbc.queryForMap("SELECT cantidad_recibida_acopio FROM detalle_donacion WHERE id_detalle_donacion=?",line);
        var balanceBefore=logistics.stock(0).stream().filter(row -> row.detalleDonacionId()==line).findFirst().orElseThrow();
        long movements=count("SELECT COUNT(*) FROM movimiento_donacion WHERE id_donacion=?",donation);
        long reservations=count("SELECT COUNT(*) FROM detalle_entrega WHERE id_detalle_donacion=?",line);
        var first=service.resolve(transfer,request(operation,"Faltante"),email);
        var persisted=jdbc.queryForMap("SELECT * FROM resolucion_traslado WHERE operacion_id=?",operation);
        assertEquals(transfer,persisted.get("traslado_id")); assertEquals(actor,((Number)persisted.get("id_usuario")).longValue());
        assertNotNull(persisted.get("fecha_resolucion")); assertEquals("PERDIDA",persisted.get("tipo"));
        assertEquals("Faltante",persisted.get("motivo")); assertEquals("Acta de prueba",persisted.get("evidencia"));
        assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)persisted.get("cantidad_enviada")));
        assertEquals(0,new BigDecimal("8").compareTo((BigDecimal)persisted.get("cantidad_recibida")));
        assertEquals(0,new BigDecimal("2").compareTo((BigDecimal)persisted.get("diferencia")));
        assertEquals(first.operacionId(),service.resolve(transfer,request(operation,"Faltante"),email).operacionId());
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE traslado_id=?",transfer)); assertEquals(1L,auditCount(transfer));
        assertEquals(1L,count("SELECT COUNT(*) FROM registro_auditoria WHERE entidad='TRASLADO' AND id_registro=? AND accion='RESOLUCION_DIFERENCIA' AND id_usuario=? AND JSON_UNQUOTE(JSON_EXTRACT(datos_nuevos,'$.operacionId'))=?",transfer,actor,operation));
        assertEquals(before,jdbc.queryForMap("SELECT cantidad_enviada,cantidad_recibida,estado FROM traslado_recurso WHERE operacion_id=?",transfer));
        assertEquals(stockBefore,jdbc.queryForMap("SELECT cantidad_recibida_acopio FROM detalle_donacion WHERE id_detalle_donacion=?",line));
        assertEquals(balanceBefore,logistics.stock(0).stream().filter(row -> row.detalleDonacionId()==line).findFirst().orElseThrow());
        assertEquals(movements,count("SELECT COUNT(*) FROM movimiento_donacion WHERE id_donacion=?",donation));
        assertEquals(reservations,count("SELECT COUNT(*) FROM detalle_entrega WHERE id_detalle_donacion=?",line));
    }
    @Test void rejectsDifferentContentSecondResolutionAndUuidAcrossTransfers() {
        service.resolve(transfer,request(operation,"Faltante"),email);
        assertEquals(409,status(() -> service.resolve(transfer,request(operation,"Otro motivo"),email)));
        assertEquals(409,status(() -> service.resolve(transfer,request(operation,"Faltante"),otherEmail)));
        assertEquals(409,status(() -> service.resolve(transfer,request(UUID.randomUUID().toString(),"Faltante"),email)));
        assertEquals(409,status(() -> service.resolve(other,request(operation,"Faltante"),email)));
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",operation));
        assertEquals(1L,auditCount(transfer)); assertEquals(0L,auditCount(other));
    }
    @Test void rejectsTransferInTransit() {
        assertEquals(409,status(() -> service.resolve(inTransit,request(operation,"Faltante"),email)));
        assertEquals(0L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",operation)); assertEquals(0L,auditCount(inTransit));
    }
    @Test void databaseRejectsInvalidReferencesChecksAndDuplicateKeys() {
        service.resolve(transfer,request(operation,"Faltante"),email);
        String sql="INSERT INTO resolucion_traslado(operacion_id,traslado_id,id_usuario,fecha_resolucion,tipo,motivo,cantidad_enviada,cantidad_recibida,diferencia) VALUES (?,?,?,CURRENT_TIMESTAMP(6),'OTRO','Prueba',?,?,?)";
        assertMysqlError(1062,() -> jdbc.update(sql,operation,other,actor,10,8,2));
        assertMysqlError(1062,() -> jdbc.update(sql,UUID.randomUUID().toString(),transfer,actor,10,8,2));
        String missingTransfer=UUID.randomUUID().toString();
        assertEquals(0L,count("SELECT COUNT(*) FROM traslado_recurso WHERE operacion_id=?",missingTransfer));
        assertEquals(1L,count("SELECT COUNT(*) FROM usuario WHERE id_usuario=?",actor));
        assertForeignKeyError("fk_resolucion_traslado","traslado_id","traslado_recurso","operacion_id",
            () -> jdbc.update(sql,UUID.randomUUID().toString(),missingTransfer,actor,10,8,2));
        long absent=Long.MAX_VALUE; assertEquals(0L,count("SELECT COUNT(*) FROM usuario WHERE id_usuario=?",absent));
        assertEquals(1L,count("SELECT COUNT(*) FROM traslado_recurso WHERE operacion_id=?",other));
        assertForeignKeyError("fk_resolucion_usuario","id_usuario","usuario","id_usuario",
            () -> jdbc.update(sql,UUID.randomUUID().toString(),other,absent,10,8,2));
        assertMysqlError(3819,() -> jdbc.update(sql,UUID.randomUUID().toString(),other,actor,10,8,1));
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",operation));
    }
    private void assertMysqlError(int expected,Runnable action) {
        var error=assertThrows(DataAccessException.class,action::run);
        Throwable cause=error;
        while (cause!=null && !(cause instanceof SQLException)) cause=cause.getCause();
        assertNotNull(cause,"Expected an SQL exception"); assertEquals(expected,((SQLException)cause).getErrorCode());
    }
    private void assertForeignKeyError(String constraint,String column,String parent,String parentColumn,Runnable action) {
        assertEquals(1L,count("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name='resolucion_traslado' AND constraint_name=? AND column_name=? AND referenced_table_name=? AND referenced_column_name=?",constraint,column,parent,parentColumn));
        var error=assertThrows(DataAccessException.class,action::run);
        Throwable cause=error;
        while (cause!=null && !(cause instanceof SQLException)) cause=cause.getCause();
        assertNotNull(cause,"Expected an SQL exception");
        var sqlError=(SQLException)cause;
        assertEquals("23000",sqlError.getSQLState());
        assertTrue(sqlError.getErrorCode()==1216 || sqlError.getErrorCode()==1452,"Expected a missing-parent foreign key error");
        // MySQL 1216 can suppress constraint details; 1452 includes the concrete FK name.
        if (sqlError.getErrorCode()==1452) assertTrue(sqlError.getMessage().contains(constraint),"Unexpected foreign key constraint");
    }
    @Test void auditFailureMarksWholeTransactionForRollback() {
        AuditRecordRepository failingAudit=mock(AuditRecordRepository.class);
        when(failingAudit.saveAndFlush(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
        var failingService=new TransferResolutionService(logistics,resolutions,users,failingAudit,json);
        var tx=new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class,() -> tx.executeWithoutResult(status -> failingService.resolve(transfer,request(operation,"Faltante"),email)));
        // The template joined the test transaction and marked it rollback-only; @AfterTransaction checks actual absence.
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",operation));
        assertEquals(0L,auditCount(transfer));
    }
    @AfterTransaction void noFixtureResolutionSurvivesRollback() {
        ControlledTestMySqlCondition.verifyDataSource(dataSource);
        for (String id:observedOperations) assertEquals(0L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",id));
        for (String id:fixtureTransfers) {
            assertEquals(0L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE traslado_id=?",id),"Resolución conservada para "+id);
            assertEquals(0L,auditCount(id),"Auditoría conservada para "+id);
            assertEquals(0L,count("SELECT COUNT(*) FROM traslado_recurso WHERE operacion_id=?",id),"Traslado conservado: "+id);
        }
        for (long id:fixtureIds.getOrDefault("usuario",List.of()))
            assertEquals(0L,count("SELECT COUNT(*) FROM registro_auditoria WHERE id_usuario=?",id),"Auditoría conservada para usuario sintético "+id);
        for (long id:fixtureIds.getOrDefault("rol",List.of()))
            assertEquals(0L,count("SELECT COUNT(*) FROM rol_permiso WHERE id_rol=?",id),"Relación conservada para rol sintético "+id);
        fixtureIds.forEach((table,ids) -> {
            String key=FIXTURE_KEYS.get(table);
            for (long id:ids) assertEquals(0L,count("SELECT COUNT(*) FROM "+table+" WHERE "+key+"=?",id),
                "Fixture conservado: "+table+" / "+id);
        });
    }
    // Future independent-connection concurrency tests require committed fixtures and explicit separate authorization.
    // This class deliberately has no worker threads, REQUIRES_NEW transactions, commit, or cleanup.
}
