package io.izzel.minecraftmcp.serverlink;

import io.izzel.minecraftmcp.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public final class RemoteMcpProxy {
    public interface Sender {
        void send(String payload);
    }

    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    public void failPending() {
        pending.forEach((id, future) ->
            future.completeExceptionally(new IllegalStateException("the MCP peer is no longer reachable")));
        pending.clear();
    }

    public Object call(String tool, Map<String, Object> arguments, Sender sender, long timeoutMs) throws Exception {
        long id = nextId.getAndIncrement();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(id, future);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "request");
        message.put("id", id);
        message.put("tool", tool);
        message.put("arguments", arguments == null ? Map.of() : arguments);
        try {
            sender.send(Json.stringify(message));
            return future.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new TimeoutException("Timed out waiting for the MCP peer's response to " + tool);
        } catch (Exception e) {
            pending.remove(id);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void receiveResponse(String payload) {
        Object parsed;
        try {
            parsed = Json.parse(payload);
        } catch (RuntimeException | StackOverflowError e) {
            return;
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> message = (Map<String, Object>) raw;
        if (!"response".equals(String.valueOf(message.get("type")))) {
            return;
        }
        Object idValue = message.get("id");
        if (!(idValue instanceof Number number)) {
            return;
        }
        CompletableFuture<Object> future = pending.remove(number.longValue());
        if (future == null) {
            return;
        }
        if (Boolean.TRUE.equals(message.get("ok"))) {
            future.complete(message.get("result"));
        } else {
            future.completeExceptionally(new IllegalStateException(Objects.toString(message.get("error"), "the MCP peer's request failed")));
        }
    }
}
