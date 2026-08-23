package io.izzel.minecraftmcp.mcp;

import io.izzel.minecraftmcp.config.McpConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class ToolRegistry {
    private static final Supplier<McpConfig> NO_POLICY = () -> null;

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    private final Supplier<McpConfig> policy;

    public ToolRegistry() {
        this(NO_POLICY);
    }

    public ToolRegistry(Supplier<McpConfig> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void register(McpTool tool) {
        McpTool previous = tools.putIfAbsent(tool.name(), tool);
        if (previous != null) {
            throw new IllegalStateException("Duplicate tool: " + tool.name());
        }
    }

    public Optional<McpTool> find(String name) {
        if (disabled(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    public List<Map<String, Object>> listTools() {
        return tools.values().stream()
            .filter(tool -> !disabled(tool.name()))
            .sorted(Comparator.comparing(McpTool::name)).map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.name());
                m.put("description", t.description());
                m.put("inputSchema", t.inputSchema());
                return m;
            }).toList();
    }

    public Object call(String name, Map<String, Object> args) throws Exception {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        McpTool tool = find(name).orElseThrow(() -> new UnknownToolException(name));
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

    private boolean disabled(String name) {
        McpConfig config = policy.get();
        return config != null && config.toolDisabled(name);
    }

    private static long timeoutMs(Map<String, Object> args) {
        return Math.max(0L, McpTools.longArg(args, "timeoutMs", 10000L));
    }

    public static final class UnknownToolException extends IllegalArgumentException {
        private final String toolName;

        public UnknownToolException(String toolName) {
            super("Unknown tool: " + toolName);
            this.toolName = toolName;
        }

        public String toolName() {
            return toolName;
        }
    }
}
