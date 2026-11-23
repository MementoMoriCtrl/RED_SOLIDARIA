package pe.redsolidaria.test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded test workers: never uses ExecutorService.close(), which can wait indefinitely. */
public final class ControlledConcurrency {
    private static final AtomicBoolean UNSAFE_WORKER_LEFT_RUNNING=new AtomicBoolean();
    private final Duration startTimeout,executionTimeout,shutdownTimeout;
    private final Set<ExecutorService> executors=ConcurrentHashMap.newKeySet();
    public ControlledConcurrency() { this(Duration.ofSeconds(5),Duration.ofSeconds(15),Duration.ofSeconds(5)); }
    ControlledConcurrency(Duration start,Duration execution,Duration shutdown) {
        startTimeout=start; executionTimeout=execution; shutdownTimeout=shutdown;
    }
    private record Indexed(int index,Object value) {}
    public List<Object> run(Callable<?> first,Callable<?> second) {
        assertQuiescent();
        AtomicInteger sequence=new AtomicInteger();
        ExecutorService pool=Executors.newFixedThreadPool(2, task -> {
            Thread thread=new Thread(task,"controlled-mysql-worker-"+sequence.incrementAndGet());
            thread.setDaemon(true); return thread;
        });
        executors.add(pool);
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        var completion=new ExecutorCompletionService<Indexed>(pool);
        List<Future<Indexed>> futures=new ArrayList<>();
        AssertionError failure=null;
        Object[] result=new Object[2];
        boolean interrupted=false;
        try {
            List<Callable<?>> tasks=List.of(first,second);
            for (int i=0;i<tasks.size();i++) {
                final int index=i;
                futures.add(completion.submit(() -> {
                    ready.countDown();
                    if (!start.await(startTimeout.toMillis(),TimeUnit.MILLISECONDS)) throw new TimeoutException("Start barrier");
                    return new Indexed(index,tasks.get(index).call());
                }));
            }
            if (!ready.await(startTimeout.toMillis(),TimeUnit.MILLISECONDS)) throw new TimeoutException("Workers not ready");
            long deadline=System.nanoTime()+executionTimeout.toNanos();
            start.countDown();
            for (int i=0;i<2;i++) {
                Future<Indexed> completed=completion.poll(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                if (completed==null) throw new TimeoutException("Concurrent execution deadline");
                Indexed item=completed.get(); result[item.index()]=item.value();
            }
        } catch (InterruptedException stopped) {
            interrupted=true; failure=new AssertionError("Concurrent test interrupted; pending tasks cancelled");
        } catch (TimeoutException timeout) {
            failure=new AssertionError("Concurrent test timeout: "+timeout.getMessage()+"; pending tasks cancelled");
        } catch (ExecutionException worker) {
            failure=new AssertionError(describe(worker.getCause()));
        } finally {
            start.countDown();
            futures.forEach(task -> { if (!task.isDone()) task.cancel(true); });
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(shutdownTimeout.toMillis(),TimeUnit.MILLISECONDS)) {
                    UNSAFE_WORKER_LEFT_RUNNING.set(true);
                    var shutdownFailure=new AssertionError("Concurrent workers still active after cancellation; integrity is INDETERMINATE");
                    if (failure==null) failure=shutdownFailure; else failure.addSuppressed(shutdownFailure);
                }
            } catch (InterruptedException stopped) {
                interrupted=true;
                if (!pool.isTerminated()) UNSAFE_WORKER_LEFT_RUNNING.set(true);
                if (failure==null) failure=new AssertionError("Interrupted while stopping workers; integrity is INDETERMINATE");
            }
            if (pool.isTerminated()) executors.remove(pool);
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (failure!=null) throw failure;
        return Arrays.asList(result);
    }
    public void assertQuiescent() {
        if (UNSAFE_WORKER_LEFT_RUNNING.get() || executors.stream().anyMatch(pool -> !pool.isTerminated()))
            throw new AssertionError("Workers still active; do not certify integrity or start another scenario");
    }
    static String describe(Throwable failure) {
        for (Throwable cause=failure;cause!=null;cause=cause.getCause()) if (cause instanceof SQLException sql) {
            String kind=sql.getErrorCode()==1213 ? "deadlock" : sql.getErrorCode()==1205 ? "lock timeout" : "SQL failure";
            return "Concurrent "+kind+" (SQLState="+sql.getSQLState()+", MySQL="+sql.getErrorCode()+")";
        }
        return "Concurrent worker failed ("+failure.getClass().getSimpleName()+"); request and exception bodies omitted";
    }
}
