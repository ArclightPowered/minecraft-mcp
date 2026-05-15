package io.izzel.minecraftmcp.serverlink;

import io.izzel.minecraftmcp.json.Json;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerMcpPluginMessageHandler {
    public interface Sender { void send(String payload); }

    private final ToolRegistry tools;

    public ServerMcpPluginMessageHandler(ToolRegistry tools) {
        this.tools = tools;
    }

    @SuppressWarnings("unchecked")
    public void receive(String payload, Sender sender) {
        Object parsed = Json.parse(payload);
        if (!(parsed instanceof Map<?, ?> raw)) return;
        Map<String, Object> message = (Map<String, Object>) raw;
        if (!"request".equals(String.valueOf(message.get("type")))) return;
        Object id = message.get("id");
        String tool = String.valueOf(message.get("tool"));
        Map<String, Object> arguments = message.get("arguments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "response");
        response.put("id", id);
        try {
            response.put("result", tools.call(tool, arguments));
            response.put("ok", true);
        } catch (Exception e) {
            response.put("ok", false);
            response.put("error", e.getMessage());
        }
        sender.send(Json.stringify(response));
    }
}
