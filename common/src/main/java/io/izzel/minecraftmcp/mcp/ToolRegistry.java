package io.izzel.minecraftmcp.mcp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        McpTool tool = find(name).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
        return tool.call(args == null ? Map.of() : args);
    }
}
