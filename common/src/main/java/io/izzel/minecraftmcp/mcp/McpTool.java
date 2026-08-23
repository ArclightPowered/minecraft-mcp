package io.izzel.minecraftmcp.mcp;

import java.util.Map;

public interface McpTool {
    String name();

    String description();

    Map<String, Object> inputSchema();

    Object call(Map<String, Object> arguments) throws Exception;
}
