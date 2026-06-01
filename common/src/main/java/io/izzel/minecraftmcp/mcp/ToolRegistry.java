package io.izzel.minecraftmcp.mcp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public final class ToolRegistry {
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    public void register(McpTool tool) { tools.put(tool.name(), tool); }
    public Optional<McpTool> find(String name) { return Optional.ofNullable(tools.get(name)); }
    public List<Map<String,Object>> listTools() {
        return tools.values().stream().sorted(Comparator.comparing(McpTool::name)).map(t -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name", t.name()); m.put("description", t.description()); m.put("inputSchema", t.inputSchema()); return m;
        }).toList();
    }
    public Object call(String name, Map<String,Object> args) throws Exception {
        Map<String,Object> safeArgs = args == null ? Map.of() : args;
        McpTool tool = find(name).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
        Object result = tool.call(safeArgs);
        if (result instanceof FutureResult<?> futureResult) {
            long timeoutMs = timeoutMs(safeArgs);
            try {
                return futureResult.await(timeoutMs);
            } catch (TimeoutException e) {
                futureResult.cancel();
                throw e;
            }
        }
        return result;
    }

    private static long timeoutMs(Map<String,Object> args) {
        Object value = args.getOrDefault("timeoutMs", 10000L);
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        return Long.parseLong(String.valueOf(value));
    }
}
