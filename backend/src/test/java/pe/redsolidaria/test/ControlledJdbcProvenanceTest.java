package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Fake generated-key backend, real Spring callbacks, no JDBC execution or Spring context. */
class ControlledJdbcProvenanceTest {
    static final class Harness implements AutoCloseable {
        final ControlledFixtureAuditTest fixture=new ControlledFixtureAuditTest();
        final ControlledTransactionCallbacksTest.MemoryManager manager=new ControlledTransactionCallbacksTest.MemoryManager();
        final IntegrityGate gate=new IntegrityGate();
        final DataSource datasource=mock(DataSource.class);
        Snapshot durable=fixture.source.snapshot();
        long nextGenerated=200;
        int inserts;
        final ControlledFixtureAudit audit=new ControlledFixtureAudit(new JdbcTemplate(datasource),manager,
            UUID.randomUUID().toString(),"ModelB.synthetic-history",gate,new Initialization(() -> {},() -> durable,() -> {},
                key -> fixture.source.snapshot().tables().get(key.table()).get(key.id()),this::generatedInsert));
        Harness(boolean multipleResources) {
            manager.commit=() -> durable=fixture.source.snapshot();
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                fixture.fixture(); fixture.base.forEach(key -> audit.register(key.table(),Long.parseLong(key.id())));
                Map<Long,Long> additional=new HashMap<>();
                if (multipleResources) {
                    long resource=audit.insertJdbcBase("insert into recurso(id_unidad_medida,nombre,categoria,estado) values (?,?,?,?)",105,"second","IT","ACTIVO");
                    long line=audit.insertJdbcBase("insert into detalle_donacion(id_donacion,id_recurso,cantidad_donada) values (?,?,?)",111,resource,10);
                    additional.put(line,resource);
                    assertEquals(Set.of(new Key("recurso","200"),new Key("detalle_donacion","201")),
                        ledger().keys(State.ASSIGNED).stream().filter(key -> key.id().equals("200") || key.id().equals("201")).collect(java.util.stream.Collectors.toSet()));
                }
                audit.authorizeRelations(new ScenarioRelations(111,102,112,106,105,109,110,Set.of(113L,114L),additional,103L));
            });
            audit.announce();
        }
        private Long generatedInsert(String sql,Object[] args) {
            inserts++; long id=nextGenerated++;
            if (sql.startsWith("insert into recurso(")) fixture.source.add("recurso",Long.toString(id),"id_unidad_medida",args[0].toString());
            else if (sql.startsWith("insert into detalle_donacion(")) fixture.source.add("detalle_donacion",Long.toString(id),"id_donacion",args[0].toString(),"id_recurso",args[1].toString());
            else if (sql.startsWith("insert into recepcion_operacion(")) fixture.source.add("recepcion_operacion",Long.toString(id),
                "id_donacion",args[0].toString(),"operacion_id",args[1].toString(),"huella",args[2].toString(),"respuesta",args[3].toString());
            else if (sql.startsWith("insert into movimiento_donacion(")) fixture.source.add("movimiento_donacion",Long.toString(id),
                "id_donacion",args[2].toString(),"id_usuario_responsable","103","tipo_evento",args[1].toString(),"observaciones",args[0].toString(),"estado_nuevo","REGISTRADA");
            else throw new AssertionError("Unexpected generated-key backend operation");
            return id;
        }
        long seed(UUID uuid,int version) { return audit.insertHistoricalReceipt(uuid,version,"a".repeat(64),response(111,106)); }
        FixtureLedger ledger() { return (FixtureLedger)ReflectionTestUtils.getField(audit,"ledger"); }
        @SuppressWarnings("unchecked") List<HistoricalReceiptSeed> seeds() {
            return (List<HistoricalReceiptSeed>)ReflectionTestUtils.getField(audit,"historicalSeeds");
        }
        @Override public void close() { audit.close(); verifyNoInteractions(datasource); }
    }
    static String response(long donation,long resource) {
        return "{\"donacionId\":"+donation+",\"detalles\":[{\"recursoId\":"+resource+",\"cantidadRecibidaOperacion\":1}]}";
    }
    @Test void bothHistoricalRowsCarryGeneratedIdsImagesActorsAndTheirActualSpringTransaction() {
        try (Harness h=new Harness(false)) {
            UUID v2=UUID.randomUUID(),v3=UUID.randomUUID();
            var transaction=new TransactionTemplate(h.manager); transaction.setName("model-b.synthetic-v2-v3-receipts");
            transaction.executeWithoutResult(status -> {
                assertEquals(200,h.seed(v2,2)); assertEquals(201,h.seed(v3,3));
                assertEquals(2,h.ledger().keys(State.ASSIGNED).size());
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
                assertTrue(h.durable.tables().get("recepcion_operacion").isEmpty());
            });
            assertEquals(Set.of(new Key("recepcion_operacion","200"),new Key("recepcion_operacion","201")),h.ledger().keys(State.COMMITTED_OPERATION));
            assertEquals(2,h.seeds().size());
            for (var seed:h.seeds()) {
                assertEquals(111,seed.donation()); assertEquals(102,seed.scenarioActor()); assertEquals(103,seed.historicalActor());
                assertEquals(109,seed.collection()); assertTrue(seed.scenario().endsWith("/ModelB.synthetic-history"));
                assertEquals(h.seeds().getFirst().transaction(),seed.transaction());
                assertNotNull(h.durable.tables().get(seed.key().table()).get(seed.key().id()));
            }
            assertEquals(v2.toString(),h.seeds().getFirst().operationId()); assertEquals(v3.toString(),h.seeds().getLast().operationId());
            h.gate.requireSafe(); h.audit.finish();
        }
    }
    @Test void historicalJdbcRollbackNeverCertifiesItsGeneratedId() {
        try (Harness h=new Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> { h.seed(UUID.randomUUID(),2); status.setRollbackOnly(); });
            assertEquals(Set.of(new Key("recepcion_operacion","200")),h.ledger().keys(State.ROLLED_BACK));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertTrue(h.durable.tables().get("recepcion_operacion").isEmpty()); h.gate.requireSafe(); h.audit.finish();
        }
    }
    @Test void uncertainJdbcCommitBlocksTheNextScenarioAndKeepsTheRowUncertified() {
        try (Harness h=new Harness(false)) {
            h.manager.commitUncertain=true;
            assertThrows(TransactionSystemException.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> h.seed(UUID.randomUUID(),3)));
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertThrows(AssertionError.class,h.ledger()::requireSettled);
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @Test void autocommitAndWrongPhaseAreRejectedBeforeTheInsertBackendIsCalled() {
        try (Harness h=new Harness(false)) {
            assertThrows(AssertionError.class,() -> h.seed(UUID.randomUUID(),2));
            assertEquals(0,h.inserts);
            new TransactionTemplate(h.manager).executeWithoutResult(status ->
                assertThrows(AssertionError.class,() -> h.audit.insertJdbcBase("insert into recurso(id_unidad_medida) values (?)",105)));
            assertEquals(0,h.inserts);
        }
    }
    @ParameterizedTest @ValueSource(strings={"donation","resource","version","fingerprint"})
    void historicalSeedCannotClaimAnUnrelatedDonationResourceOrUnsupportedFormat(String wrong) {
        try (Harness h=new Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                String response=response(wrong.equals("donation") ? 999 : 111,wrong.equals("resource") ? 999 : 106);
                assertThrows(AssertionError.class,() -> h.audit.insertHistoricalReceipt(UUID.randomUUID(),wrong.equals("version") ? 4 : 2,
                    wrong.equals("fingerprint") ? "invalid" : "a".repeat(64),response));
                status.setRollbackOnly();
            });
            assertEquals(0,h.inserts); assertTrue(h.seeds().isEmpty());
        }
    }
    @Test void secondResourceAndDetailAreCommittedBaseRowsInTheSamePreparationTransaction() {
        try (Harness h=new Harness(true)) {
            assertEquals(16,h.ledger().keys(State.COMMITTED_BASE).size());
            assertEquals(2,h.inserts); h.gate.requireSafe(); h.audit.finish();
        }
    }
    @Test void changingAnObservedHistoricalImageBeforeCommitBlocksIntegrity() {
        try (Harness h=new Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.seed(UUID.randomUUID(),2);
                h.fixture.change("recepcion_operacion","200","huella","changed-after-insert");
            });
            assertThrows(AssertionError.class,h.gate::requireSafe); assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @Test void registeringAUuidWithoutAnObservedInsertCannotAttributeTheRow() {
        try (Harness h=new Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status ->
                h.fixture.source.add("recepcion_operacion","200","id_donacion","111","operacion_id",UUID.randomUUID().toString()));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertThrows(AssertionError.class,h.gate::requireSafe); assertThrows(AssertionError.class,h.audit::finish);
        }
    }
}
