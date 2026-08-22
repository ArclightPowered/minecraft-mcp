package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.MinecraftMcpBootstrap;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.serverlink.ServerMcpPluginMessageHandler;
import io.izzel.minecraftmcp.serverlink.ServerMcpProxy;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import io.izzel.minecraftmcp.schematic.ServerSchematicTools;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.file.Path;
import java.util.Map;

@Mod("minecraft_mcp")
public final class NeoForgeMinecraftMcpMod {

    static final ServerMcpProxy SERVER_PROXY = new ServerMcpProxy();

    private static MinecraftMcpBootstrap.McpEndpoint server;
    private static ServerMcpPluginMessageHandler pluginHandler;
    public NeoForgeMinecraftMcpMod(IEventBus modBus, ModContainer container) {
        NeoForgeMcpConfig.register(modBus, container);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(NeoForgeStringPayload.REQUEST, NeoForgeStringPayload.codec(NeoForgeStringPayload.REQUEST), (payload, context) -> {
            if (pluginHandler != null) {
                pluginHandler.receive(payload.text(), response -> context.reply(new NeoForgeStringPayload(NeoForgeStringPayload.RESPONSE, response)));
            }
        });
        registrar.playToClient(NeoForgeStringPayload.RESPONSE, NeoForgeStringPayload.codec(NeoForgeStringPayload.RESPONSE), (payload, context) -> SERVER_PROXY.receive(payload.text()));
        registrar.playToClient(NeoForgeStringPayload.HELLO, NeoForgeStringPayload.codec(NeoForgeStringPayload.HELLO), (payload, context) -> SERVER_PROXY.receive(payload.text()));
    }

    private void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) return;
        try {
            NeoForgeServerBridge bridge = new NeoForgeServerBridge(event.getServer());
            server = MinecraftMcpBootstrap.start(bridge);
            ToolRegistry pluginRegistry = new ToolRegistry();
            BuiltinServerTools.register(pluginRegistry, bridge);
            pluginHandler = new ServerMcpPluginMessageHandler(pluginRegistry);
            event.getServer().getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, new NeoForgeStringPayload(NeoForgeStringPayload.HELLO, ServerMcpProxy.hello())));
            System.out.println("[Minecraft MCP] NeoForge dedicated MCP server started on port " + server.port());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Minecraft MCP dedicated server", e);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (server != null) server.close();
    }
    static final class NeoForgeServerBridge implements MinecraftServerBridge {
        private final MinecraftServer server;
        NeoForgeServerBridge(MinecraftServer server) { this.server = server; }
        public String loader() { return "neoforge-server"; }
        public String minecraftVersion() { return server.getServerVersion(); }
        public Path gameDirectory() { return server.getServerDirectory(); }
        public boolean isOnServerThread() { return server.isSameThread(); }
        public void execute(Runnable runnable) { server.execute(runnable); }
        public Map<String, Object> serverState() {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("running", server.isRunning());
            result.put("dedicated", server.isDedicatedServer());
            result.put("motd", server.getMotd());
            result.put("players", server.getPlayerList().getPlayerCount());
            result.put("maxPlayers", server.getPlayerList().getMaxPlayers());
            result.put("version", server.getServerVersion());
            result.put("overworldTime", server.overworld().getGameTime());
            return result;
        }
        public Map<String, Object> runCommand(String command) {
            String normalized = command.startsWith("/") ? command : "/" + command;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), normalized);
            return Map.of("status", "executed", "command", command);
        }
        public Map<String, Object> exportSchematic(Map<String, Object> args) { return ServerSchematicTools.exportSchematic(server.overworld(), gameDirectory(), args); }
        public Map<String, Object> pasteSchematic(Map<String, Object> args) { return ServerSchematicTools.pasteSchematic(server.overworld(), gameDirectory(), args); }
        public void shutdownServer() { server.halt(false); }
    }
}
