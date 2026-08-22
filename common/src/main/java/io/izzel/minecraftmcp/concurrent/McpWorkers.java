package io.izzel.minecraftmcp.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public interface McpWorkers extends Executor, AutoCloseable {
    @Override
    void execute(Runnable task);

    @Override
    void close();

    static McpWorkers pooled(String name) {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService delegate = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        return new McpWorkers() {
            @Override
            public void execute(Runnable task) {
                delegate.execute(task);
            }

            @Override
            public void close() {
                delegate.shutdownNow();
            }

            @Override
            public String toString() {
                return "McpWorkers.pooled(" + name + ")";
            }
        };
    }

    static McpWorkers direct() {
        return new McpWorkers() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return "McpWorkers.direct()";
            }
        };
    }
}
