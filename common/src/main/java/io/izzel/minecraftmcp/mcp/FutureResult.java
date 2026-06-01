package io.izzel.minecraftmcp.mcp;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FutureResult<T> {
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final Executor callbackExecutor;

    public FutureResult(Executor callbackExecutor) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    public FutureResult(Consumer<Runnable> starter, Executor callbackExecutor) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        Objects.requireNonNull(starter, "starter").accept(() -> {});
    }

    private FutureResult(Executor callbackExecutor, boolean ignored) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    public static <T> FutureResult<T> completed(T value, Executor callbackExecutor) {
        FutureResult<T> result = new FutureResult<>(callbackExecutor, true);
        result.complete(value);
        return result;
    }

    public static <T> FutureResult<T> failed(Throwable throwable, Executor callbackExecutor) {
        FutureResult<T> result = new FutureResult<>(callbackExecutor, true);
        result.completeExceptionally(throwable);
        return result;
    }

    public static <T> FutureResult<T> supply(Executor executor, Executor callbackExecutor, Callable<T> callable) {
        FutureResult<T> result = new FutureResult<>(callbackExecutor, true);
        executor.execute(() -> {
            if (result.isCancelled()) return;
            try {
                result.complete(callable.call());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    public boolean complete(T value) {
        return future.complete(value);
    }

    public boolean completeExceptionally(Throwable throwable) {
        return future.completeExceptionally(throwable);
    }

    public T await(long timeoutMs) throws Exception {
        try {
            return future.get(Math.max(0, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new RuntimeException(cause);
        }
    }

    public <U> FutureResult<U> thenApply(Function<T, U> fn) {
        FutureResult<U> next = new FutureResult<>(callbackExecutor, true);
        future.whenComplete((value, throwable) -> callbackExecutor.execute(() -> {
            if (throwable != null) {
                next.completeExceptionally(unwrapCompletion(throwable));
                return;
            }
            if (next.isCancelled()) return;
            try {
                next.complete(fn.apply(value));
            } catch (Throwable t) {
                next.completeExceptionally(t);
            }
        }));
        next.toCompletableFuture().whenComplete((value, throwable) -> {
            if (next.toCompletableFuture().isCancelled()) this.cancel();
        });
        return next;
    }

    public FutureResult<T> thenAccept(Consumer<T> consumer) {
        return thenApply(value -> {
            consumer.accept(value);
            return value;
        });
    }

    public boolean cancel() {
        return future.cancel(true);
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    private static Throwable unwrapCompletion(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (throwable instanceof ExecutionException ee && ee.getCause() != null) return ee.getCause();
        return throwable;
    }
}
