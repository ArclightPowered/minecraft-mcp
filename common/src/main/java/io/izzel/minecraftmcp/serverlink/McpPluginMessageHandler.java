package io.izzel.minecraftmcp.serverlink;

import io.izzel.minecraftmcp.config.ChannelCaller;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.json.Json;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public final class McpPluginMessageHandler {
    public interface Sender {
        void send(String payload);
    }

    private final ToolRegistry tools;
    private final Supplier<McpConfig> policy;
    private final Executor workers;

    public McpPluginMessageHandler(ToolRegistry tools, Supplier<McpConfig> policy, Executor workers) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.workers = Objects.requireNonNull(workers, "workers");
    }

    public void receiveRequest(String payload, ChannelCaller caller, Sender sender) {
        McpConfig config = policy.get();
        boolean trusted = config.trusts(caller);
        String denial = trusted ? null : config.refusal(caller);

        try {
            workers.execute(() -> answer(payload, trusted, denial, sender));
        } catch (RejectedExecutionException e) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "response");
            response.put("ok", false);
            response.put("error", "this endpoint is shutting down");
            sender.send(Json.stringify(response));
        }
    }

    public void receiveResponse(String payload, ChannelCaller caller) {
        if (!policy.get().trusts(caller)) {
            return;
        }
        RemoteMcpProxy proxy = proxyFor(caller);
        if (proxy == null) {
            return;
        }
        try {
            workers.execute(() -> proxy.receiveResponse(payload));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static RemoteMcpProxy proxyFor(ChannelCaller caller) {
        return switch (caller) {
            case ChannelCaller.Player player -> RemoteMcpProxies.findClient(player.id());
            case ChannelCaller.Server ignored -> RemoteMcpProxies.toServer();
            case ChannelCaller.Local ignored -> RemoteMcpProxies.toServer();
        };
    }

    @SuppressWarnings("unchecked")
    private void answer(String payload, boolean trusted, String denial, Sender sender) {
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
        if (!"request".equals(String.valueOf(message.get("type")))) {
            return;
        }
        Object id = message.get("id");
        String tool = String.valueOf(message.get("tool"));
        Map<String, Object> arguments = message.get("arguments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "response");
        response.put("id", id);
        if (!trusted) {
            response.put("ok", false);
            response.put("error", denial);
        } else {
            try {
                response.put("result", tools.call(tool, arguments));
                response.put("ok", true);
            } catch (Exception e) {
                response.put("ok", false);
                response.put("error", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            }
        }
        sender.send(Json.stringify(response));
    }
}
