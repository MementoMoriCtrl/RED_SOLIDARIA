package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.sql.DataSource;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Real Spring transaction lifecycle and real audit listener; only storage and JDBC are replaced. */
class ControlledTransactionCallbacksTest {
    public interface DispatchWriter { void dispatch(pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch request); }
    static final class MemoryManager extends AbstractPlatformTransactionManager {
        Runnable commit=() -> {};
        boolean commitUncertain;
        boolean rollbackUncertain;
        Runnable rollback=() -> {};
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx,TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {
            commit.run();
            if (commitUncertain) throw new TransactionSystemException("simulated uncertain commit result");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            rollback.run();
            if (rollbackUncertain) throw new TransactionSystemException("simulated uncertain rollback result");
        }
    }
    static final class Harness implements AutoCloseable {
        final ControlledFixtureAuditTest fixture=new ControlledFixtureAuditTest();
        final DataSource datasource=mock(DataSource.class);
        final IntegrityGate gate=new IntegrityGate();
        final MemoryManager manager=new MemoryManager();
        Snapshot durable=fixture.source.snapshot();
        boolean unreadable;
        final ControlledFixtureAudit audit=new ControlledFixtureAudit(new JdbcTemplate(datasource),manager,
            UUID.randomUUID().toString(),"isolated-callback-scenario",gate,
            new Initialization(() -> {},() -> {
                if (unreadable) throw new AssertionError("simulated committed capture failure");
                return durable;
            },() -> {},key -> fixture.source.snapshot().tables().get(key.table()).get(key.id())));
        Harness() {
            manager.commit=() -> durable=fixture.source.snapshot();
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                fixture.fixture();
                fixture.base.forEach(key -> audit.register(key.table(),Long.parseLong(key.id())));
                audit.authorizeRelations(fixture.expectedRelations());
            });
            audit.announce();
        }
        FixtureLedger ledger() { return (FixtureLedger)ReflectionTestUtils.getField(audit,"ledger"); }
        Key persistMovement() {
            fixture.source.add("movimiento_donacion","116","id_donacion","111","id_usuario_responsable","102");
            var mapping=mock(EntityPersister.class); when(mapping.getTableName()).thenReturn("movimiento_donacion");
            new ControlledInsertObserver().onPostInsert(new PostInsertEvent(new Object(),116L,new Object[0],mapping,null));
            return new Key("movimiento_donacion","116");
        }
        @Override public void close() { audit.close(); verifyNoInteractions(datasource); }
    }
    @Test void springAfterCommitAloneCertifiesTheActualInsertedId() {
        try (Harness h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                Key key=h.persistMovement();
                assertEquals(Set.of(key),h.ledger().keys(State.ASSIGNED));
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
                assertFalse(h.durable.tables().get(key.table()).containsKey(key.id()));
            });
            assertEquals(Set.of(new Key("movimiento_donacion","116")),h.ledger().keys(State.COMMITTED_OPERATION));
            h.ledger().requireSettled(); h.gate.requireSafe(); h.audit.finish();
        }
    }
    @Test void springAfterRollbackCertifiesAbsenceAndAllowsALaterLegitimateTransaction() {
        try (Harness h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> { h.persistMovement(); status.setRollbackOnly(); });
            assertEquals(Set.of(new Key("movimiento_donacion","116")),h.ledger().keys(State.ROLLED_BACK));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); h.gate.requireSafe();
            h.fixture.source.remove("movimiento_donacion","116");
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {});
            h.audit.finish();
        }
    }
    @Test void anUncertainSpringCommitBlocksCertificationAndSubsequentWriteTransactions() {
        try (Harness h=new Harness()) {
            h.manager.commitUncertain=true;
            assertThrows(TransactionSystemException.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> h.persistMovement()));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertThrows(AssertionError.class,h.ledger()::requireSettled);
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> fail("must not run")));
            assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @Test void anAuditFailureAfterCommitCannotPretendThatTheDurableOperationRolledBack() {
        try (Harness h=new Harness()) {
            h.unreadable=true;
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.persistMovement());
            assertEquals(1,h.ledger().keys(State.COMMITTED_OPERATION).size());
            assertTrue(h.ledger().keys(State.ROLLED_BACK).isEmpty());
            assertTrue(h.durable.tables().get("movimiento_donacion").containsKey("116"));
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @Test void anUnobservedCommittedInsertionWithCorrectParentsFailsClosedInTheRealCallback() {
        try (Harness h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status ->
                h.fixture.source.add("movimiento_donacion","116","id_donacion","111","id_usuario_responsable","102"));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @Test void jdbcDispatchObserverAttributesOnlyASuccessfulRepositoryInsertionAndThenWaitsForCommit() {
        try (Harness h=new Harness()) {
            UUID id=UUID.randomUUID();
            DispatchWriter target=request -> h.fixture.source.add("traslado_recurso",id.toString(),"id_detalle_donacion","112",
                "id_centro_acopio","109","id_centro_distribucion","110","id_unidad_medida","105","id_usuario_salida","102","estado","EN_TRANSITO");
            var factory=new org.springframework.aop.framework.ProxyFactory(target); DispatchWriter repository=(DispatchWriter)factory.getProxy();
            h.audit.observeTransferIds(repository);
            var request=new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(id,112L,110L,java.math.BigDecimal.ONE,null);
            Key key=new Key("traslado_recurso",id.toString());
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                repository.dispatch(request);
                assertEquals(Set.of(key),h.ledger().keys(State.ASSIGNED));
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            });
            assertEquals(Set.of(key),h.ledger().keys(State.COMMITTED_OPERATION)); h.gate.requireSafe(); h.audit.finish();
        }
    }
    @Test void failedJdbcDispatchCannotAttributeARequestUuidAsAnInsertedRow() {
        try (Harness h=new Harness()) {
            DispatchWriter target=request -> { throw new IllegalStateException("simulated insert failure"); };
            var factory=new org.springframework.aop.framework.ProxyFactory(target); DispatchWriter repository=(DispatchWriter)factory.getProxy();
            h.audit.observeTransferIds(repository);
            var request=new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(UUID.randomUUID(),112L,110L,java.math.BigDecimal.ONE,null);
            assertThrows(IllegalStateException.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> repository.dispatch(request)));
            assertTrue(h.ledger().keys(State.ASSIGNED).isEmpty());
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); h.gate.requireSafe(); h.audit.finish();
        }
    }
}
