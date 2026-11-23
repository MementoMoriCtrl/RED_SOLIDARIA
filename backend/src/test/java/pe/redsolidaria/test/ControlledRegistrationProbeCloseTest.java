package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.hibernate.event.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.test.ControlledRegistrationFailureProbe.Point;

/** Listener registry and storage are simulated. No Hibernate factory, Spring context or JDBC execution. */
class ControlledRegistrationProbeCloseTest {
    private static void states(ControlledRegistrationFailureProbe probe,boolean requested,boolean attempted,boolean confirmed) {
        assertEquals(requested,ReflectionTestUtils.getField(probe,"closed"));
        assertEquals(attempted,ReflectionTestUtils.getField(probe,"restorationAttempted"));
        assertEquals(confirmed,ReflectionTestUtils.getField(probe,"restorationConfirmed"));
    }
    @Test void successfulCloseRecordsAllThreeStatesAndRepeatedCloseDoesNotTouchTheRegistry() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry(); var originals=List.copyOf(registry.listeners);
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            states(probe,false,false,false); probe.close(); states(probe,true,true,true);
            assertEquals(originals,registry.listeners); clearInvocations(registry.registry,registry.group);
            probe.close(); verifyNoInteractions(registry.registry,registry.group); h.gate.requireSafe();
        }
    }
    @Test void inspectionFailureStillRestoresAndPreservesTheExactOriginalException() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry(); var originals=List.copyOf(registry.listeners);
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var inspection=new IllegalStateException("inspection unavailable");
            when(registry.group.listeners()).thenThrow(inspection).thenAnswer(call -> List.copyOf(registry.listeners));
            assertSame(inspection,assertThrows(IllegalStateException.class,probe::close));
            assertEquals(originals,registry.listeners); states(probe,true,true,true);
            assertSame(inspection,assertThrows(AssertionError.class,h.gate::requireSafe).getCause());
            probe.close();
        }
    }
    @Test void restorationFailureIsSuppressedUnderTheOriginalInspectionErrorAndCanBeRetried() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry(); var originals=List.copyOf(registry.listeners);
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var inspection=new AssertionError("inspection failed"); var cleanup=new IllegalStateException("restore failed");
            when(registry.group.listeners()).thenThrow(inspection).thenAnswer(call -> List.copyOf(registry.listeners));
            doThrow(cleanup).doAnswer(call -> {
                registry.listeners.clear(); registry.listeners.addAll(Arrays.asList((PostInsertEventListener[])call.getRawArguments()[1])); return null;
            }).when(registry.registry).setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertSame(inspection,assertThrows(AssertionError.class,probe::close));
            assertArrayEquals(new Throwable[]{cleanup},inspection.getSuppressed()); states(probe,true,true,false);
            assertSame(inspection,assertThrows(AssertionError.class,h.gate::requireSafe).getCause());
            assertTrue(registry.listeners.contains(probe));
            probe.close(); states(probe,true,true,true); assertEquals(originals,registry.listeners);
            assertThrows(AssertionError.class,h.gate::requireSafe); // Successful cleanup never resets the latch.
        }
    }
    @Test void failedRestorationVerificationDoesNotClaimSuccessAndRetryAcceptsAlreadyRestoredOriginals() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry(); var originals=List.copyOf(registry.listeners);
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var verification=new IllegalStateException("verification unavailable");
            when(registry.group.listeners()).thenAnswer(call -> List.copyOf(registry.listeners))
                .thenThrow(verification).thenAnswer(call -> List.copyOf(registry.listeners));
            assertSame(verification,assertThrows(IllegalStateException.class,probe::close));
            assertEquals(originals,registry.listeners); states(probe,true,true,false);
            probe.close(); states(probe,true,true,true); assertThrows(AssertionError.class,h.gate::requireSafe);
        }
    }
    @Test void restorationErrorWithoutAnInspectionErrorRemainsThePrimaryException() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry();
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var cleanup=new AssertionError("restoration failed");
            doThrow(cleanup).when(registry.registry).setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertSame(cleanup,assertThrows(AssertionError.class,probe::close)); states(probe,true,true,false);
            assertEquals(0,cleanup.getSuppressed().length); h.blocked();
        }
    }
    @Test void tryWithResourcesKeepsTheBodyExceptionAndTheNestedCleanupCauses() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry();
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var body=new IllegalArgumentException("original body failure");
            var inspection=new IllegalStateException("inspection failure"); var cleanup=new AssertionError("cleanup failure");
            when(registry.group.listeners()).thenThrow(inspection);
            doThrow(cleanup).when(registry.registry).setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertSame(body,assertThrows(IllegalArgumentException.class,() -> { try (probe) { throw body; } }));
            assertArrayEquals(new Throwable[]{inspection},body.getSuppressed());
            assertArrayEquals(new Throwable[]{cleanup},inspection.getSuppressed()); states(probe,true,true,false); h.blocked();
        }
    }
    @Test void aPendingFailedCloseCannotInstallAnotherProbeOrCertifyAnOperation() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry();
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            doThrow(new IllegalStateException("restore failed")).when(registry.registry)
                .setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertThrows(IllegalStateException.class,probe::close); states(probe,true,true,false);
            assertThrows(AssertionError.class,() -> new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT));
            assertEquals(1,registry.listeners.stream().filter(listener -> listener instanceof ControlledRegistrationFailureProbe).count());
            assertThrows(AssertionError.class,() -> probe.certify(new RuntimeException("unrelated"))); h.blocked();
        }
    }
    @Test void identicalInspectionAndRestorationThrowableDoesNotCauseSelfSuppression() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new ControlledRegistrationAtomicityTest.Registry();
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            var same=new IllegalStateException("shared failure");
            when(registry.group.listeners()).thenThrow(same);
            doThrow(same).when(registry.registry).setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertSame(same,assertThrows(IllegalStateException.class,probe::close));
            assertEquals(0,same.getSuppressed().length); states(probe,true,true,false); h.blocked();
        }
    }
}
