package io.izzel.minecraftmcp.config;

public final class McpPermissions {
    public static final String NAMESPACE = "minecraft_mcp";

    public static final String REMOTE_CALL_PATH = "remote.call";

    public static final String REMOTE_CALL_NEOFORGE = NAMESPACE + "." + REMOTE_CALL_PATH;

    public static final String REMOTE_CALL_FABRIC = NAMESPACE + ":" + REMOTE_CALL_PATH;

    private McpPermissions() {
    }
}
