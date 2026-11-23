package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.test.FixtureLedger.*;

/** Generated-key storage is simulated; Spring transaction callbacks run without a context or connection. */
class ControlledPresentationMovementTest {
    @Test void generatedMovementIsAssignedWithInitialImageAndOnlyCertifiedByActualCommitCallback() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            var tx=new TransactionTemplate(h.manager); tx.setName("query.presentation");
            tx.executeWithoutResult(status -> {
                assertEquals(200,h.audit.insertPresentedDonationMovement());
                assertEquals(Set.of(new Key("movimiento_donacion","200")),h.ledger().keys(State.ASSIGNED));
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
                assertTrue(h.durable.tables().get("movimiento_donacion").isEmpty());
                var images=(Map<?,?>)ReflectionTestUtils.getField(h.audit,"allocationImages");
                assertNotNull(images.get(new Key("movimiento_donacion","200")));
                assertTrue(h.ledger().manifest().toString().contains("query.presentation"));
            });
            assertEquals(Set.of(new Key("movimiento_donacion","200")),h.ledger().keys(State.COMMITTED_OPERATION));
            h.audit.requireReadyForScenario(); h.audit.finish();
        }
    }
    @Test void rollbackRecordsGeneratedIdWithoutCertifyingAnOperation() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.audit.insertPresentedDonationMovement(); status.setRollbackOnly();
            });
            assertEquals(Set.of(new Key("movimiento_donacion","200")),h.ledger().keys(State.ROLLED_BACK));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            h.audit.requireReadyForScenario(); h.audit.finish();
        }
    }
    @Test void autocommitIsRejectedBeforeInsertionAndBlocksSubsequentScenarios() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            assertThrows(AssertionError.class,h.audit::insertPresentedDonationMovement);
            assertEquals(0,h.inserts);
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertThrows(AssertionError.class,h.audit::requireReadyForScenario);
        }
    }
    @Test void uncertainCommitBlocksReadinessAndTheNextWriteWithoutCertifyingTheRow() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            h.manager.commitUncertain=true;
            assertThrows(TransactionSystemException.class,() -> new TransactionTemplate(h.manager)
                .executeWithoutResult(status -> h.audit.insertPresentedDonationMovement()));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            assertThrows(AssertionError.class,h.audit::requireReadyForScenario);
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> fail("blocked")));
        }
    }
    @ParameterizedTest @ValueSource(strings={"tipo_evento","id_donacion","id_usuario_responsable","observaciones"})
    void changedInitialMovementOrRelationshipBlocksReadinessEvenWhenCommitAlreadyHappened(String column) {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.audit.insertPresentedDonationMovement(); h.fixture.change("movimiento_donacion","200",column,"invalid");
            });
            assertThrows(AssertionError.class,h.audit::requireReadyForScenario);
            assertThrows(AssertionError.class,h.gate::requireSafe);
            assertEquals(1,h.ledger().keys(State.COMMITTED_OPERATION).size());
            assertTrue(h.ledger().keys(State.ROLLED_BACK).isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings={"DONACION_PRESENTADA","DONACION_RECIBIDA"})
    void registrarCannotBeUsedByAnotherObservedMovementEvenForTheSameEvent(String event) {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.audit.insertPresentedDonationMovement());
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.fixture.source.add("movimiento_donacion","201","id_donacion","111","id_usuario_responsable","103","tipo_evento",event);
                ControlledFixtureAudit.hibernateInserted("movimiento_donacion","201");
            });
            assertThrows(AssertionError.class,h.audit::requireReadyForScenario);
        }
    }
    @Test void otherMovementsStillAcceptTheNormalScenarioActor() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.audit.insertPresentedDonationMovement());
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.fixture.source.add("movimiento_donacion","201","id_donacion","111","id_usuario_responsable","102","tipo_evento","DONACION_RECIBIDA");
                ControlledFixtureAudit.hibernateInserted("movimiento_donacion","201");
            });
            h.audit.requireReadyForScenario(); h.audit.finish();
        }
    }
    @Test void changingDonationRegistrarCannotAuthorizeTheMovement() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.fixture.change("donacion","111","id_usuario_registro","102"); h.audit.insertPresentedDonationMovement();
            });
            assertThrows(AssertionError.class,h.audit::requireReadyForScenario);
        }
    }
    @Test void duplicatePresentationSeedIsRejectedAndLatchesIntegrity() {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.audit.insertPresentedDonationMovement());
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager)
                .executeWithoutResult(status -> h.audit.insertPresentedDonationMovement()));
            assertEquals(1,h.inserts); assertThrows(AssertionError.class,h.gate::requireSafe);
        }
    }
    @ParameterizedTest @ValueSource(ints={0,2,1})
    void realGeneratedKeyPathRejectsWrongRowCountsOrMissingKeyWithoutOpeningAnyConnection(int count) {
        try (var h=new ControlledJdbcProvenanceTest.Harness(false)) {
            // Exercise the production JdbcTemplate branch with a mock, not the memory insert backend.
            ReflectionTestUtils.setField(h.audit,"initialization",null);
            var jdbc=mock(JdbcTemplate.class); ReflectionTestUtils.setField(h.audit,"jdbc",jdbc);
            when(jdbc.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(call -> {
                if (count!=1) ((KeyHolder)call.getArgument(1)).getKeyList().add(Map.of("id",200L));
                return count;
            });
            assertThrows(AssertionError.class,() -> ReflectionTestUtils.invokeMethod(h.audit,"generatedInsert","insert into ignored(x) values (?)",new Object[]{1}));
            verify(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        }
    }
}
