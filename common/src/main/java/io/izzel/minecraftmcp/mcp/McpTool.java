package io.izzel.minecraftmcp.mcp;

import java.util.Map;

public interface McpTool {
    String name();

    String description();

    Map<String, Object> inputSchema();

    Object call(Map<String, Object> arguments) throws Exception;

    /** Throws IllegalArgumentException for arguments this tool would refuse, without running it. */
    default void checkArguments(Map<String, Object> arguments) {
    }
}
