package model;

import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.*;

public class Debounce {
    private final ScheduledExecutorService scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("Debounce", 1);
    private final ConcurrentHashMap<Object, Future<?>> delayedMap = new ConcurrentHashMap<>();

    /**
     * Debounce {@code callable} by {@code delay}, i.e., schedules it to be executed after {@code delay},
     * or cancels its execution if the method is called with the same key within the {@code delay} again.
     */
    public void debounce(final Object key, final Runnable runnable, long delay, TimeUnit unit) {
        final Future<?> prev = delayedMap.put(key, scheduler.schedule(() -> {
            try {
                runnable.run();
            } finally {
                delayedMap.remove(key);
            }
        }, delay, unit));
        if (prev != null) {
            prev.cancel(true);
        }
    }

    /**
     * Shuts down the scheduler and cancels all pending tasks.
     * Should be called when the Debounce instance is no longer needed.
     */
    public void shutdown() {
        // Cancel all pending futures
        for (Future<?> future : delayedMap.values()) {
            future.cancel(true);
        }
        delayedMap.clear();

        // Shutdown the scheduler
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
