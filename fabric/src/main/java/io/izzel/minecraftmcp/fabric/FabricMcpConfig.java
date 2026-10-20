package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.compat.MinecraftCompat;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.config.McpConfigDocument;
import io.izzel.minecraftmcp.config.McpConfigs;
import io.izzel.minecraftmcp.config.McpOptions;
import io.izzel.minecraftmcp.config.McpPermissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

final class FabricMcpConfig {
    static final String FILE_NAME = "minecraft-mcp.json";

    // 26.1 brought a permission API into Fabric API; before that fabric-permissions-api (bundled) provides it.
    //? if >=26.1 {
    /*static final net.fabricmc.fabric.api.permission.v1.PermissionNode<Boolean> REMOTE_CALL =
        net.fabricmc.fabric.api.permission.v1.PermissionNode.of(net.minecraft.resources.Identifier.fromNamespaceAndPath(
            McpPermissions.NAMESPACE, McpPermissions.REMOTE_CALL_PATH));

    private static final Predicate<ServerPlayer> MAY_REMOTE_CALL =
        net.fabricmc.fabric.api.permission.v1.PermissionPredicates.require(REMOTE_CALL,
            net.minecraft.server.permissions.PermissionLevel.OWNERS);
    *///?} else {
    private static final Predicate<ServerPlayer> MAY_REMOTE_CALL =
        player -> me.lucko.fabric.api.permissions.v0.Permissions.check(player, McpPermissions.REMOTE_CALL_FABRIC, 4);
    //?}

    private static boolean loaded;

    private FabricMcpConfig() {
    }

    static synchronized void bootstrap() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        writeDefaultIfAbsent(file);
        McpConfigs.install(new McpConfig(readOptions(file), "config/" + FILE_NAME));
        McpConfigs.report();
    }

    private static McpOptions readOptions(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Minecraft MCP] cannot read " + file + "; falling back to defaults: " + e);
            return McpConfigDocument.empty();
        }
        try {
            return McpConfigDocument.read(text);
        } catch (RuntimeException e) {
            System.err.println("[Minecraft MCP] " + file + " is not valid JSON (" + e.getMessage()
                + "); falling back to defaults, so no caller will be trusted");
            return McpConfigDocument.empty();
        }
    }

    private static void writeDefaultIfAbsent(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, McpConfigDocument.defaultDocument(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Minecraft MCP] could not write the default config to " + file + ": " + e);
        }
    }

    static boolean mayRemoteCall(ServerPlayer player) {
        try {
            return MAY_REMOTE_CALL.test(player);
        } catch (RuntimeException e) {
            System.err.println("[Minecraft MCP] could not resolve " + McpPermissions.REMOTE_CALL_FABRIC
                + " for " + MinecraftCompat.profileName(player) + ", refusing: " + e);
            return false;
        }
    }
}
