package pe.redsolidaria.test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** One-way latch. Instances let unit tests exercise failures without resetting the JVM gate. */
final class IntegrityGate {
    private record Failure(String stage, Throwable cause) {}
    private final AtomicReference<Failure> failure=new AtomicReference<>();
    void fail(String stage,Throwable cause) { failure.compareAndSet(null,new Failure(stage,cause)); }
    void requireSafe() {
        Failure first=failure.get();
        if (first!=null) throw new AssertionError("Integrity unsafe at "+first.stage()+"; later scenarios blocked",first.cause());
    }
    <T> T initialize(String stage,Supplier<T> action) {
        requireSafe();
        try { return action.get(); }
        catch (RuntimeException | Error problem) { fail(stage,problem); throw problem; }
    }
}
