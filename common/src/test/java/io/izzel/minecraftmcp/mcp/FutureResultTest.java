package io.izzel.minecraftmcp.mcp;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FutureResultTest {
    @Test
    void callbacksRunOnProvidedExecutorAndAwaitReturnsMappedValue() throws Exception {
        Queue<Runnable> mainThread = new ArrayDeque<>();
        AtomicReference<String> thread = new AtomicReference<>("none");

        FutureResult<Integer> result = FutureResult.completed(2, mainThread::add)
                .thenApply(value -> {
                    thread.set("main");
                    return value + 3;
                });

        assertEquals("none", thread.get());
        while (!mainThread.isEmpty()) mainThread.remove().run();
        assertEquals(5, result.await(1000));
        assertEquals("main", thread.get());
    }

    @Test
    void awaitUsesUnifiedTimeout() {
        FutureResult<String> result = new FutureResult<>(mainThread -> {}, Runnable::run);
        assertThrows(TimeoutException.class, () -> result.await(1));
    }

    @Test
    void cancelUsesCompletableFutureCancellationResultAndState() {
        FutureResult<String> result = FutureResult.completed("done", Runnable::run);

        assertFalse(result.cancel());
        assertFalse(result.isCancelled());
    }

    @Test
    void cancelPropagatesThroughCompletableFutureCallbacks() {
        FutureResult<String> result = new FutureResult<>(Runnable::run);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        result.toCompletableFuture().whenComplete((value, throwable) -> cancelled.set(result.toCompletableFuture().isCancelled()));

        assertTrue(result.cancel());
        assertTrue(cancelled.get());
        assertTrue(result.isCancelled());
    }
}
