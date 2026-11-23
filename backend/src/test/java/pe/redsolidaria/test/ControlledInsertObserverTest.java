package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import pe.redsolidaria.test.FixtureLedger.*;

class ControlledInsertObserverTest {
    @Test void directEntityManagerPersistAndRepositorySaveShareTheRealPostInsertEventAndWaitForCommit() {
        FixtureLedger ledger=new FixtureLedger("correlation/scenario"); var tx=ledger.begin("delivery.save",Phase.SCENARIO);
        EntityPersister persister=mock(EntityPersister.class); when(persister.getTableName()).thenReturn("movimiento_donacion");
        var observer=new ControlledInsertObserver((table,id) -> ledger.allocated(new Key(table,id),tx));
        // Hibernate emits this event after its actual INSERT, regardless of the Java persistence entry point.
        observer.onPostInsert(new PostInsertEvent(new Object(),116L,new Object[0],persister,null));
        Key key=new Key("movimiento_donacion","116");
        assertEquals(Set.of(key),ledger.keys(State.ASSIGNED)); assertTrue(ledger.keys(State.COMMITTED_OPERATION).isEmpty());
        assertThrows(AssertionError.class,ledger::requireSettled);
        ledger.complete(tx,Outcome.COMMITTED); ledger.requireSettled();
        assertEquals(Set.of(key),ledger.keys(State.COMMITTED_OPERATION));
        assertEquals("correlation/scenario",ledger.manifest().get("scenario"));
        assertFalse(observer.requiresPostCommitHandling(persister));
    }
    @Test void theSamePersistEvidenceCanRollBackOrBecomeUncertainWithoutBeingCertified() {
        for (Outcome outcome:List.of(Outcome.ROLLED_BACK,Outcome.UNKNOWN)) {
            FixtureLedger ledger=new FixtureLedger(); var tx=ledger.begin("persist",Phase.SCENARIO);
            ledger.allocated(new Key("movimiento_donacion","116"),tx);
            if (outcome==Outcome.UNKNOWN) {
                assertThrows(AssertionError.class,() -> ledger.complete(tx,outcome)); assertThrows(AssertionError.class,ledger::requireSettled);
            } else { ledger.complete(tx,outcome); ledger.requireSettled(); assertEquals(1,ledger.keys(State.ROLLED_BACK).size()); }
            assertTrue(ledger.keys(State.COMMITTED_OPERATION).isEmpty());
        }
    }
    @Test void missingActualIdOrUnsupportedMappingCannotInventProvenance() {
        EntityPersister persister=mock(EntityPersister.class);
        java.util.function.BiConsumer<String,String> sink=mock(java.util.function.BiConsumer.class);
        var observer=new ControlledInsertObserver(sink);
        assertThrows(AssertionError.class,() -> observer.onPostInsert(new PostInsertEvent(new Object(),null,new Object[0],persister,null)));
        when(persister.hasMultipleTables()).thenReturn(true);
        assertThrows(AssertionError.class,() -> observer.onPostInsert(new PostInsertEvent(new Object(),116L,new Object[0],persister,null)));
        verifyNoInteractions(sink);
    }
    @Test void integrationRegistersTheListenerOnTheFactoryActuallyUsedByHibernate() {
        var factory=mock(org.hibernate.engine.spi.SessionFactoryImplementor.class);
        var services=mock(org.hibernate.service.spi.ServiceRegistryImplementor.class);
        var registry=mock(org.hibernate.event.service.spi.EventListenerRegistry.class);
        when(factory.getServiceRegistry()).thenReturn(services);
        when(services.requireService(org.hibernate.event.service.spi.EventListenerRegistry.class)).thenReturn(registry);
        var observer=new ControlledInsertObserver((table,id) -> {});
        observer.integrate(null,null,factory);
        verify(registry).appendListeners(EventType.POST_INSERT,observer);
    }
}
