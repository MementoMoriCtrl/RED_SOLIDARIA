package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.*;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.test.ControlledRegistrationFailureProbe.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Real transaction advice/callbacks with synthetic storage and a stateful listener-registry mock. No context/JDBC. */
class ControlledRegistrationAtomicityTest {
    static final class Registry {
        final EventListenerRegistry registry=mock(EventListenerRegistry.class);
        @SuppressWarnings("unchecked") final EventListenerGroup<PostInsertEventListener> group=mock(EventListenerGroup.class);
        final List<PostInsertEventListener> listeners=new ArrayList<>();
        boolean failAppend,reorderAppend;
        Registry() {
            listeners.add(new ControlledInsertObserver()); listeners.add(mock(PostInsertEventListener.class));
            when(registry.getEventListenerGroup(EventType.POST_INSERT)).thenReturn(group);
            when(group.listeners()).thenAnswer(call -> List.copyOf(listeners));
            doAnswer(call -> {
                var added=(PostInsertEventListener[])call.getRawArguments()[1];
                if (reorderAppend) listeners.addAll(0,Arrays.asList(added)); else listeners.addAll(Arrays.asList(added));
                if (failAppend) throw new IllegalStateException("Synthetic append failure"); return null;
            }).when(registry).appendListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            doAnswer(call -> {
                listeners.clear(); listeners.addAll(Arrays.asList((PostInsertEventListener[])call.getRawArguments()[1])); return null;
            }).when(registry).setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
        }
        void dispatch(PostInsertEvent event) { for (var listener:List.copyOf(listeners)) listener.onPostInsert(event); }
        EntityManagerFactory factory() {
            var emf=mock(EntityManagerFactory.class); var factory=mock(SessionFactoryImplementor.class);
            var services=mock(org.hibernate.service.spi.ServiceRegistryImplementor.class);
            when(emf.unwrap(SessionFactoryImplementor.class)).thenReturn(factory); when(factory.getServiceRegistry()).thenReturn(services);
            when(services.requireService(EventListenerRegistry.class)).thenReturn(registry); return emf;
        }
    }
    private static TransactionTemplate transaction(ControlledRegistrationProvenanceTest.Harness h) {
        var tx=new TransactionTemplate(h.manager);
        tx.setName("pe.redsolidaria.modules.donations.application.DonationRegistrationService.register"); return tx;
    }
    private static PostInsertEvent event(String table,Long id) {
        var mapping=mock(EntityPersister.class); when(mapping.getTableName()).thenReturn(table);
        return new PostInsertEvent(new Object(),id,new Object[0],mapping,null);
    }
    @Test void actualServiceProxyRollsBackFourObservedRowsAndRestoresFactoryListeners() {
        try (var service=new ControlledRegistrationServiceTest.ServiceHarness()) {
            var h=service.storage; var registry=new Registry(); var originals=List.copyOf(registry.listeners);
            h.postInsert=registry::dispatch;
            try (var probe=ControlledRegistrationFailureProbe.install(registry.factory(),h.audit,Point.AUDIT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> service.service.register(service.request,"fixture@integration.test"));
                assertEquals(4,h.ledger().keys(State.ROLLED_BACK).size()); assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
                var ids=probe.certify(new IllegalStateException("wrapper",failure));
                assertEquals(4,ids.size()); assertEquals("900204",ids.get("registro_auditoria"));
                assertThrows(UnsupportedOperationException.class,ids::clear);
                verify(service.em).persist(any(pe.redsolidaria.modules.audit.domain.AuditRecord.class));
                verify(service.em,never()).refresh(any());
            }
            assertEquals(originals,registry.listeners); h.audit.finish();
        }
    }
    @Test void movementFailureRollsBackThreeRealObservedKeysAndNeverAttemptsAuditPersist() {
        try (var service=new ControlledRegistrationServiceTest.ServiceHarness()) {
            var h=service.storage; var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.MOVEMENT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> service.service.register(service.request,"fixture@integration.test"));
                var ids=probe.certify(failure); assertEquals(3,ids.size()); assertFalse(ids.containsKey("registro_auditoria"));
                verify(service.em,never()).persist(any()); assertEquals(3,h.ledger().keys(State.ROLLED_BACK).size());
            }
            h.audit.finish();
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate"})
    void missingOrDuplicateControlledObserverCannotArmTheFault(String wrong) {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry();
            if (wrong.equals("missing")) registry.listeners.removeFirst(); else registry.listeners.add(new ControlledInsertObserver());
            var original=List.copyOf(registry.listeners);
            assertThrows(AssertionError.class,() -> new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT));
            assertEquals(original,registry.listeners); h.blocked();
        }
    }
    @ParameterizedTest @ValueSource(strings={"append-failure","wrong-order"})
    void failedInstallationRestoresTheOriginalListenerArray(String wrong) {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); var original=List.copyOf(registry.listeners);
            registry.failAppend=wrong.equals("append-failure"); registry.reorderAppend=wrong.equals("wrong-order");
            assertThrows(Throwable.class,() -> new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT));
            assertEquals(original,registry.listeners); h.blocked();
        }
    }
    @Test void originalListenersAreRestoredEvenWhenAnUnrelatedServiceFailureOccurs() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); var original=List.copyOf(registry.listeners); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var wrong=assertThrows(IllegalArgumentException.class,() -> transaction(h).executeWithoutResult(status -> {
                    h.insert(0); throw new IllegalArgumentException("unrelated failure");
                }));
                assertThrows(AssertionError.class,() -> probe.certify(wrong)); h.blocked();
            }
            assertEquals(original,registry.listeners);
        }
    }
    @Test void changingListenerConfigurationIsDetectedAndOriginalsStillRestored() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); var original=List.copyOf(registry.listeners);
            var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            registry.listeners.add(mock(PostInsertEventListener.class));
            assertThrows(AssertionError.class,probe::close); assertEquals(original,registry.listeners); h.blocked();
            probe.close();
        }
    }
    @Test void failedRestorationBlocksIntegrityRatherThanCertifyingSuccess() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT);
            doThrow(new IllegalStateException("restoration unavailable")).when(registry.registry)
                .setListeners(eq(EventType.POST_INSERT),any(PostInsertEventListener[].class));
            assertThrows(IllegalStateException.class,probe::close); h.blocked();
        }
    }
    @Test void aForeignFaultEventPkCannotReplaceTheActualObservedAuditPk() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry();
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                assertThrows(AssertionError.class,() -> transaction(h).executeWithoutResult(status -> {
                    h.all(); probe.onPostInsert(event("registro_auditoria",999999L));
                })); h.blocked();
            }
        }
    }
    @Test void observerIntegrityFailureCannotBeMistakenForTheDeliberateException() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var failure=assertThrows(AssertionError.class,() -> transaction(h).executeWithoutResult(status -> {
                    var fields=h.fields("donacion"); fields.put("id_usuario_registro","3"); h.store("donacion",900201,fields,true);
                }));
                assertThrows(AssertionError.class,() -> probe.certify(failure)); h.blocked();
            }
        }
    }
    @Test void secondActivationOfTheFaultIsAnIntegrityFailure() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                assertThrows(AssertionError.class,() -> transaction(h).executeWithoutResult(status -> {
                    assertThrows(InjectedRegistrationFailure.class,h::all);
                    probe.onPostInsert(event("registro_auditoria",900204L));
                })); h.blocked();
            }
        }
    }
    @Test void uncertainRollbackCannotBeCertified() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                h.manager.rollbackUncertain=true;
                var failure=assertThrows(TransactionSystemException.class,() -> transaction(h).executeWithoutResult(status -> h.all()));
                assertThrows(AssertionError.class,() -> probe.certify(failure));
                assertThrows(AssertionError.class,h.ledger()::requireSettled); h.blocked();
            }
        }
    }
    @Test void residualRowsAfterAReportedRollbackBlockTheGate() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            h.manager.rollback=() -> h.durable=h.fixture.source.snapshot();
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> transaction(h).executeWithoutResult(status -> h.all()));
                assertThrows(AssertionError.class,() -> probe.certify(failure)); h.blocked();
            }
        }
    }
    @Test void swallowingTheFaultAndCommittingCannotPassRollbackCertification() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                Throwable[] fault=new Throwable[1];
                transaction(h).executeWithoutResult(status -> fault[0]=assertThrows(InjectedRegistrationFailure.class,h::all));
                assertEquals(4,h.ledger().keys(State.COMMITTED_OPERATION).size());
                assertThrows(AssertionError.class,() -> probe.certify(fault[0])); h.blocked();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"capture","master"})
    void postRollbackAuditFailuresBlockTheNextScenario(String wrong) {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            h.manager.rollback=() -> { h.unreadable=wrong.equals("capture"); h.masterUnhealthy=wrong.equals("master"); };
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> transaction(h).executeWithoutResult(status -> h.all()));
                assertThrows(AssertionError.class,() -> probe.certify(failure)); h.blocked();
            }
        }
    }
    @Test void all381IdentitiesAreRequiredAndPreservedDuringCertifiedRollback() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness(true)) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> transaction(h).executeWithoutResult(status -> h.all()));
                assertEquals(4,probe.certify(failure).size());
            }
            ControlledFixtureAudit.verifyRequiredRows(h.durable,PhaseOneConfirmedRows.loadAllConfirmed()); h.audit.finish();
        }
    }
    @Test void aSimilarExceptionMessageDoesNotProveTheFaultWasPropagated() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry(); h.postInsert=registry::dispatch;
            try (var probe=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) {
                var failure=assertThrows(InjectedRegistrationFailure.class,() -> transaction(h).executeWithoutResult(status -> h.all()));
                assertThrows(AssertionError.class,() -> probe.certify(new RuntimeException(failure.getMessage()))); h.blocked();
            }
        }
    }
    @Test void armedButUncertifiedScenarioCannotEmitSuccessfulFinalIntegrity() {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var registry=new Registry();
            try (var ignored=new ControlledRegistrationFailureProbe(registry.registry,h.audit,Point.AUDIT)) { }
            assertThrows(AssertionError.class,h.audit::finish); assertThrows(AssertionError.class,h.gate::requireSafe);
        }
    }
    @ParameterizedTest @ValueSource(strings={"InnoDB","MyISAM","missing"})
    void engineGuardUsesTheReadTransactionAndRejectsNonTransactionalOrMissingTables(String engine) {
        try (var h=new ControlledRegistrationProvenanceTest.Harness()) {
            var jdbc=mock(JdbcTemplate.class); ReflectionTestUtils.setField(h.audit,"jdbc",jdbc);
            List<Map<String,Object>> tables=new ArrayList<>();
            for (String table:ControlledRegistrationProvenanceTest.TABLES) tables.add(Map.of("TABLE_NAME",table,"ENGINE",engine));
            if (engine.equals("missing")) tables.removeLast();
            when(jdbc.queryForList(anyString())).thenReturn(tables);
            if (engine.equals("InnoDB")) h.audit.requireTransactionalRegistrationTables();
            else { assertThrows(AssertionError.class,h.audit::requireTransactionalRegistrationTables); h.blocked(); }
            verify(jdbc).queryForList(contains("INFORMATION_SCHEMA.TABLES"));
        }
    }
}
