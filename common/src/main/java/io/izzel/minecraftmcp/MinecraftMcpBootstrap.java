package io.izzel.minecraftmcp;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.concurrent.McpWorkers;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.config.McpConfigs;
import io.izzel.minecraftmcp.mcp.*;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.server.IntegratedServerBridge;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;
import io.izzel.minecraftmcp.tools.BuiltinClientTools;
import io.izzel.minecraftmcp.tools.BuiltinCommonTools;
import io.izzel.minecraftmcp.tools.BuiltinRemoteTools;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;

public final class MinecraftMcpBootstrap {
    private MinecraftMcpBootstrap() {
    }

    public record McpEndpoint(LocalHttpMcpServer server, McpWorkers workers) implements AutoCloseable {
        public int port() {
            return server.port();
        }

        @Override
        public void close() {
            server.close();
            workers.close();
        }
    }

    public static McpEndpoint start(MinecraftClientBridge bridge) throws Exception {
        McpConfig config = McpConfigs.current();
        ToolRegistry registry = new ToolRegistry(McpConfigs::current);
        ScenarioEngine scenarios = new ScenarioEngine(registry);
        BuiltinCommonTools.register(registry, bridge, scenarios);
        BuiltinClientTools.register(registry, bridge);
        BuiltinRemoteTools.registerClient(registry, bridge);
        registerIntegratedServerTools(registry, bridge);
        McpWorkers workers = McpWorkers.pooled("minecraft-mcp-" + bridge.side());
        LocalHttpMcpServer server = new LocalHttpMcpServer(config, new JsonRpcHandler(registry), workers);
        try {
            server.start(bridge.gameDirectory(), bridge.loader(), bridge.minecraftVersion());
        } catch (Exception e) {
            workers.close();
            throw e;
        }
        if (!config.scenarioDir().isBlank()) {
            new Thread(() -> {
                try {
                    scenarios.runBatch(config.scenarioDir(), ScenarioRunOptions.builder()
                        .loader(bridge.loader()).side(bridge.side()).build());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (config.batchExit()) {
                        bridge.execute(bridge::shutdownClient);
                    }
                }
            }, "minecraft-mcp-scenario-batch").start();
        }
        return new McpEndpoint(server, workers);
    }

    static void registerIntegratedServerTools(ToolRegistry registry, MinecraftClientBridge bridge) {
        IntegratedServerBridge integrated = new IntegratedServerBridge(bridge);
        ToolRegistry serverTools = new ToolRegistry();
        BuiltinServerTools.register(serverTools, integrated);
        for (McpTool tool : serverTools.allTools()) {
            if (!tool.name().startsWith("mc.server.")) {
                continue;
            }
            registry.register(McpTools.simple(tool.name(), tool.description(), tool.inputSchema(), args -> {
                if (!integrated.present()) {
                    return java.util.Map.of(
                        "error", "no_local_server",
                        "side", "client",
                        "hint", "this process has no MinecraftServer; open a singleplayer world, "
                            + "or use mc.remote.call to reach the one you are connected to");
                }
                return tool.call(args);
            }));
        }
    }

    public static McpEndpoint start(MinecraftServerBridge bridge) throws Exception {
        McpConfig config = McpConfigs.current();
        ToolRegistry registry = new ToolRegistry(McpConfigs::current);
        ScenarioEngine scenarios = new ScenarioEngine(registry);
        BuiltinServerTools.register(registry, bridge, scenarios);
        McpWorkers workers = McpWorkers.pooled("minecraft-mcp-" + bridge.side());
        LocalHttpMcpServer server = new LocalHttpMcpServer(config, new JsonRpcHandler(registry), workers);
        try {
            server.start(bridge.gameDirectory(), bridge.loader(), bridge.minecraftVersion());
        } catch (Exception e) {
            workers.close();
            throw e;
        }
        return new McpEndpoint(server, workers);
    }
}
