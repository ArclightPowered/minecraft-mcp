package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftBridge;
import io.izzel.minecraftmcp.mcp.Param;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;
import io.izzel.minecraftmcp.schematic.ServerSchematicTools;

import java.util.List;
import java.util.Map;

import static io.izzel.minecraftmcp.mcp.McpTools.tool;

public final class BuiltinCommonTools {
    private BuiltinCommonTools() {
    }

    public static void register(ToolRegistry registry, MinecraftBridge bridge, ScenarioEngine scenarios) {
        registry.register(tool("mc.debug.capabilities", "Return MCP capabilities for this endpoint", args -> bridge.capabilities()));
        registry.register(tool("mc.schematic.info", "Read Sponge v3 schematic metadata", List.of(ServerSchematicTools.PATH),
            args -> bridge.schematicInfo(args)));
        Param<String> directory = Param.string("directory", "absolute path of a scenario directory, searched recursively").required();
        Param<List<?>> includeTags = Param.array("includeTags", "only scenarios with one of these tags");
        Param<List<?>> excludeTags = Param.array("excludeTags", "skip scenarios with these tags");
        registry.register(tool("mc.scenario.batch.run", "Run scenarios from a directory", List.of(directory, includeTags, excludeTags), args -> {
            ScenarioRunOptions.Builder options = ScenarioRunOptions.builder()
                .loader(bridge.loader())
                .side(bridge.side());
            addTags(includeTags.get(args), true, options);
            addTags(excludeTags.get(args), false, options);
            return scenarios.runBatch(directory.get(args), options.build()).toMap();
        }));
        registry.register(tool("mc.scenario.report", "Return latest scenario report",
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
