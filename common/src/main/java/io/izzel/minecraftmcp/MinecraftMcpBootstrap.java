package io.izzel.minecraftmcp;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.config.MinecraftMcpConfig;
import io.izzel.minecraftmcp.mcp.*;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;
import io.izzel.minecraftmcp.tools.BuiltinTools;

public final class MinecraftMcpBootstrap {
    private MinecraftMcpBootstrap() {}
    public static LocalHttpMcpServer start(MinecraftClientBridge bridge) throws Exception {
        MinecraftMcpConfig config = MinecraftMcpConfig.load();
        ToolRegistry registry = new ToolRegistry();
        ScenarioEngine scenarios = new ScenarioEngine(registry);
        BuiltinTools.register(registry, bridge, scenarios);
        LocalHttpMcpServer server = new LocalHttpMcpServer(config, new JsonRpcHandler(registry));
        server.start(bridge.gameDirectory(), bridge.loader(), bridge.minecraftVersion());
        if (!config.scenarioDir().isBlank()) {
            new Thread(() -> {
                try {
                    scenarios.runBatch(config.scenarioDir(), ScenarioRunOptions.builder().loader(bridge.loader()).build());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (config.batchExit()) {
                        bridge.execute(bridge::shutdownClient);
                    }
                }
            }, "minecraft-mcp-scenario-batch").start();
        }
        return server;
    }
}
