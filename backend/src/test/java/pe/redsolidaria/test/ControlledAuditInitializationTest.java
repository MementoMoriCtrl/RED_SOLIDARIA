package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.dao.DataAccessResourceFailureException;
import pe.redsolidaria.test.ControlledFixtureAudit.*;

class ControlledAuditInitializationTest {
    @ParameterizedTest @ValueSource(strings={"identity","capture","master","listener"})
    void everyConstructorStageLatchesTheOriginalFailureAndPreventsTheNextScenario(String stage) {
        IntegrityGate gate=new IntegrityGate(); DataSource source=mock(DataSource.class);
        var manager=mock(JpaTransactionManager.class);
        var failure=stage.equals("identity") ? new DataAccessResourceFailureException("simulated unavailable connection")
            : new AssertionError("simulated integrity inconsistency at "+stage);
        Runnable identity=() -> { if (stage.equals("identity")) raise(failure); };
        java.util.function.Supplier<Snapshot> capture=() -> { if (stage.equals("capture")) raise(failure); return new Snapshot(Map.of(),Map.of()); };
        Runnable master=() -> { if (stage.equals("master")) raise(failure); };
        if (stage.equals("listener")) doAnswer(call -> { raise(failure); return null; }).when(manager).addListener(any());
        var initialization=new Initialization(identity,capture,master);
        assertSame(failure,assertThrows(Throwable.class,() -> new ControlledFixtureAudit(new JdbcTemplate(source),manager,
            UUID.randomUUID().toString(),"scenario",gate,initialization)));
        var blocked=assertThrows(AssertionError.class,gate::requireSafe); assertSame(failure,blocked.getCause());
        assertTrue(blocked.getMessage().contains("audit-construction/"+stage));
        var next=mock(java.util.function.Supplier.class);
        assertThrows(AssertionError.class,() -> gate.initialize("next-scenario",next)); verifyNoInteractions(next,source);
        assertThrows(AssertionError.class,() -> new ControlledFixtureAudit(new JdbcTemplate(source),manager,
            UUID.randomUUID().toString(),"next",gate,initialization));
        assertSame(failure,assertThrows(AssertionError.class,gate::requireSafe).getCause());
    }
    private static void raise(Throwable failure) {
        if (failure instanceof Error error) throw error;
        throw (RuntimeException)failure;
    }
    @Test void lifecycleGuardAlsoLatchesFailuresOutsideTheConstructorWithoutResetting() {
        IntegrityGate gate=new IntegrityGate(); AssertionError first=new AssertionError("observer setup");
        assertSame(first,assertThrows(AssertionError.class,() -> gate.initialize("before-each",() -> { throw first; })));
        gate.fail("later",new AssertionError("different"));
        assertSame(first,assertThrows(AssertionError.class,gate::requireSafe).getCause());
    }
    @Test void invalidTransactionManagerAndCorrelationAreAlsoInitializationFailures() {
        for (boolean badManager:List.of(true,false)) {
            IntegrityGate gate=new IntegrityGate(); DataSource source=mock(DataSource.class);
            org.springframework.transaction.PlatformTransactionManager manager=badManager
                ? mock(org.springframework.transaction.PlatformTransactionManager.class) : mock(JpaTransactionManager.class);
            assertThrows(Throwable.class,() -> new ControlledFixtureAudit(new JdbcTemplate(source),manager,
                badManager ? UUID.randomUUID().toString() : "invalid-correlation","scenario",gate,
                new Initialization(() -> {},() -> new Snapshot(Map.of(),Map.of()),() -> {})));
            var failure=assertThrows(AssertionError.class,gate::requireSafe);
            assertTrue(failure.getMessage().contains(badManager ? "transaction-manager" : "scenario-identity"));
            verifyNoInteractions(source);
        }
    }
}
