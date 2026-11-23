package pe.redsolidaria.test;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.entity.EntityPersister;

/** POST_INSERT runs after the real INSERT, also for EntityManager.persist and commit-time flush. */
final class ControlledInsertObserver implements Integrator,PostInsertEventListener {
    private final BiConsumer<String,String> assigned;
    private final Consumer<Throwable> failure;
    ControlledInsertObserver() { this(ControlledFixtureAudit::hibernateInserted,ControlledFixtureAudit::insertObservationFailed); }
    ControlledInsertObserver(BiConsumer<String,String> assigned) { this(assigned,ignored -> {}); }
    private ControlledInsertObserver(BiConsumer<String,String> assigned,Consumer<Throwable> failure) {
        this.assigned=assigned; this.failure=failure;
    }
    @Override public void integrate(Metadata metadata,BootstrapContext context,SessionFactoryImplementor factory) {
        factory.getServiceRegistry().requireService(EventListenerRegistry.class).appendListeners(EventType.POST_INSERT,this);
    }
    @Override public void onPostInsert(PostInsertEvent event) {
        try {
            if (event.getId()==null || event.getPersister().hasMultipleTables())
                throw new AssertionError("Cannot establish insert provenance for this mapping");
            assigned.accept(event.getPersister().getTableName(),event.getId().toString());
        } catch (RuntimeException | Error problem) { failure.accept(problem); throw problem; }
    }
    @Override public boolean requiresPostCommitHandling(EntityPersister persister) { return false; }
}
