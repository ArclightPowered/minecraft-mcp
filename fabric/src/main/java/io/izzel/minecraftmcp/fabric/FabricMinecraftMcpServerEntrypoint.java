package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.MinecraftMcpBootstrap;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.MinecraftMcpBootstrap.McpEndpoint;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.serverlink.ServerMcpPluginMessageHandler;
import io.izzel.minecraftmcp.serverlink.ServerMcpProxy;
import io.izzel.minecraftmcp.schematic.ServerSchematicTools;
import io.izzel.minecraftmcp.server.ServerWorldTools;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FabricMinecraftMcpServerEntrypoint implements DedicatedServerModInitializer {
    private static McpEndpoint mcpServer;
    private static ServerMcpPluginMessageHandler pluginHandler;

    @Override
    public void onInitializeServer() {
        FabricMcpConfig.bootstrap();
        PayloadTypeRegistry.serverboundPlay().register(FabricStringPayload.REQUEST, FabricStringPayload.codec(FabricStringPayload.REQUEST));
        PayloadTypeRegistry.clientboundPlay().register(FabricStringPayload.RESPONSE, FabricStringPayload.codec(FabricStringPayload.RESPONSE));
        PayloadTypeRegistry.clientboundPlay().register(FabricStringPayload.HELLO, FabricStringPayload.codec(FabricStringPayload.HELLO));
        ServerPlayNetworking.registerGlobalReceiver(FabricStringPayload.REQUEST, (payload, context) -> pluginHandler.receive(payload.text(), response -> ServerPlayNetworking.send(context.player(), new FabricStringPayload(FabricStringPayload.RESPONSE, response))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, new FabricStringPayload(FabricStringPayload.HELLO, ServerMcpProxy.hello())));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                FabricServerBridge bridge = new FabricServerBridge(server);
                mcpServer = MinecraftMcpBootstrap.start(bridge);
                ToolRegistry pluginRegistry = new ToolRegistry();
                BuiltinServerTools.register(pluginRegistry, bridge);
                pluginHandler = new ServerMcpPluginMessageHandler(pluginRegistry);
                server.getPlayerList().getPlayers().forEach(player -> ServerPlayNetworking.send(player, new FabricStringPayload(FabricStringPayload.HELLO, ServerMcpProxy.hello())));
                System.out.println("[Minecraft MCP] Fabric dedicated MCP server started on port " + mcpServer.port());
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Minecraft MCP dedicated server", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (mcpServer != null) {
                mcpServer.close();
            }
        });
    }

    static final class FabricServerBridge implements MinecraftServerBridge {
        private final MinecraftServer server;
        FabricServerBridge(MinecraftServer server) { this.server = server; }
        public String loader() { return "fabric"; }
        public String minecraftVersion() { return server.getServerVersion(); }
        public Path gameDirectory() { return server.getServerDirectory(); }
        public boolean isOnServerThread() { return server.isSameThread(); }
        public void execute(Runnable runnable) { server.execute(runnable); }
        public Map<String, Object> serverState() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("running", server.isRunning());
            result.put("dedicated", server.isDedicatedServer());
            result.put("motd", server.getMotd());
            result.put("players", server.getPlayerList().getPlayerCount());
            result.put("maxPlayers", server.getPlayerList().getMaxPlayers());
            result.put("version", server.getServerVersion());
            result.put("overworldTime", server.overworld().getGameTime());
            return result;
        }
        public boolean dedicated() { return server.isDedicatedServer(); }
        public Map<String, Object> runCommand(String command, String asPlayer) { return ServerWorldTools.runCommand(server, command, asPlayer); }
        public Map<String, Object> players() { return ServerWorldTools.players(server); }
        public Map<String, Object> playerState(String who) { return ServerWorldTools.playerState(server, who); }
        public Map<String, Object> playerInventory(String who) { return ServerWorldTools.playerInventory(server, who); }
        public Map<String, Object> connectionList() { return ServerWorldTools.connectionList(server); }
        public Map<String, Object> worldList() { return ServerWorldTools.worldList(server); }
        public Map<String, Object> worldSnapshot(String dimension) { return ServerWorldTools.worldSnapshot(server, dimension); }
        public Map<String, Object> blockAt(String dimension, int x, int y, int z) { return ServerWorldTools.blockAt(server, dimension, x, y, z); }
        public Map<String, Object> setBlock(String dimension, int x, int y, int z, String blockState) { return ServerWorldTools.setBlock(server, dimension, x, y, z, blockState); }
        public Map<String, Object> entityQuery(Map<String, Object> args) { return ServerWorldTools.entityQuery(server, args); }
        public Map<String, Object> chunkState(String dimension, int chunkX, int chunkZ) { return ServerWorldTools.chunkState(server, dimension, chunkX, chunkZ); }
        public Map<String, Object> tickStats() { return ServerWorldTools.tickStats(server); }
        public Map<String, Object> tailLog(int lines) { return ServerWorldTools.tailLog(gameDirectory(), lines); }
        public Map<String, Object> exportSchematic(Map<String, Object> args) { return ServerSchematicTools.exportSchematic(server.overworld(), gameDirectory(), args); }
        public Map<String, Object> pasteSchematic(Map<String, Object> args) { return ServerSchematicTools.pasteSchematic(server.overworld(), gameDirectory(), args); }
        public void shutdownServer() { server.halt(false); }
    }
}
