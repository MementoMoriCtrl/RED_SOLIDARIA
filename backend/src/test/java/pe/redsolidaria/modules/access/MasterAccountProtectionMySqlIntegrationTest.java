package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP/service/JPA/MySQL tests. Retains only a new synthetic actor and its audit evidence.
 * Never changes existing accounts, roles, permissions or business data, even for fixture setup.
 * The interceptor only observes real transaction completion and pauses the real locking query.
 */
@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.sql.init.mode=never"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ExtendWith(MasterAccountProtectionMySqlIntegrationTest.DedicatedCredentials.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MasterAccountProtectionMySqlIntegrationTest {
    static boolean configured() {
        String url=System.getenv("TEST_DB_URL");
        return url!=null && url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):3306/red_solidaria_test")
            && "rs_test".equals(System.getenv("TEST_DB_USERNAME"))
            && System.getenv("TEST_DB_PASSWORD")!=null && !System.getenv("TEST_DB_PASSWORD").isBlank();
    }
    public static final class DedicatedCredentials implements ExecutionCondition {
        @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return configured() ? ConditionEvaluationResult.enabled("Explicit rs_test credentials")
                : ConditionEvaluationResult.disabled("Requires local red_solidaria_test on port 3306 and TEST_DB_USERNAME=rs_test / TEST_DB_PASSWORD; no fallback");
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        if (!configured()) throw new IllegalStateException("Missing exclusive rs_test TEST_DB_* configuration");
        // Preflight before the application context, Hibernate validation or fixture writes.
        try (var c=DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USERNAME"),System.getenv("TEST_DB_PASSWORD")); var s=c.createStatement()) {
            s.setQueryTimeout(5);
            try (var r=s.executeQuery("SELECT DATABASE(), SUBSTRING_INDEX(CURRENT_USER(),'@',1), SUBSTRING_INDEX(USER(),'@',1)")) {
                assertTrue(r.next()); assertEquals("red_solidaria_test",r.getString(1));
                assertEquals("rs_test",r.getString(2)); assertEquals("rs_test",r.getString(3));
            }
            try (var r=s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5') AND success=1")) {
                assertTrue(r.next()); assertEquals(5,r.getInt(1),"Existing V1-V5 required; no migrations will be run");
            }
            try (var r=s.executeQuery("SELECT COUNT(*) FROM usuario WHERE admin_maestro=1 AND estado='ACTIVO'")) {
                assertTrue(r.next()); assertEquals(1,r.getInt(1),"An existing active test master is required; do not create or repair it");
            }
        }
        registry.add("spring.datasource.url",() -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username",() -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password",() -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled",() -> "false");
        registry.add("spring.sql.init.mode",() -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        registry.add("spring.jpa.open-in-view",() -> "false");
        registry.add("spring.jpa.properties.jakarta.persistence.query.timeout",() -> "5000");
        registry.add("spring.datasource.hikari.maximum-pool-size",() -> "4");
        registry.add("spring.datasource.hikari.connection-timeout",() -> "5000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource source;
    @Autowired JsonMapper json;
    final Queue<Integer> completions=new ConcurrentLinkedQueue<>();
    final String run=UUID.randomUUID().toString();
    volatile Gate gate;
    long masterId,roleId,actorId;
    String email,password;
    Map<String,String> baseline;
    MethodInterceptor observer;
    boolean fixtureReady;

    @BeforeAll void prepare() throws Exception {
        assertEquals("red_solidaria_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals("rs_test",jdbc.queryForObject("SELECT SUBSTRING_INDEX(CURRENT_USER(),'@',1)",String.class));
        var master=jdbc.queryForMap("SELECT id_usuario,id_rol FROM usuario WHERE admin_maestro=1 AND estado='ACTIVO'");
        masterId=((Number)master.get("id_usuario")).longValue(); roleId=((Number)master.get("id_rol")).longValue();
        assertEssentialState();
        assertInstanceOf(Advised.class,users,"A real instrumentable Spring Data repository is required");
        email="master-protection-"+run+"@example.test";
        password=UUID.randomUUID()+"-IntegrationOnly";
        String encoded=encoder.encode(password);
        actorId=Objects.requireNonNull(new TransactionTemplate(manager).execute(status -> {
            var masterAccount=users.findById(masterId).orElseThrow();
            return users.saveAndFlush(new UserAccount(masterAccount.getRol(),"Integration","Protection",email,encoded,"ACTIVO")).getId();
        }));
        baseline=snapshot(); fixtureReady=true;
        observer=invocation -> {
            if (!invocation.getMethod().getName().equals("findMarkedMasterForUpdate")) return invocation.proceed();
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completions.add(status); }
            });
            Gate active=gate;
            int call=active==null ? 0 : active.calls.incrementAndGet();
            if (call==2) active.secondAttempt.countDown();
            Object result=invocation.proceed(); // Actual Hibernate SELECT ... FOR UPDATE against MySQL.
            if (call==1) {
                active.locked.countDown();
                assertTrue(active.release.await(10,TimeUnit.SECONDS),"Controlled holder was not released within ten seconds");
            }
            return result;
        };
        // Must precede Spring Data's terminal query interceptor; appending it would not observe queries.
        ((Advised)users).addAdvice(0,observer);
        System.out.printf("MASTER_PROTECTION_SETUP database=red_solidaria_test mysql_user=rs_test run=%s actorId=%d masterId=%d%n",run,actorId,masterId);
    }
    @BeforeEach void resetObservation() { completions.clear(); }

    @Test @Order(1) void auditSurvivesTheRejectedApiTransactionRollback() throws Exception {
        assertEquals(403,call("state"));
        assertEquals(List.of(TransactionSynchronization.STATUS_ROLLED_BACK),List.copyOf(completions));
        verifyAudit("PATCH","/api/admin/usuarios/"+masterId+"/estado",1);
        assertEssentialState(); assertEquals(baseline,snapshot());
    }

    static Stream<Arguments> competingChanges() {
        return Stream.of(Arguments.of("state","state"),Arguments.of("role","role"),Arguments.of("permissions","permissions"),
            Arguments.of("state","role"),Arguments.of("role","permissions"),Arguments.of("permissions","state"));
    }
    @ParameterizedTest @MethodSource("competingChanges") @Order(2)
    void commonMysqlLockSerializesCompetingApiTransactions(String first,String second) throws Exception {
        Gate current=new Gate(); gate=current;
        ExecutorService executor=Executors.newFixedThreadPool(2);
        Future<Integer> holder=null,waiter=null;
        long started=System.nanoTime();
        int before=auditCount();
        try {
            holder=executor.submit(() -> call(first));
            assertTrue(current.locked.await(8,TimeUnit.SECONDS),"First request did not acquire the real master lock");
            waiter=executor.submit(() -> call(second));
            assertTrue(current.secondAttempt.await(8,TimeUnit.SECONDS),"Second request did not reach the locking query");
            Future<Integer> pending=waiter;
            assertThrows(TimeoutException.class,() -> pending.get(350,TimeUnit.MILLISECONDS),
                "Second locking query completed while the first transaction still held the row");
            current.release.countDown();
            assertEquals(403,holder.get(8,TimeUnit.SECONDS));
            assertEquals(403,waiter.get(8,TimeUnit.SECONDS));
            assertEquals(2,completions.size());
            assertTrue(completions.stream().allMatch(s -> s==TransactionSynchronization.STATUS_ROLLED_BACK));
            assertEquals(before+2,auditCount());
            assertEssentialState(); assertEquals(baseline,snapshot());
            System.out.printf("MASTER_PROTECTION_CONCURRENCY first=%s second=%s blockedAtLeastMs=350 completedMs=%d rollbacks=2 newAudits=2%n",
                first,second,Duration.ofNanos(System.nanoTime()-started).toMillis());
        } finally {
            current.release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(15,TimeUnit.SECONDS)) {
                if (holder!=null) holder.cancel(true);
                if (waiter!=null) waiter.cancel(true);
                executor.shutdownNow();
                fail("Worker did not finish: investigate lock timeout/deadlock; no cleanup SQL was issued");
            }
            gate=null;
        }
    }

    @Test @Order(3) void finalIntegrityAndReleasedLockAreVerified() throws Exception {
        assertEssentialState(); assertEquals(baseline,snapshot());
        assertEquals(13,auditCount(),"One audit test plus two rejections for each of six races");
        // Reacquire the same real lock in a fresh transaction, then roll back without updates.
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                assertTrue(users.findMarkedMasterForUpdate().isPresent()); status.setRollbackOnly(); return true;
            })).get(8,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(8,TimeUnit.SECONDS)); }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        try (var connection=source.getConnection()) { assertTrue(connection.getAutoCommit()); }
        if (source instanceof com.zaxxer.hikari.HikariDataSource pool)
            assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());
        System.out.printf("MASTER_PROTECTION_INTEGRITY unchangedTables=%d auditRows=%d masterActive=true essentials=true lockReacquired=true%n",baseline.size(),auditCount());
    }

    @AfterAll void checkEvenAfterFailure() throws Exception {
        if (gate!=null) gate.release.countDown();
        if (observer!=null) ((Advised)users).removeAdvice(observer);
        if (fixtureReady) {
            assertEssentialState(); assertEquals(baseline,snapshot(),"Existing data changed during integration checks");
            System.out.printf("MASTER_PROTECTION_RETAINED actorId=%d auditRows=%d noCleanupOrMigration=true%n",actorId,auditCount());
        }
    }
    private int call(String operation) throws Exception {
        String method,path,body;
        switch(operation) {
            case "state" -> { method="PATCH"; path="/api/admin/usuarios/"+masterId+"/estado"; body="{\"estado\":\"INACTIVO\"}"; }
            case "role" -> { method="PUT"; path="/api/admin/usuarios/"+masterId+"/rol"; body="{\"rolId\":"+roleId+"}"; }
            case "permissions" -> { method="PUT"; path="/api/admin/roles/"+roleId+"/permisos"; body="{\"permisoIds\":[]}"; }
            default -> throw new IllegalArgumentException("Unknown controlled operation");
        }
        var login=mvc.perform(post("/api/auth/login").with(csrf()).param("correo",email).param("password",password)).andReturn();
        assertEquals(204,login.getResponse().getStatus());
        var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
        return mvc.perform(request(HttpMethod.valueOf(method),path).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
    private int auditCount() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id_usuario=? AND accion='OPERACION_ACCESO_BLOQUEADA'",Integer.class,actorId));
    }
    private void verifyAudit(String method,String path,int expected) {
        var rows=jdbc.queryForList("SELECT id_registro_auditoria,id_usuario,entidad,id_registro,accion,fecha_hora,datos_nuevos FROM registro_auditoria WHERE id_usuario=? AND accion='OPERACION_ACCESO_BLOQUEADA' ORDER BY id_registro_auditoria",actorId);
        assertEquals(expected,rows.size());
        for (var row:rows) {
            assertEquals(actorId,((Number)row.get("id_usuario")).longValue()); assertEquals("ACCESO",row.get("entidad"));
            assertEquals(path,row.get("id_registro")); assertNotNull(row.get("fecha_hora"));
            var data=json.readTree(row.get("datos_nuevos").toString());
            assertEquals(4,data.size()); assertEquals(method,data.path("metodo").asText());
            assertEquals(path,data.path("ruta").asText()); assertEquals(403,data.path("estadoHttp").asInt());
            assertFalse(data.path("motivo").asText().isBlank());
            assertFalse(data.toString().contains(password)); assertFalse(data.has("password")); assertFalse(data.has("body"));
            System.out.printf("MASTER_PROTECTION_AUDIT id=%s actorId=%d method=%s path=%s http=403 date=%s reasonPresent=true requestBodyAbsent=true%n",
                row.get("id_registro_auditoria"),actorId,method,path,row.get("fecha_hora"));
        }
    }
    private void assertEssentialState() {
        var state=jdbc.queryForMap("SELECT id_rol,estado,admin_maestro FROM usuario WHERE id_usuario=?",masterId);
        assertEquals(roleId,((Number)state.get("id_rol")).longValue());
        assertEquals("ACTIVO",state.get("estado")); assertEquals(1,((Number)state.get("admin_maestro")).intValue());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT p.codigo) FROM rol_permiso rp JOIN permiso p ON p.id_permiso=rp.id_permiso WHERE rp.id_rol=? AND p.codigo IN ('ACCESO_ADMINISTRAR','DONACION_REGISTRAR')",Integer.class,roleId));
    }
    private Map<String,String> snapshot() throws Exception {
        // Digest values never leave the process as raw business/private data.
        var tables=jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME<>'registro_auditoria' ORDER BY TABLE_NAME",String.class);
        Map<String,String> result=new TreeMap<>();
        for (String table:tables) {
            assertTrue(table.matches("[a-zA-Z0-9_]+"));
            var keys=jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION",String.class,table);
            assertFalse(keys.isEmpty(),"Cannot safely compare a table without a primary key");
            assertTrue(keys.stream().allMatch(k -> k.matches("[a-zA-Z0-9_]+")));
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            jdbc.query("SELECT * FROM `"+table+"` ORDER BY "+String.join(",",keys.stream().map(k -> "`"+k+"`").toList()),
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    for (int i=1;i<=row.getMetaData().getColumnCount();i++) {
                        String value=row.getString(i);
                        byte[] bytes=value==null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                        digest.update(ByteBuffer.allocate(4).putInt(value==null ? -1 : bytes.length).array()); digest.update(bytes);
                    }
                });
            result.put(table,HexFormat.of().formatHex(digest.digest()));
        }
        return result;
    }
    private static final class Gate {
        final CountDownLatch locked=new CountDownLatch(1),secondAttempt=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicInteger calls=new AtomicInteger();
    }
}
