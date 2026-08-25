package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.config.ChannelCaller;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.config.McpConfigs;
import io.izzel.minecraftmcp.mcp.McpTools;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.serverlink.RemoteMcpProxies;
import io.izzel.minecraftmcp.serverlink.RemoteMcpProxy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.izzel.minecraftmcp.mcp.McpTools.simple;

public final class BuiltinRemoteTools {
    public interface ClientSender {
        void send(UUID player, String payload);
    }

    public record ClientPeer(ChannelCaller.Player caller, boolean reachable) {
    }

    public interface ClientPeers {
        ClientPeer resolve(UUID player);
    }

    private BuiltinRemoteTools() {
    }

    public static void registerClient(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(simple("mc.remote.call", "Call a tool on the connected server over the plugin channel", args -> {
            String tool = String.valueOf(args.getOrDefault("tool", ""));
            long timeoutMs = McpTools.longArg(args, "timeoutMs", 30000L);
            return bridge.serverMcpCall(tool, arguments(args), timeoutMs);
        }));
        registry.register(simple("mc.remote.state", "Report whether the server is reachable over the plugin channel",
            args -> bridge.submit(() -> {
                boolean remote = bridge.remoteMcpAvailable();
                boolean integrated = bridge.integratedServerAvailable();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("side", "client");
                result.put("available", remote);
                result.put("integratedServer", integrated);
                result.put("kind", remote ? "plugin-channel" : integrated ? "integrated" : "none");
                return result;
            }).get(10, TimeUnit.SECONDS)));
    }

    public static void registerServer(ToolRegistry registry, MinecraftServerBridge bridge,
                                      ClientSender sender, ClientPeers peers) {
        registry.register(simple("mc.remote.call", "Call a tool on one connected player's client over the plugin channel", args -> {
            String tool = String.valueOf(args.getOrDefault("tool", ""));
            long timeoutMs = McpTools.longArg(args, "timeoutMs", 30000L);
            UUID target = resolvePlayer(args);
            ClientPeer peer = bridge.submit(() -> peers.resolve(target)).get(10, TimeUnit.SECONDS);
            if (peer == null) {
                throw new IllegalStateException("player " + args.get("player") + " is not connected");
            }

            McpConfig config = McpConfigs.current();
            if (!config.trusts(peer.caller())) {
                throw new IllegalStateException(config.refusal(peer.caller()));
            }
            if (!peer.reachable()) {
                throw new IllegalStateException("player " + peer.caller().name()
                    + " has not registered the MCP plugin channel; its client does not have this mod");
            }
            RemoteMcpProxy proxy = RemoteMcpProxies.findClient(target);
            if (proxy == null) {
                throw new IllegalStateException("player " + peer.caller().name() + " is not known to this server");
            }
            return proxy.call(tool, arguments(args), payload -> sender.send(target, payload), timeoutMs);
        }));
        registry.register(simple("mc.remote.state", "List clients reachable over the plugin channel", args -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("side", "server");
            result.put("clients", bridge.submit(() -> RemoteMcpProxies.clientSummaries(uuid -> {
                ClientPeer peer = peers.resolve(uuid);
                return peer != null && peer.reachable();
            })).get(10, TimeUnit.SECONDS));
            return result;
        }));
    }

    private static UUID resolvePlayer(Map<String, Object> args) {
        Object value = args.get("player");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("player is required on a server endpoint (name or uuid)");
        }
        String player = String.valueOf(value);
        UUID byName = RemoteMcpProxies.findClientByName(player);
        if (byName != null) {
            return byName;
        }
        try {
            return UUID.fromString(player);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("no connected client known as " + player);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(Map<String, Object> args) {
        Object raw = args.get("arguments");
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
