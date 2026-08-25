package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.config.DefaultOptions;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.config.McpConfigs;
import io.izzel.minecraftmcp.config.McpOptions;
import io.izzel.minecraftmcp.config.McpPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class NeoForgeMcpConfig {
    static final String FILE_NAME = "minecraft-mcp.toml";

    static final PermissionNode<Boolean> REMOTE_CALL = new PermissionNode<>(
        McpPermissions.NAMESPACE, McpPermissions.REMOTE_CALL_PATH, PermissionTypes.BOOLEAN,
        (player, uuid, context) -> player != null && player.permissions().hasPermission(Permissions.COMMANDS_OWNER))
        .setInformation(
            Component.literal("Minecraft MCP remote call"),
            Component.literal("Exchange MCP tool calls with this server over the plugin channel, "
                + "in either direction."));

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Map<List<String>, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();
    static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Minecraft MCP configuration.",
                "Every setting can also come from -DminecraftMcp.<path> or MINECRAFT_MCP_<PATH>,",
                "which take priority over this file. The startup log says which one won.",
                "See docs/protocol.md.")
            .push("endpoint");
        define(BUILDER.comment("Address the local HTTP endpoint binds to.",
                "Requires a game restart: the server has already bound.")
            .translation("minecraft_mcp.configuration.endpoint.bind")
            .gameRestart()
            .define("bind", "127.0.0.1"));
        define(BUILDER.comment("Port for the local HTTP endpoint; 0 picks a free one.",
                "Requires a game restart: the server has already bound.")
            .translation("minecraft_mcp.configuration.endpoint.port")
            .gameRestart()
            .defineInRange("port", 0, 0, 65535));
        define(BUILDER.comment("Bearer token for the local HTTP endpoint.",
                "Blank mints a fresh random token on every start.",
                "Requires a game restart: the token is already published in mcp/server.json.")
            .translation("minecraft_mcp.configuration.endpoint.authToken")
            .gameRestart()
            .define("authToken", ""));
        define(BUILDER.comment("Advertised in mc.debug.capabilities so an agent knows this is a virtual",
                "display rather than a real one. Informational only.",
                "Requires a game restart: it describes how this process was launched.")
            .translation("minecraft_mcp.configuration.endpoint.headless")
            .gameRestart()
            .define("headless", false));
        BUILDER.pop();

        BUILDER.push("scenario");
        define(BUILDER.comment("Directory of scenarios to run once at startup; blank runs none.",
                "Requires a game restart: the batch is dispatched during startup.")
            .translation("minecraft_mcp.configuration.scenario.directory")
            .gameRestart()
            .define("directory", ""));
        define(BUILDER.comment("Stop the process once the startup scenario batch finishes.",
                "Requires a game restart: the batch is dispatched during startup.")
            .translation("minecraft_mcp.configuration.scenario.batchExit")
            .gameRestart()
            .define("batchExit", false));
        BUILDER.pop();

        BUILDER.comment("Access policy. These two take effect as soon as the file is saved.")
            .push("access");
        define(BUILDER.comment("Tools this process refuses to serve, by exact name or a trailing-* prefix.",
                "Applies everywhere, including the local HTTP endpoint: a disabled tool is absent",
                "from tools/list and tools/call answers -32602.",
                "Example: [\"mc.server.log.tail\", \"mc.remote.*\"]")
            .translation("minecraft_mcp.configuration.access.disabledTools")
            // defineListAllowEmpty, not defineList: the latter ends up on ListValueSpec.NON_EMPTY,
            // so an empty default reads as incorrect and FML backs the file up and rewrites it on
            // every start. Empty is exactly what we want to ship.
            .defineListAllowEmpty("disabledTools", List.of(),
                () -> "mc.server.log.tail", NeoForgeMcpConfig::nonBlankString));
        define(BUILDER.comment("Servers this client exchanges MCP messages with, in either direction:",
                "each entry lets that server drive this client, and lets mc.remote.call from",
                "this client reach that server. Calling a server that is not listed fails",
                "immediately rather than timing out.",
                "Matched against the address exactly as typed into the multiplayer screen,",
                "ignoring case. No parsing: \"example.com\" and \"example.com:25565\" are",
                "different entries. A refusal message quotes the exact string to add.",
                "Singleplayer is always trusted. Client-side only; a dedicated server ignores this.",
                "Players are not listed here - a player needs the " + McpPermissions.REMOTE_CALL_NEOFORGE
                    + " permission.")
            .translation("minecraft_mcp.configuration.access.trustedServers")
            .defineListAllowEmpty("trustedServers", List.of(),
                () -> "127.0.0.1:25565", NeoForgeMcpConfig::nonBlankString));
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private NeoForgeMcpConfig() {
    }

    private static void define(ModConfigSpec.ConfigValue<?> value) {
        VALUES.put(List.copyOf(value.getPath()), value);
    }

    private static boolean nonBlankString(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static void verifyCoversEveryOption() {
        List<String> missing = new ArrayList<>();
        for (List<String> path : DefaultOptions.paths()) {
            if (!VALUES.containsKey(path)) {
                missing.add(String.join(".", path));
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("[Minecraft MCP] " + FILE_NAME + " does not define " + missing
                + "; those can only be set from a system property or the environment");
        }
    }

    static void register(IEventBus modBus, ModContainer container) {
        verifyCoversEveryOption();
        container.registerConfig(ModConfig.Type.COMMON, SPEC, FILE_NAME);
        McpConfigs.install(new McpConfig(new SpecOptions(), "config/" + FILE_NAME));
        modBus.addListener(ModConfigEvent.Loading.class, NeoForgeMcpConfig::reportFor);
        modBus.addListener(ModConfigEvent.Reloading.class, NeoForgeMcpConfig::reportFor);
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, event -> event.addNodes(REMOTE_CALL));
    }

    private static void reportFor(ModConfigEvent event) {
        if (FILE_NAME.equals(event.getConfig().getFileName())) {
            McpConfigs.report();
        }
    }

    static boolean mayRemoteCall(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, REMOTE_CALL);
        } catch (RuntimeException e) {
            System.err.println("[Minecraft MCP] could not resolve " + McpPermissions.REMOTE_CALL_NEOFORGE
                + " for " + player.getGameProfile().name() + ", refusing: " + e);
            return false;
        }
    }

    private record SpecOptions() implements McpOptions {
        @Override
        public Optional<String> get(List<String> path) {
            Object value = read(path);
            return value == null || value instanceof List ? Optional.empty() : Optional.of(String.valueOf(value));
        }

        @Override
        public Optional<List<String>> getList(List<String> path) {
            if (!(read(path) instanceof List<?> list)) {
                return Optional.empty();
            }
            List<String> items = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    items.add(String.valueOf(item));
                }
            }
            return Optional.of(items);
        }

        private static Object read(List<String> path) {
            ModConfigSpec.ConfigValue<?> value = VALUES.get(path);
            if (value == null) {
                return null;
            }
            try {
                return value.get();
            } catch (IllegalStateException e) {
                System.err.println("[Minecraft MCP] " + String.join(".", path)
                    + " was read before " + FILE_NAME + " loaded; using the built-in default");
                return null;
            }
        }
    }
}
