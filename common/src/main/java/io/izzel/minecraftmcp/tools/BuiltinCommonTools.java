package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;

import java.util.Map;

import static io.izzel.minecraftmcp.mcp.McpTools.simple;

public final class BuiltinCommonTools {
    private BuiltinCommonTools() {
    }

    public static void register(ToolRegistry registry, MinecraftBridge bridge, ScenarioEngine scenarios) {
        registry.register(simple("mc.debug.capabilities", "Return MCP capabilities for this endpoint", args -> bridge.capabilities()));
        registry.register(simple("mc.schematic.info", "Read Sponge v3 schematic metadata", args -> bridge.schematicInfo(args)));
        registry.register(simple("mc.scenario.batch.run", "Run scenarios from a directory", args -> {
            ScenarioRunOptions.Builder options = ScenarioRunOptions.builder()
                .loader(bridge.loader())
                .side(bridge.side());
            addTags(args.get("includeTags"), true, options);
            addTags(args.get("excludeTags"), false, options);
            return scenarios.runBatch(String.valueOf(args.getOrDefault("directory", "")), options.build()).toMap();
        }));
        registry.register(simple("mc.scenario.report", "Return latest scenario report",
            args -> scenarios.latestReport().map(report -> report.toMap()).orElse(Map.of("status", "none"))));
    }

    private static void addTags(Object value, boolean include, ScenarioRunOptions.Builder options) {
        if (value instanceof Iterable<?> iterable) {
            for (Object tag : iterable) {
                if (include) {
                    options.includeTags(String.valueOf(tag));
                } else {
                    options.excludeTags(String.valueOf(tag));
                }
            }
        } else if (value instanceof String tag && !tag.isBlank()) {
            if (include) {
                options.includeTags(tag);
            } else {
                options.excludeTags(tag);
            }
        }
    }
}
