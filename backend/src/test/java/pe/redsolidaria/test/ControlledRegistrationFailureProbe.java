package pe.redsolidaria.test;

import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import pe.redsolidaria.test.ControlledFixtureAudit.RegistrationRollbackEvidence;

/** Appended after the real observer. Its intentional exception never originates in the integrity observer. */
public final class ControlledRegistrationFailureProbe implements PostInsertEventListener,AutoCloseable {
    public enum Point {
        AUDIT("registro_auditoria"), MOVEMENT("movimiento_donacion");
        final String table;
        Point(String table) { this.table=table; }
    }
    public static final class InjectedRegistrationFailure extends RuntimeException {
        private InjectedRegistrationFailure(String correlation) { super("Deliberate registration rollback: "+correlation); }
    }
    private final EventListenerRegistry registry;
    private final ControlledFixtureAudit audit;
    private final Point point;
    private final List<PostInsertEventListener> original;
    private final String correlation;
    private InjectedRegistrationFailure injected;
    private RegistrationRollbackEvidence evidence;
    private int activations;
    private boolean closed; // Closure requested: disables injection/certification even if restoration must be retried.
    private boolean restorationAttempted;
    private boolean restorationConfirmed;

    public static ControlledRegistrationFailureProbe install(EntityManagerFactory factory,ControlledFixtureAudit audit,Point point) {
        try {
            var session=factory.unwrap(SessionFactoryImplementor.class);
            return new ControlledRegistrationFailureProbe(session.getServiceRegistry().requireService(EventListenerRegistry.class),audit,point);
        } catch (RuntimeException | Error failure) { audit.atomicityFailure(failure); throw failure; }
    }
    ControlledRegistrationFailureProbe(EventListenerRegistry registry,ControlledFixtureAudit audit,Point point) {
        this.registry=registry; this.audit=audit; this.point=Objects.requireNonNull(point);
        original=listeners();
        try {
            if (original.stream().filter(listener -> listener instanceof ControlledInsertObserver).count()!=1
                    || original.stream().anyMatch(listener -> listener instanceof ControlledRegistrationFailureProbe))
                throw new AssertionError("Exactly one controlled observer and no competing probe required");
            correlation=audit.armRegistrationRollback(point.table);
            try {
                registry.appendListeners(EventType.POST_INSERT,this);
                if (!listeners().equals(expectedListeners())) throw new AssertionError("Fault listener must follow all original listeners");
            } catch (RuntimeException | Error failure) {
                try { restore(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } catch (RuntimeException | Error failure) { audit.atomicityFailure(failure); throw failure; }
    }
    private List<PostInsertEventListener> listeners() {
        List<PostInsertEventListener> result=new ArrayList<>();
        registry.getEventListenerGroup(EventType.POST_INSERT).listeners().forEach(result::add);
        return List.copyOf(result);
    }
    private List<PostInsertEventListener> expectedListeners() {
        List<PostInsertEventListener> expected=new ArrayList<>(original); expected.add(this); return List.copyOf(expected);
    }
    @Override public synchronized void onPostInsert(PostInsertEvent event) {
        try {
            if (closed) throw new AssertionError("Closed rollback probe was invoked");
            if (!point.table.equals(event.getPersister().getTableName())) return;
            if (activations!=0 || event.getId()==null || event.getPersister().hasMultipleTables())
                throw new AssertionError("Fault must activate once with an actual single-table PK");
            evidence=audit.captureRegistrationRollback(correlation,point.table,event.getId().toString());
            injected=new InjectedRegistrationFailure(correlation); activations++;
        } catch (RuntimeException | Error failure) { audit.atomicityFailure(failure); throw failure; }
        throw injected;
    }
    public synchronized Map<String,String> certify(Throwable caught) {
        try {
            if (closed || activations!=1 || injected==null || !contains(caught,injected))
                throw new AssertionError("The exact injected exception must escape the real transactional proxy");
            return audit.requireRolledBackRegistration(evidence);
        } catch (RuntimeException | Error failure) { audit.atomicityFailure(failure); throw failure; }
    }
    private static boolean contains(Throwable caught,Throwable expected) {
        Set<Throwable> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable next=caught;next!=null && visited.add(next);next=next.getCause()) if (next==expected) return true;
        return false;
    }
    private void restore() {
        registry.setListeners(EventType.POST_INSERT,original.toArray(PostInsertEventListener[]::new));
        if (!listeners().equals(original)) throw new AssertionError("Original Hibernate listeners were not restored");
    }
    @Override public synchronized void close() {
        if (restorationConfirmed) return;
        boolean retry=restorationAttempted;
        closed=true;
        Throwable failure=null;
        try {
            var current=listeners();
            if (!current.equals(expectedListeners()) && !(retry && current.equals(original)))
                throw new AssertionError("Hibernate listener configuration changed while the probe was installed");
        } catch (RuntimeException | Error problem) {
            failure=problem; audit.atomicityFailure(problem);
        } finally {
            restorationAttempted=true;
            try {
                restore();
                restorationConfirmed=true;
            } catch (RuntimeException | Error cleanup) {
                if (failure==null) failure=cleanup;
                else if (failure!=cleanup) failure.addSuppressed(cleanup);
                audit.atomicityFailure(failure);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure!=null) throw (RuntimeException)failure;
    }
    @Override public boolean requiresPostCommitHandling(org.hibernate.persister.entity.EntityPersister mapping) { return false; }
}
