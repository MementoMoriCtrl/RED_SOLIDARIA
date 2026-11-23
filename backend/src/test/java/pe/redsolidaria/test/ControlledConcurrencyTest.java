package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ControlledConcurrencyTest {
    ControlledConcurrency runner() { return new ControlledConcurrency(Duration.ofSeconds(1),Duration.ofMillis(150),Duration.ofSeconds(2)); }
    @Test void runsTwoTasksConcurrentlyPreservesInputOrderAndTerminatesWorkers() {
        var runner=runner(); var both=new CountDownLatch(2);
        assertEquals(List.of("first","second"),runner.run(() -> {
            both.countDown(); assertTrue(both.await(1,TimeUnit.SECONDS)); return "first";
        },() -> { both.countDown(); assertTrue(both.await(1,TimeUnit.SECONDS)); return "second"; }));
        assertDoesNotThrow(runner::assertQuiescent);
    }
    @Test void executionTimeoutCancelsBothTasksAndWaitsForTheirTermination() {
        var runner=runner(); var interrupted=new CountDownLatch(2);
        Callable<Object> blocked=() -> {
            try { new CountDownLatch(1).await(); return null; }
            catch (InterruptedException cancelled) { interrupted.countDown(); throw cancelled; }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(4),() -> {
            var failure=assertThrows(AssertionError.class,() -> runner.run(blocked,blocked));
            assertTrue(failure.getMessage().contains("timeout"));
            assertEquals(0,interrupted.getCount()); assertDoesNotThrow(runner::assertQuiescent);
        });
    }
    @Test void deadlockCancelsTheOtherTaskAndReportsOnlySafeSqlMetadata() {
        var runner=runner(); var failure=assertThrows(AssertionError.class,() -> runner.run(
            () -> { throw new SQLException("private request body and password","40001",1213); },
            () -> { new CountDownLatch(1).await(); return null; }));
        assertTrue(failure.getMessage().contains("deadlock")); assertTrue(failure.getMessage().contains("1213"));
        assertFalse(failure.getMessage().contains("password")); assertNull(failure.getCause());
        assertDoesNotThrow(runner::assertQuiescent);
    }
    @Test void reportsMysqlLockTimeoutAndSanitizesNonSqlFailures() {
        assertTrue(ControlledConcurrency.describe(new SQLException("secret","HY000",1205)).contains("lock timeout"));
        assertEquals("Concurrent worker failed (IllegalStateException); request and exception bodies omitted",
            ControlledConcurrency.describe(new IllegalStateException("password")));
    }
}
