package pe.redsolidaria.modules.logistics;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.test.ControlledTestMySqlCondition;

/**
 * DEJA REGISTROS PERMANENTES EN red_solidaria_test. Sin limpieza automática.
 * Cada escenario confirma sus propios fixtures sintéticos: rol, usuario, donante,
 * centro_acopio, centro_distribucion, unidad_medida, recurso, donacion,
 * detalle_donacion y dos traslado_recurso. La operación ganadora confirma además
 * una resolucion_traslado y una fila de registro_auditoria. El manifiesto contiene
 * los identificadores para su inspección; un fallo puede dejar un subconjunto.
 */
@SpringBootTest
@Import(TransferResolutionMySqlIntegrationTest.DedicatedDatabase.class)
@ExtendWith(ControlledTestMySqlCondition.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("red_solidaria_test")
@Isolated("Committed concurrency fixtures must not overlap other JUnit test classes")
class TransferResolutionConcurrencyMySqlTest {
    private static volatile boolean unsafeWorkers;
    private static final Set<String> TABLES=Set.of("rol","usuario","donante","centro_acopio","centro_distribucion",
        "unidad_medida","recurso","donacion","detalle_donacion","traslado_recurso","resolucion_traslado","registro_auditoria");
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
        registry.add("spring.transaction.default-timeout",() -> "15s");
        registry.add("spring.jdbc.template.query-timeout",() -> "10s");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TransferResolutionService service;
    @Autowired TransferResolutionRepository resolutions;
    private final Map<String,Long> ids=new LinkedHashMap<>();
    private String scenario,email,first,second;
    private volatile int fixtureOutcome=TransactionSynchronization.STATUS_UNKNOWN;

    @BeforeEach void createIndependentFixtures(TestInfo info) {
        if (unsafeWorkers) throw new IllegalStateException("Previous concurrency workers did not terminate; inspect the test database");
        scenario=info.getTestMethod().orElseThrow().getName()+"-"+UUID.randomUUID();
        ControlledTestMySqlCondition.verifyDataSource(dataSource);
        assertEquals(6L,count("SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6') AND success=1"));
        assertEquals(0L,count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
        for (String table:TABLES) assertEquals(1L,count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=? AND table_type='BASE TABLE' AND engine='InnoDB'",table),"Missing InnoDB table "+table);
        // Fail before fixture writes if the independent observer cannot inspect lock-wait metadata.
        jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID=w.REQUESTING_THREAD_ID JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID",Long.class);
        String token=UUID.randomUUID().toString().replace("-",""); email=token+"@integration.test";
        first=UUID.randomUUID().toString(); second=UUID.randomUUID().toString();
        System.out.println("CONCURRENCY_SCENARIO "+scenario+" fixture_commit_planned=true transfer1="+first+" transfer2="+second);
        try {
            var setup=new TransactionTemplate(transactions); setup.setTimeout(15);
            setup.executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int outcome) { fixtureOutcome=outcome; }
                });
                long role=insert("rol","INSERT INTO rol(codigo,nombre) VALUES (?,?)","IT-"+token,"Concurrent fixture");
                long actor=insert("usuario","INSERT INTO usuario(id_rol,nombre,apellido,correo,password_hash,estado) VALUES (?,?,?,?,?,?)",role,"Concurrent","Fixture",email,"not-a-login","ACTIVO");
                long donor=insert("donante","INSERT INTO donante(nombre) VALUES (?)","IT-"+token);
                long origin=insert("centro_acopio","INSERT INTO centro_acopio(nombre) VALUES (?)","IT-"+token);
                long destination=insert("centro_distribucion","INSERT INTO centro_distribucion(nombre) VALUES (?)","IT-"+token);
                long unit=insert("unidad_medida","INSERT INTO unidad_medida(codigo,nombre) VALUES (?,?)",token.substring(0,30),"Test unit");
                long resource=insert("recurso","INSERT INTO recurso(id_unidad_medida,nombre,categoria,estado) VALUES (?,?,?,?)",unit,"IT-"+token,"TEST","ACTIVO");
                long donation=insert("donacion","INSERT INTO donacion(id_donante,id_centro_acopio,id_usuario_registro,codigo_trazabilidad,estado) VALUES (?,?,?,?,?)",donor,origin,actor,"IT-"+token,"RECIBIDA");
                long line=insert("detalle_donacion","INSERT INTO detalle_donacion(id_donacion,id_recurso,cantidad_donada,cantidad_recibida_acopio) VALUES (?,?,100,100)",donation,resource);
                shipment(first,line,origin,destination,unit,actor); shipment(second,line,origin,destination,unit,actor);
            });
        } finally {
            String outcome=switch(fixtureOutcome) {
                case TransactionSynchronization.STATUS_COMMITTED -> "COMMIT_CONFIRMED";
                case TransactionSynchronization.STATUS_ROLLED_BACK -> "ROLLBACK_CONFIRMED";
                default -> "UNKNOWN_VERIFY_DATABASE_BEFORE_RETRY";
            };
            if (fixtureOutcome==TransactionSynchronization.STATUS_UNKNOWN) unsafeWorkers=true;
            System.out.println("CONCURRENCY_FIXTURES "+scenario+" outcome="+outcome+" ids="+ids+" transfers="+first+","+second+"; no automatic cleanup");
        }
        if (fixtureOutcome!=TransactionSynchronization.STATUS_COMMITTED)
            throw new IllegalStateException("Fixture transaction was not confirmed committed; inspect outcome before retry");
    }
    private long insert(String label,String sql,Object... values) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); return statement; },keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue(); ids.put(label,id); return id;
    }
    private void shipment(String id,long line,long origin,long destination,long unit,long actor) {
        String hash="a".repeat(64);
        jdbc.update("INSERT INTO traslado_recurso(operacion_id,id_detalle_donacion,id_centro_acopio,id_centro_distribucion,id_unidad_medida,cantidad_enviada,cantidad_recibida,id_usuario_salida,id_usuario_recepcion,fecha_recepcion,estado,huella_salida,operacion_recepcion,huella_recepcion) VALUES (?,?,?,?,?,10,8,?,?,CURRENT_TIMESTAMP(6),'RECIBIDO_CON_DIFERENCIA',?,?,?)",id,line,origin,destination,unit,actor,actor,hash,UUID.randomUUID().toString(),hash);
    }
    private long count(String sql,Object... values) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,values)); }
    private ResolutionDtos.Request request(UUID uuid,String reason) { return new ResolutionDtos.Request(uuid,"PERDIDA",reason,"Acta de concurrencia"); }
    private record Outcome(int status,String operation,long connectionId) {}
    private record Pair(Outcome first,Outcome second) {}

    /** One worker obtains the real row lock before the other begins the service call. */
    private Pair raceSameTransfer(ResolutionDtos.Request one,ResolutionDtos.Request two) throws Exception {
        var locked=new CountDownLatch(1); var secondReady=new CountDownLatch(1); var release=new CountDownLatch(1);
        var ownerConnection=new AtomicLong(-1); var waitingConnection=new AtomicLong(-1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> a=pool.submit(() -> inTransaction(first,one,() -> {
                jdbc.queryForObject("SELECT operacion_id FROM traslado_recurso WHERE operacion_id=? FOR UPDATE",String.class,first);
                ownerConnection.set(Objects.requireNonNull(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class)));
                locked.countDown(); await(release);
            }));
            Future<Outcome> b=pool.submit(() -> { await(locked); return inTransaction(first,two,() -> {
                waitingConnection.set(Objects.requireNonNull(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class)));
                secondReady.countDown();
            }); });
            assertTrue(secondReady.await(8,TimeUnit.SECONDS),"Second transaction did not start");
            try { requireObservedLockWait(waitingConnection.get(),ownerConnection.get(),b); }
            finally { release.countDown(); }
            Outcome x=a.get(15,TimeUnit.SECONDS),y=b.get(15,TimeUnit.SECONDS);
            assertNotEquals(x.connectionId(),y.connectionId(),"Distinct MySQL sessions required"); return new Pair(x,y);
        } finally { release.countDown(); stop(pool); }
    }
    private void requireObservedLockWait(long waiting,long owner,Future<Outcome> worker) throws Exception {
        assertTrue(waiting>0 && owner>0 && waiting!=owner,"Independent observed sessions required");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        String sql="SELECT COUNT(*) FROM performance_schema.data_lock_waits w "
            +"JOIN performance_schema.threads r ON r.THREAD_ID=w.REQUESTING_THREAD_ID "
            +"JOIN performance_schema.threads b ON b.THREAD_ID=w.BLOCKING_THREAD_ID "
            +"JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID "
            +"WHERE r.PROCESSLIST_ID=? AND b.PROCESSLIST_ID=? AND l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='traslado_recurso' AND l.LOCK_TYPE='RECORD'";
        while (System.nanoTime()<deadline) {
            if (count(sql,waiting,owner)>0) return;
            if (worker.isDone()) throw new AssertionError("Second request completed before MySQL reported the expected lock wait");
            Thread.sleep(25);
        }
        throw new AssertionError("MySQL did not expose the required row-lock wait; check performance_schema privileges and instrumentation");
    }
    /** Both transactions hold distinct transfer locks before either proceeds. */
    private Pair raceDifferentTransfers(ResolutionDtos.Request one,ResolutionDtos.Request two) throws Exception {
        var ready=new CyclicBarrier(2); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> a=pool.submit(() -> inTransaction(first,one,() -> lockAndWait(first,ready)));
            Future<Outcome> b=pool.submit(() -> inTransaction(second,two,() -> lockAndWait(second,ready)));
            Outcome x=a.get(20,TimeUnit.SECONDS),y=b.get(20,TimeUnit.SECONDS);
            assertNotEquals(x.connectionId(),y.connectionId(),"Distinct MySQL sessions required"); return new Pair(x,y);
        } finally { stop(pool); }
    }
    private void lockAndWait(String transfer,CyclicBarrier barrier) {
        jdbc.queryForObject("SELECT operacion_id FROM traslado_recurso WHERE operacion_id=? FOR UPDATE",String.class,transfer);
        try { barrier.await(8,TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException("Concurrency barrier failed",e); }
    }
    private Outcome inTransaction(String transfer,ResolutionDtos.Request request,Runnable beforeService) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(15); final long[] connection={-1};
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                assertEquals("red_solidaria_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
                assertEquals("rs_test@127.0.0.1",jdbc.queryForObject("SELECT CURRENT_USER()",String.class));
                assertEquals("rs_test",jdbc.queryForObject("SELECT SUBSTRING_INDEX(USER(),'@',1)",String.class));
                connection[0]=Objects.requireNonNull(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));
                beforeService.run(); var result=service.resolve(transfer,request,email);
                return new Outcome(200,result.operacionId(),connection[0]);
            }));
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode().value()!=409) throw conflict;
            return new Outcome(409,null,connection[0]);
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(8,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted",e); }
    }
    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        try { if (!pool.awaitTermination(20,TimeUnit.SECONDS)) { unsafeWorkers=true; throw new IllegalStateException("Workers did not terminate; inspect test database before continuing"); } }
        catch (InterruptedException e) { unsafeWorkers=true; Thread.currentThread().interrupt(); throw e; }
    }
    private void verifyFinal(String transfer,int expected) {
        assertEquals(expected,count("SELECT COUNT(*) FROM resolucion_traslado WHERE traslado_id=?",transfer));
        assertEquals(expected,count("SELECT COUNT(*) FROM registro_auditoria WHERE entidad='TRASLADO' AND id_registro=? AND accion='RESOLUCION_DIFERENCIA'",transfer));
        if (expected==1) assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado r JOIN registro_auditoria a ON a.id_registro=r.traslado_id AND a.id_usuario=r.id_usuario AND JSON_UNQUOTE(JSON_EXTRACT(a.datos_nuevos,'$.operacionId'))=r.operacion_id WHERE r.traslado_id=? AND a.entidad='TRASLADO' AND a.accion='RESOLUCION_DIFERENCIA'",transfer));
    }
    @Test void identicalConcurrentRequestsReturnIdempotentResult() throws Exception {
        UUID operation=UUID.randomUUID(); var request=request(operation,"Mismo motivo");
        Pair result=raceSameTransfer(request,request);
        assertEquals(200,result.first().status()); assertEquals(200,result.second().status());
        assertEquals(operation.toString(),result.first().operation()); assertEquals(result.first().operation(),result.second().operation());
        verifyFinal(first,1); verifyFinal(second,0);
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=? AND traslado_id=?",operation.toString(),first));
    }
    @Test void differentConcurrentRequestsForSameTransferConflict() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(); Pair result=raceSameTransfer(request(a,"Primero"),request(b,"Segundo"));
        assertEquals(200,result.first().status()); assertEquals(409,result.second().status());
        verifyFinal(first,1); verifyFinal(second,0);
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=? AND traslado_id=?",a.toString(),first));
        assertEquals(0L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=?",b.toString()));
    }
    @Test void simultaneousUuidReuseOnDifferentTransfersNeverOverwrites() throws Exception {
        UUID operation=UUID.randomUUID();
        var checked=new CyclicBarrier(2); var absentChecks=new AtomicInteger();
        if (!(resolutions instanceof Advised proxy)) throw new IllegalStateException("Expected an interceptable Spring Data proxy");
        MethodInterceptor interceptor=invocation -> {
            Object result=invocation.proceed();
            if (invocation.getMethod().getName().equals("existsById") && invocation.getArguments().length==1
                    && operation.toString().equals(invocation.getArguments()[0])) {
                boolean exists=(Boolean)result;
                if (exists) throw new AssertionError("UUID already existed before the synchronized insert");
                absentChecks.incrementAndGet();
                try { checked.await(8,TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException("UUID pre-insert barrier failed",e); }
            }
            return result;
        };
        Pair result;
        proxy.addAdvice(0,interceptor);
        try { result=raceDifferentTransfers(request(operation,"Primero"),request(operation,"Segundo")); }
        finally { if (!proxy.removeAdvice(interceptor)) throw new IllegalStateException("Could not remove UUID test interceptor"); }
        assertEquals(2,absentChecks.get(),"Both requests must observe an absent UUID before either inserts");
        assertEquals(Set.of(200,409),Set.of(result.first().status(),result.second().status()));
        String winner=result.first().status()==200 ? first : second; String loser=winner.equals(first) ? second : first;
        verifyFinal(winner,1); verifyFinal(loser,0);
        assertEquals(1L,count("SELECT COUNT(*) FROM resolucion_traslado WHERE operacion_id=? AND traslado_id=?",operation.toString(),winner));
        assertEquals(winner.equals(first) ? "Primero" : "Segundo",jdbc.queryForObject("SELECT motivo FROM resolucion_traslado WHERE operacion_id=?",String.class,operation.toString()));
    }
    @AfterEach void reportPersistentRecords() {
        if (fixtureOutcome!=TransactionSynchronization.STATUS_COMMITTED) return;
        if (unsafeWorkers) {
            System.out.println("CONCURRENCY_RETAINED "+scenario+" workers_unconfirmed=true; verify database before retry; fixtures="+ids+" transfers="+first+","+second);
            return;
        }
        System.out.println("CONCURRENCY_RETAINED "+scenario+" fixtures="+ids+" transfers="+first+","+second);
        for (String id:List.of(first,second)) {
            System.out.println("CONCURRENCY_RETAINED_TRANSFER "+id+" resolutions="+
                jdbc.queryForList("SELECT operacion_id FROM resolucion_traslado WHERE traslado_id=?",String.class,id)+" audits="+
                jdbc.queryForList("SELECT id_registro_auditoria FROM registro_auditoria WHERE entidad='TRASLADO' AND id_registro=? AND accion='RESOLUCION_DIFERENCIA'",Long.class,id));
        }
    }
}
