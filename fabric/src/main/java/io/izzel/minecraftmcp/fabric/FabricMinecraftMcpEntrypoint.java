package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.MinecraftMcpBootstrap;
import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.LocalHttpMcpServer;
import io.izzel.minecraftmcp.serverlink.ServerMcpPluginMessageHandler;
import io.izzel.minecraftmcp.serverlink.ServerMcpProxy;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.input.KeyAliases;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.WorldDataConfiguration;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class FabricMinecraftMcpEntrypoint implements ClientModInitializer {
    private static LocalHttpMcpServer server;
    @Override public void onInitializeClient() {
        try {
            registerServerMcpPluginMessages();
            server = MinecraftMcpBootstrap.start(new FabricBridge());
            System.out.println("[Minecraft MCP] Fabric MCP server started on port " + server.port());
        } catch (Exception e) { throw new RuntimeException("Failed to start Minecraft MCP", e); }
    }
    private static void registerServerMcpPluginMessages() {
        PayloadTypeRegistry.playS2C().register(FabricStringPayload.HELLO, FabricStringPayload.codec(FabricStringPayload.HELLO));
        PayloadTypeRegistry.playS2C().register(FabricStringPayload.RESPONSE, FabricStringPayload.codec(FabricStringPayload.RESPONSE));
        PayloadTypeRegistry.playC2S().register(FabricStringPayload.REQUEST, FabricStringPayload.codec(FabricStringPayload.REQUEST));
        ClientPlayNetworking.registerGlobalReceiver(FabricStringPayload.HELLO, (payload, context) -> FabricBridge.SERVER_PROXY.receive(payload.text()));
        ClientPlayNetworking.registerGlobalReceiver(FabricStringPayload.RESPONSE, (payload, context) -> FabricBridge.SERVER_PROXY.receive(payload.text()));
    }
    static final class FabricBridge implements MinecraftClientBridge {
        static final ServerMcpProxy SERVER_PROXY = new ServerMcpProxy();
        private final Minecraft mc = Minecraft.getInstance();
        public String loader() { return "fabric"; }
        public String minecraftVersion() { return SharedConstants.getCurrentVersion().getName(); }
        public Path gameDirectory() { return FabricLoader.getInstance().getGameDir(); }
        public boolean isOnClientThread() { return false; }
        public void execute(Runnable runnable) { mc.execute(runnable); }
        public void pressKey(String key) {
            setKeyDown(key, true);
            setKeyDown(key, false);
        }
        public void setKeyDown(String key, boolean down) {
            KeyMapping.set(InputConstants.getKey(KeyAliases.normalize(key)), down);
        }
        public void shutdownClient() { mc.stop(); }

        public void swing(String hand) {
            if (mc.player == null) return;
            InteractionHand interactionHand = "off".equalsIgnoreCase(hand) || "offhand".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            mc.player.swing(interactionHand);
        }
        public Map<String, Object> vehicleState() {
            if (mc.player == null) return Map.of("inWorld", false, "isPassenger", false);
            Entity vehicle = mc.player.getVehicle();
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("inWorld", mc.level != null);
            result.put("isPassenger", mc.player.isPassenger());
            result.put("vehicle", vehicle == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString());
            result.put("x", mc.player.getX());
            result.put("y", mc.player.getY());
            result.put("z", mc.player.getZ());
            return result;
        }

        public Map<String, Object> runCommand(String command) {
            if (mc.player == null || mc.player.connection == null) {
                return Map.of("status", "unsupported", "reason", "no client connection", "command", command == null ? "" : command);
            }
            String normalized = command == null ? "" : command.trim();
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            mc.player.connection.sendCommand(normalized);
            return Map.of("status", "sent", "command", normalized);
        }
        public Map<String, Object> connectServer(String address, String name) {
            String target = address == null ? "" : address.trim();
            if (target.isEmpty()) return Map.of("status", "rejected", "reason", "empty address");
            String displayName = (name == null || name.isBlank()) ? target : name;
            if (mc.level != null) {
                mc.level.disconnect();
                mc.disconnect();
            } else if (mc.getConnection() != null) {
                mc.disconnect();
            }
            ServerData data = new ServerData(displayName, target, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target), data, false, null);
            return Map.of("status", "connecting", "address", target, "name", displayName);
        }
        public boolean serverMcpAvailable() { return SERVER_PROXY.available(); }
        public Object serverMcpCall(String tool, Map<String, Object> arguments, long timeoutMs) throws Exception {
            return SERVER_PROXY.call(tool, arguments, text -> ClientPlayNetworking.send(new FabricStringPayload(FabricStringPayload.REQUEST, text)), timeoutMs);
        }


        public Map<String, Object> sendChat(String message) {
            if (mc.player == null || mc.player.connection == null) {
                return Map.of("status", "unsupported", "reason", "no client connection", "message", message == null ? "" : message);
            }
            String text = message == null ? "" : message;
            if (text.startsWith("/")) {
                mc.player.connection.sendCommand(text.substring(1));
                return Map.of("status", "sent", "kind", "command", "message", text);
            }
            mc.player.connection.sendChat(text);
            return Map.of("status", "sent", "kind", "chat", "message", text);
        }
        public Map<String, Object> screenState() {
            Screen screen = mc.screen;
            if (screen == null) return Map.of("hasScreen", false);
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("hasScreen", true);
            result.put("screen", screen.getClass().getName());
            result.put("title", screen.getTitle().getString());
            result.put("narration", screen.getNarrationMessage().getString());
            result.put("width", screen.width);
            result.put("height", screen.height);
            java.util.List<java.util.Map<String, Object>> children = new java.util.ArrayList<>();
            int index = 0;
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                int widgetIndex = index++;
                String widgetId = "widget-" + widgetIndex;
                entry.put("index", widgetIndex);
                entry.put("id", widgetId);
                entry.put("class", child.getClass().getName());
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    entry.put("x", widget.getX());
                    entry.put("y", widget.getY());
                    entry.put("width", widget.getWidth());
                    entry.put("height", widget.getHeight());
                    entry.put("message", widget.getMessage().getString());
                    entry.put("active", widget.active);
                    entry.put("visible", widget.visible);
                }
                children.add(entry);
            }
            result.put("children", children);
            return result;
        }
        public Map<String, Object> typeText(String text, boolean submit) {
            Screen screen = mc.screen;
            if (screen == null) return Map.of("status", "no_screen");
            String value = text == null ? "" : text;
            int typed = 0;
            for (int offset = 0; offset < value.length(); ) {
                int cp = value.codePointAt(offset);
                if (screen.charTyped((char) cp, 0)) typed++;
                offset += Character.charCount(cp);
            }
            boolean submitted = false;
            if (submit) {
                submitted = screen.keyPressed(InputConstants.KEY_RETURN, 0, 0);
                if (!submitted) submitted = screen.keyPressed(InputConstants.KEY_NUMPADENTER, 0, 0);
            }
            return Map.of("status", "typed", "chars", typed, "submitted", submitted, "screen", screen.getClass().getName());
        }
        public Map<String, Object> clickScreen(double x, double y, int button) {
            Screen screen = mc.screen;
            if (screen == null) return Map.of("status", "no_screen", "x", x, "y", y, "button", button);
            boolean handled = screen.mouseClicked(x, y, button);
            return Map.of("status", "clicked", "handled", handled, "x", x, "y", y, "button", button, "screen", screen.getClass().getName());
        }
        public Map<String, Object> clickWidget(String id, String message, int button) {
            Screen screen = mc.screen;
            if (screen == null) return Map.of("status", "no_screen", "id", id == null ? "" : id, "message", message == null ? "" : message);
            String wantedId = id == null ? "" : id.trim();
            String wantedMessage = message == null ? "" : message;
            int index = 0;
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                String widgetId = "widget-" + index;
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    String widgetMessage = widget.getMessage().getString();
                    boolean idMatches = !wantedId.isBlank() && wantedId.equals(widgetId);
                    boolean messageMatches = wantedId.isBlank() && !wantedMessage.isBlank() && wantedMessage.equals(widgetMessage);
                    if (idMatches || messageMatches) {
                        double x = widget.getX() + widget.getWidth() / 2.0;
                        double y = widget.getY() + widget.getHeight() / 2.0;
                        boolean handled = screen.mouseClicked(x, y, button);
                        return Map.of("status", "clicked", "handled", handled, "id", widgetId, "index", index, "message", widgetMessage, "x", x, "y", y, "button", button, "screen", screen.getClass().getName());
                    }
                }
                index++;
            }
            return Map.of("status", "not_found", "id", wantedId, "message", wantedMessage, "screen", screen.getClass().getName());
        }
        public Map<String, Object> disconnectState() {
            Screen screen = mc.screen;
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("disconnected", screen instanceof DisconnectedScreen);
            result.put("screen", screen == null ? null : screen.getClass().getName());
            result.put("title", screen == null ? null : screen.getTitle().getString());
            result.put("message", screen == null ? null : screen.getNarrationMessage().getString());
            return result;
        }
        public Map<String, Object> interactBlock(int x, int y, int z, String face, String hand) {
            if (mc.player == null || mc.level == null || mc.gameMode == null) {
                return Map.of("status", "not_in_world", "x", x, "y", y, "z", z);
            }
            Direction direction;
            try { direction = Direction.valueOf((face == null ? "UP" : face.trim().toUpperCase(java.util.Locale.ROOT))); }
            catch (IllegalArgumentException e) { return Map.of("status", "rejected", "reason", "invalid face", "face", face == null ? "" : face); }
            InteractionHand interactionHand = "off".equalsIgnoreCase(hand) || "offhand".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            BlockPos pos = new BlockPos(x, y, z);
            Vec3 hit = Vec3.atCenterOf(pos);
            BlockHitResult hitResult = new BlockHitResult(hit, direction, pos, false);
            var result = mc.gameMode.useItemOn(mc.player, interactionHand, hitResult);
            if (result.consumesAction()) mc.player.swing(interactionHand);
            return Map.of("status", "interacted", "result", result.toString(), "consumesAction", result.consumesAction(), "x", x, "y", y, "z", z, "face", direction.getName(), "hand", interactionHand.name().toLowerCase(java.util.Locale.ROOT));
        }

        public void createTestWorld(String name, Map<String, Object> options) {
            if (mc.level != null) {
                return;
            }
            String levelName = name == null || name.isBlank() ? "minecraft_mcp_test_world" : name;
            LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            long seed = options.get("seed") instanceof Number n ? n.longValue() : 0L;
            WorldOptions worldOptions = new WorldOptions(seed, false, false);
            mc.createWorldOpenFlows().createFreshLevel(levelName, settings, worldOptions, WorldPresets::createNormalWorldDimensions, mc.screen == null ? new GenericMessageScreen(Component.literal("Minecraft MCP")) : mc.screen);
        }
        public void openWorld(String name) {
            mc.createWorldOpenFlows().openWorld(name, () -> mc.setScreen(null));
        }
        public void leaveWorldToTitle() {
            if (mc.level != null) {
                mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            }
        }
        public Map<String, Object> worldSnapshot() {
            if (mc.level == null) return Map.of("inWorld", false);
            return Map.of(
                "inWorld", true,
                "dimension", mc.level.dimension().location().toString(),
                "gameTime", mc.level.getGameTime(),
                "difficulty", mc.level.getDifficulty().getKey(),
                "entityCount", mc.level.entitiesForRendering().spliterator().getExactSizeIfKnown()
            );
        }
        public Map<String, Object> inventorySnapshot() {
            if (mc.player == null) return Map.of("inWorld", false, "hotbar", java.util.List.of());
            Inventory inventory = mc.player.getInventory();
            java.util.List<Map<String, Object>> hotbar = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) hotbar.add(stackMap(i, inventory.getItem(i)));
            return Map.of("inWorld", true, "selected", inventory.selected, "hotbar", hotbar);
        }
        public Map<String, Object> selectHotbarSlot(int slot) {
            if (mc.player == null) return Map.of("status", "not_in_world", "slot", slot);
            if (slot < 0 || slot >= Inventory.getSelectionSize()) {
                return Map.of("status", "rejected", "reason", "slot out of range", "slot", slot);
            }
            Inventory inventory = mc.player.getInventory();
            inventory.selected = slot;
            return Map.of("status", "selected", "slot", slot, "item", stackMap(slot, inventory.getItem(slot)));
        }
        private static Map<String, Object> stackMap(int slot, ItemStack stack) {
            return Map.of("slot", slot, "item", stack.isEmpty() ? "minecraft:air" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount());
        }
        public Map<String, Object> blockAt(int x, int y, int z) {
            if (mc.level == null) return Map.of("inWorld", false, "x", x, "y", y, "z", z);
            BlockState state = mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            return Map.of("inWorld", true, "x", x, "y", y, "z", z, "block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }
        public Map<String, Object> moveWaypoints(List<Vec3> waypoints, boolean loop, int maxLoops, double tolerance, long timeoutMs, boolean sprint, boolean controlView) {
            if (waypoints == null || waypoints.isEmpty()) throw new IllegalArgumentException("waypoints must not be empty");
            long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
            double speed = sprint ? 0.28D : 0.16D;
            int loops = 0;
            int visited = 0;
            while (System.currentTimeMillis() <= deadline) {
                for (Vec3 target : waypoints) {
                    while (System.currentTimeMillis() <= deadline) {
                        Vec3 pos = submit(() -> mc.player == null ? null : mc.player.position()).join();
                        if (pos == null) return Map.of("status", "no_player", "visited", visited, "loops", loops);
                        Vec3 delta = target.subtract(pos);
                        double distance = delta.length();
                        if (distance <= tolerance) { visited++; break; }
                        Vec3 step = delta.normalize().scale(Math.min(speed, distance));
                        execute(() -> {
                            if (mc.player != null) {
                                if (controlView) {
                                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
                                    mc.player.setXRot((float) Math.toDegrees(-Math.atan2(step.y, Math.sqrt(step.x * step.x + step.z * step.z))));
                                }
                                mc.player.move(MoverType.PLAYER, step);
                            }
                        });
                        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Map.of("status", "interrupted", "visited", visited, "loops", loops); }
                    }
                }
                loops++;
                if (!loop || (maxLoops > 0 && loops >= maxLoops)) return Map.of("status", "completed", "waypoints", waypoints.size(), "visited", visited, "loops", loops, "sprint", sprint, "controlView", controlView);
            }
            return Map.of("status", "timeout", "waypoints", waypoints.size(), "visited", visited, "loops", loops, "timeoutMs", timeoutMs, "controlView", controlView);
        }

        public ClientSnapshot snapshot() {
            String screen = mc.screen == null ? null : mc.screen.getClass().getName();
            if (mc.player == null) return new ClientSnapshot(true, false, screen, null, 0, 0, 0, 0, 0);
            return new ClientSnapshot(true, mc.level != null, screen, mc.player.getGameProfile().getName(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
        }
    }
}
