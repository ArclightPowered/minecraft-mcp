package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.MinecraftMcpBootstrap;
import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.FutureResult;
import io.izzel.minecraftmcp.MinecraftMcpBootstrap.McpEndpoint;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import io.izzel.minecraftmcp.input.KeyAliases;
import io.izzel.minecraftmcp.packet.CommandSuggestionSync;
import io.izzel.minecraftmcp.packet.PacketRecorderChannelInstaller;
import io.izzel.minecraftmcp.schematic.Schematic;
import io.izzel.minecraftmcp.schematic.SchematicPathResolver;
import io.izzel.minecraftmcp.schematic.SpongeSchematicV3;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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

@Mod(value = "minecraft_mcp", dist = Dist.CLIENT)
public final class NeoForgeMinecraftMcpClientMod {
    private static McpEndpoint server;

    public NeoForgeMinecraftMcpClientMod(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        try {
            server = MinecraftMcpBootstrap.start(new NeoForgeBridge());
            System.out.println("[Minecraft MCP] NeoForge MCP server started on port " + server.port());
        } catch (Exception e) { throw new RuntimeException("Failed to start Minecraft MCP", e); }
    }

    static final class NeoForgeBridge implements MinecraftClientBridge {
        private final Minecraft mc = Minecraft.getInstance();
        public String loader() { return "neoforge"; }
        public String minecraftVersion() { return SharedConstants.getCurrentVersion().name(); }
        public Path gameDirectory() { return FMLPaths.GAMEDIR.get(); }
        public boolean isOnClientThread() { return mc.isSameThread(); }
        public void execute(Runnable runnable) { mc.execute(runnable); }
        public void pressKey(String key) {
            var keyMapping = InputConstants.getKey(KeyAliases.normalize(key));
            if (mc.screen != null) {
                mc.screen.keyPressed(new KeyEvent(keyMapping.getValue(), 0, 0));
            } else {
                KeyMapping.set(keyMapping, true);
                KeyMapping.click(keyMapping);
                KeyMapping.set(keyMapping, false);
            }
        }
        public void setKeyDown(String key, boolean down) {
            KeyMapping.set(InputConstants.getKey(KeyAliases.normalize(key)), down);
        }
        public void shutdownClient() { mc.stop(); }

        public Map<String, Object> takeScreenshot(Map<String, Object> args) {
            requireOffGameThread("taking a screenshot");
            String requested = String.valueOf(args.getOrDefault("name", ""));
            String filename;
            if (requested.isBlank()) {
                filename = "mcp-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(java.time.LocalDateTime.now()) + ".png";
            } else {
                filename = Path.of(requested).getFileName().toString();
                if (!filename.endsWith(".png")) filename = filename + ".png";
            }
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) throw new IllegalArgumentException("invalid screenshot name");
            Path dir = gameDirectory().resolve("screenshots").normalize();
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) throw new IllegalArgumentException("invalid screenshot path");
            try {
                java.nio.file.Files.createDirectories(dir);
                // 26.1: takeScreenshot is callback-based (async GPU readback). Wait on the MCP
                // thread; blocking the client thread here would stall the frame that delivers it.
                CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();
                execute(() -> {
                    try {
                        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                            try (image) {
                                image.writeToFile(target);
                                pending.complete(Map.of(
                                        "status", "saved",
                                        "path", gameDirectory().relativize(target).toString().replace('\\', '/'),
                                        "absolutePath", target.toString(),
                                        "width", image.getWidth(),
                                        "height", image.getHeight(),
                                        "bytes", java.nio.file.Files.size(target)
                                ));
                            } catch (Throwable t) {
                                pending.completeExceptionally(t);
                            }
                        });
                    } catch (Throwable t) {
                        pending.completeExceptionally(t);
                    }
                });
                return pending.get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Failed to take screenshot: " + e.getMessage(), e);
            }
        }

        public void swing(String hand) {
            if (mc.player == null) return;
            InteractionHand interactionHand = "off".equalsIgnoreCase(hand) || "offhand".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            mc.player.swing(interactionHand);
        }
        public FutureResult<Map<String, Object>> look(float yaw, float pitch) {
            return action(() -> {
                if (mc.player == null) return Map.of("status", "not_in_world");
                float clampedPitch = Math.max(-90.0F, Math.min(90.0F, pitch));
                mc.player.setYRot(yaw);
                mc.player.setXRot(clampedPitch);
                mc.player.yHeadRot = yaw;
                mc.player.yBodyRot = yaw;
                return Map.of("status", "looked", "yaw", yaw, "pitch", clampedPitch);
            });
        }
        public FutureResult<Map<String, Object>> lookAt(double x, double y, double z) {
            return action(() -> {
                if (mc.player == null) return Map.of("status", "not_in_world", "x", x, "y", y, "z", z);
                Vec3 eye = mc.player.getEyePosition();
                double dx = x - eye.x;
                double dy = y - eye.y;
                double dz = z - eye.z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
                float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
                float clampedPitch = Math.max(-90.0F, Math.min(90.0F, pitch));
                mc.player.setYRot(yaw);
                mc.player.setXRot(clampedPitch);
                mc.player.yHeadRot = yaw;
                mc.player.yBodyRot = yaw;
                return Map.of("status", "looked_at", "x", x, "y", y, "z", z, "yaw", yaw, "pitch", clampedPitch);
            });
        }
        public FutureResult<Map<String, Object>> useItem(String hand) {
            return action(() -> {
                if (mc.player == null || mc.gameMode == null) return Map.of("status", "not_in_world", "hand", hand);
                InteractionHand interactionHand = "off".equalsIgnoreCase(hand) || "offhand".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                var result = mc.gameMode.useItem(mc.player, interactionHand);
                if (result.consumesAction()) mc.player.swing(interactionHand);
                return Map.of("status", "used", "hand", interactionHand.name().toLowerCase(java.util.Locale.ROOT), "result", result.toString(), "consumesAction", result.consumesAction());
            });
        }
        public FutureResult<Map<String, Object>> attackBlock(int x, int y, int z, String face) {
            return action(() -> {
                if (mc.player == null || mc.level == null || mc.gameMode == null) return Map.of("status", "not_in_world", "x", x, "y", y, "z", z);
                Direction direction;
                try { direction = Direction.valueOf((face == null ? "UP" : face.trim().toUpperCase(java.util.Locale.ROOT))); }
                catch (IllegalArgumentException e) { return Map.of("status", "rejected", "reason", "invalid face", "face", face == null ? "" : face); }
                BlockPos pos = new BlockPos(x, y, z);
                boolean started = mc.gameMode.startDestroyBlock(pos, direction);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return Map.of("status", "attacked_block", "x", x, "y", y, "z", z, "face", direction.getName(), "started", started);
            });
        }
        public FutureResult<Map<String, Object>> destroyBlock(int x, int y, int z, String face) {
            requireOffGameThread("destroying a block");
            FutureResult<Map<String, Object>> result = new FutureResult<>(this::execute);
            Direction direction;
            try { direction = Direction.valueOf((face == null ? "UP" : face.trim().toUpperCase(java.util.Locale.ROOT))); }
            catch (IllegalArgumentException e) { result.complete(Map.of("status", "rejected", "reason", "invalid face", "face", face == null ? "" : face)); return result; }
            Thread worker = new Thread(() -> {
                BlockPos pos = new BlockPos(x, y, z);
                long startedAt = System.currentTimeMillis();
                java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
                try {
                    while (!result.isCancelled()) {
                        Map<String, Object> tick = submit(() -> {
                            if (mc.player == null || mc.level == null || mc.gameMode == null) return Map.<String, Object>of("status", "not_in_world", "x", x, "y", y, "z", z);
                            BlockState state = mc.level.getBlockState(pos);
                            if (state.isAir()) return Map.<String, Object>of("status", "destroyed", "x", x, "y", y, "z", z, "face", direction.getName(), "attempts", attempts.get(), "elapsedMs", System.currentTimeMillis() - startedAt);
                            if (attempts.get() == 0) mc.gameMode.startDestroyBlock(pos, direction);
                            boolean continued = mc.gameMode.continueDestroyBlock(pos, direction);
                            attempts.incrementAndGet();
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            return Map.<String, Object>of("status", "destroying", "continued", continued, "block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                        }).join();
                        if (!"destroying".equals(tick.get("status"))) { result.complete(tick); return; }
                        try { Thread.sleep(50L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                    return;
                }
                submit(() -> { if (mc.gameMode != null) mc.gameMode.stopDestroyBlock(); return null; }).join();
                if (!result.isCancelled()) result.complete(Map.of("status", "cancelled", "x", x, "y", y, "z", z, "face", direction.getName(), "attempts", attempts.get(), "elapsedMs", System.currentTimeMillis() - startedAt));
            }, "minecraft-mcp-destroy-block");
            worker.setDaemon(true);
            result.toCompletableFuture().whenComplete((value, throwable) -> {
                if (result.toCompletableFuture().isCancelled()) worker.interrupt();
            });
            worker.start();
            return result;
        }
        public FutureResult<Map<String, Object>> dropSelected(boolean all) {
            return action(() -> {
                if (mc.player == null) return Map.of("status", "not_in_world", "all", all);
                boolean dropped = mc.player.drop(all);
                return Map.of("status", "dropped", "all", all, "dropped", dropped);
            });
        }
        public FutureResult<Map<String, Object>> jump() {
            return action(() -> {
                if (mc.player == null) return Map.of("status", "not_in_world");
                mc.player.jumpFromGround();
                return Map.of("status", "jumped");
            });
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
                mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
                mc.disconnectWithProgressScreen();
            } else if (mc.getConnection() != null) {
                mc.disconnectWithProgressScreen();
            }
            ServerData data = new ServerData(displayName, target, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target), data, false, null);
            return Map.of("status", "connecting", "address", target, "name", displayName);
        }
        public boolean serverMcpAvailable() { return NeoForgeMinecraftMcpMod.SERVER_PROXY.available() || mc.getSingleplayerServer() != null; }
        public Object serverMcpCall(String tool, Map<String, Object> arguments, long timeoutMs) throws Exception {
            requireOffGameThread("waiting for a server MCP response");
            if (NeoForgeMinecraftMcpMod.SERVER_PROXY.available()) return NeoForgeMinecraftMcpMod.SERVER_PROXY.call(tool, arguments, text -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new NeoForgeStringPayload(NeoForgeStringPayload.REQUEST, text)), timeoutMs);
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("server MCP is not available");
            ToolRegistry registry = new ToolRegistry();
            BuiltinServerTools.register(registry, new NeoForgeMinecraftMcpMod.NeoForgeServerBridge(server));
            return registry.call(tool, arguments);
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
        public Map<String, Object> commandSuggest(String command, long timeoutMs) throws Exception {
            requireOffGameThread("waiting for command suggestions");
            if (mc.getConnection() == null) {
                return Map.of("status", "unsupported", "reason", "no client connection", "command", command == null ? "" : command);
            }
            String text = (command == null || command.isBlank()) ? "/" : command;
            int id = CommandSuggestionSync.nextId();
            long start = System.nanoTime();
            var packet = CommandSuggestionSync.await(mc.getConnection().getConnection(), id, () -> mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, text)), timeoutMs);
            long latencyMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return CommandSuggestionSync.toMap(id, text, packet, latencyMs);
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
                if (screen.charTyped(new CharacterEvent(cp))) typed++;
                offset += Character.charCount(cp);
            }
            boolean submitted = false;
            if (submit) {
                submitted = screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                if (!submitted) submitted = screen.keyPressed(new KeyEvent(InputConstants.KEY_NUMPADENTER, 0, 0));
            }
            return Map.of("status", "typed", "chars", typed, "submitted", submitted, "screen", screen.getClass().getName());
        }
        public Map<String, Object> clickScreen(double x, double y, int button) {
            Screen screen = mc.screen;
            if (screen == null) return Map.of("status", "no_screen", "x", x, "y", y, "button", button);
            boolean handled = screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)), false);
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
                        boolean handled = screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)), false);
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
            LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            long seed = options.get("seed") instanceof Number n ? n.longValue() : 0L;
            WorldOptions worldOptions = new WorldOptions(seed, false, false);
            java.util.function.Function<net.minecraft.core.HolderLookup.Provider, net.minecraft.world.level.levelgen.WorldDimensions> dimensions = registries -> {
                String preset = String.valueOf(options.getOrDefault("preset", options.getOrDefault("generator", "normal"))).trim().toLowerCase(java.util.Locale.ROOT);
                if (preset.equals("flat") || preset.equals("superflat")) {
                    return registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
                }
                return WorldPresets.createNormalWorldDimensions(registries);
            };
            mc.createWorldOpenFlows().createFreshLevel(levelName, settings, worldOptions, dimensions, mc.screen == null ? new GenericMessageScreen(Component.literal("Minecraft MCP")) : mc.screen);
        }
        public void openWorld(String name) {
            mc.createWorldOpenFlows().openWorld(name, () -> mc.setScreen(null));
        }
        public void leaveWorldToTitle() {
            if (mc.level != null) {
                mc.disconnectWithSavingScreen();
            }
        }
        public Map<String, Object> worldSnapshot() {
            if (mc.level == null) return Map.of("inWorld", false);
            return Map.of(
                "inWorld", true,
                "dimension", mc.level.dimension().identifier().toString(),
                "gameTime", mc.level.getGameTime(),
                "difficulty", mc.level.getDifficulty().getSerializedName(),
                "entityCount", mc.level.entitiesForRendering().spliterator().getExactSizeIfKnown()
            );
        }
        public Map<String, Object> inventorySnapshot() {
            if (mc.player == null) return Map.of("inWorld", false, "hotbar", java.util.List.of(), "main", java.util.List.of(), "armor", java.util.List.of(), "offhand", java.util.List.of(), "slots", java.util.List.of());
            Inventory inventory = mc.player.getInventory();
            java.util.List<ItemStack> items = inventory.getNonEquipmentItems();
            java.util.List<Map<String, Object>> hotbar = inventorySection(items, "hotbar", 0, 0, 9);
            java.util.List<Map<String, Object>> main = inventorySection(items, "main", 9, 9, items.size() - 9);
            java.util.List<Map<String, Object>> armor = inventorySection(armorItems(mc.player), "armor", 36, 0, 4);
            java.util.List<Map<String, Object>> offhand = inventorySection(java.util.List.of(mc.player.getItemBySlot(EquipmentSlot.OFFHAND)), "offhand", 40, 0, 1);
            java.util.List<Map<String, Object>> slots = new java.util.ArrayList<>();
            slots.addAll(hotbar); slots.addAll(main); slots.addAll(armor); slots.addAll(offhand);
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("inWorld", true);
            result.put("selected", inventory.getSelectedSlot());
            result.put("carried", mc.player.containerMenu == null ? stackSummary(ItemStack.EMPTY) : stackSummary(mc.player.containerMenu.getCarried()));
            result.put("hotbar", hotbar);
            result.put("main", main);
            result.put("armor", armor);
            result.put("offhand", offhand);
            result.put("slots", slots);
            return result;
        }
        public Map<String, Object> findInventoryItem(Map<String, Object> args) {
            if (mc.player == null) return Map.of("found", false, "totalCount", 0, "matches", java.util.List.of(), "inWorld", false);
            String item = String.valueOf(args.get("item"));
            String section = String.valueOf(args.getOrDefault("section", "all"));
            int limit = ((Number) args.getOrDefault("limit", 50)).intValue();
            java.util.List<Map<String, Object>> matches = new java.util.ArrayList<>();
            int total = 0;
            for (Map<String, Object> slot : inventorySlots(mc.player)) {
                if (!"all".equalsIgnoreCase(section) && !String.valueOf(slot.get("section")).equalsIgnoreCase(section)) continue;
                if (item.equals(slot.get("item"))) {
                    total += ((Number) slot.get("count")).intValue();
                    if (matches.size() < Math.max(0, limit)) matches.add(slot);
                }
            }
            return Map.of("found", total > 0, "item", item, "totalCount", total, "matches", matches);
        }
        public Map<String, Object> countInventoryItem(Map<String, Object> args) {
            Map<String, Object> found = findInventoryItem(args);
            return Map.of("item", found.get("item"), "count", found.get("totalCount"));
        }
        public Map<String, Object> selectedInventoryItem() {
            if (mc.player == null) return Map.of("inWorld", false, "selected", -1, "item", "minecraft:air", "count", 0, "empty", true);
            Inventory inventory = mc.player.getInventory();
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>(stackSummary(inventory.getSelectedItem()));
            result.put("inWorld", true);
            result.put("selected", inventory.getSelectedSlot());
            result.put("slot", inventory.getSelectedSlot());
            return result;
        }
        public Map<String, Object> containerState() {
            if (mc.player == null) return Map.of("inWorld", false, "hasContainer", false, "hasScreen", mc.screen != null);
            boolean hasContainerScreen = mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
            boolean hasContainer = hasContainerScreen || mc.player.containerMenu != mc.player.inventoryMenu;
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("inWorld", true);
            result.put("hasContainer", hasContainer);
            result.put("hasScreen", mc.screen != null);
            result.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
            result.put("title", mc.screen == null ? null : mc.screen.getTitle().getString());
            if (!hasContainer || mc.player.containerMenu == null) {
                result.put("reason", "no container screen");
                result.put("slots", java.util.List.of());
                return result;
            }
            var menu = mc.player.containerMenu;
            result.put("containerId", menu.containerId);
            result.put("menuClass", menu.getClass().getName());
            try { result.put("menuType", net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(menu.getType()).toString()); }
            catch (Exception e) { result.put("menuType", menu.getClass().getName()); }
            result.put("carried", stackSummary(menu.getCarried()));
            java.util.List<Map<String, Object>> slots = new java.util.ArrayList<>();
            for (int i = 0; i < menu.slots.size(); i++) slots.add(containerSlotMap(i, menu.slots.get(i)));
            result.put("slots", slots);
            return result;
        }
        public Map<String, Object> clickContainer(int slot, int button, String clickType) {
            if (mc.player == null) return Map.of("status", "not_in_world", "slot", slot, "button", button, "clickType", clickType);
            if (mc.gameMode == null) return Map.of("status", "rejected", "reason", "no game mode", "slot", slot, "button", button, "clickType", clickType);
            var menu = mc.player.containerMenu;
            if (menu == null) return Map.of("status", "rejected", "reason", "no container", "slot", slot, "button", button, "clickType", clickType);
            if (slot >= menu.slots.size()) return Map.of("status", "rejected", "reason", "slot out of range", "slot", slot, "button", button, "clickType", clickType, "containerId", menu.containerId);
            net.minecraft.world.inventory.ContainerInput type = net.minecraft.world.inventory.ContainerInput.valueOf(clickType);
            mc.gameMode.handleContainerInput(menu.containerId, slot, button, type, mc.player);
            return Map.of("status", "clicked", "slot", slot, "button", button, "clickType", clickType, "containerId", menu.containerId);
        }
        public Map<String, Object> closeContainer() {
            if (mc.player == null) return Map.of("status", "not_in_world");
            mc.player.closeContainer();
            return Map.of("status", "closed");
        }
        public Map<String, Object> selectHotbarSlot(int slot) {
            if (mc.player == null) return Map.of("status", "not_in_world", "slot", slot);
            if (slot < 0 || slot >= Inventory.getSelectionSize()) {
                return Map.of("status", "rejected", "reason", "slot out of range", "slot", slot);
            }
            Inventory inventory = mc.player.getInventory();
            inventory.setSelectedSlot(slot);
            return Map.of("status", "selected", "slot", slot, "item", stackMap(slot, inventory.getItem(slot)));
        }
        private static java.util.List<ItemStack> armorItems(net.minecraft.world.entity.player.Player player) {
            return java.util.List.of(
                    player.getItemBySlot(EquipmentSlot.FEET),
                    player.getItemBySlot(EquipmentSlot.LEGS),
                    player.getItemBySlot(EquipmentSlot.CHEST),
                    player.getItemBySlot(EquipmentSlot.HEAD));
        }
        private static java.util.List<Map<String, Object>> inventorySlots(net.minecraft.world.entity.player.Player player) {
            java.util.List<ItemStack> items = player.getInventory().getNonEquipmentItems();
            java.util.List<Map<String, Object>> slots = new java.util.ArrayList<>();
            slots.addAll(inventorySection(items, "hotbar", 0, 0, 9));
            slots.addAll(inventorySection(items, "main", 9, 9, items.size() - 9));
            slots.addAll(inventorySection(armorItems(player), "armor", 36, 0, 4));
            slots.addAll(inventorySection(java.util.List.of(player.getItemBySlot(EquipmentSlot.OFFHAND)), "offhand", 40, 0, 1));
            return slots;
        }
        private static java.util.List<Map<String, Object>> inventorySection(java.util.List<ItemStack> source, String section, int playerInventoryBase, int sourceStart, int length) {
            java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
            for (int i = 0; i < length; i++) list.add(inventorySlotMap(section, i, playerInventoryBase + i, source.get(sourceStart + i)));
            return list;
        }
        private static Map<String, Object> inventorySlotMap(String section, int index, int playerInventoryIndex, ItemStack stack) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>(stackSummary(stack));
            map.put("section", section);
            map.put("index", index);
            map.put("slot", playerInventoryIndex);
            map.put("playerInventoryIndex", playerInventoryIndex);
            return map;
        }
        private static Map<String, Object> containerSlotMap(int menuSlot, net.minecraft.world.inventory.Slot slot) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>(stackSummary(slot.getItem()));
            map.put("slot", menuSlot);
            map.put("containerSlot", slot.index);
            map.put("index", slot.index);
            map.put("x", slot.x);
            map.put("y", slot.y);
            map.put("hasItem", slot.hasItem());
            map.put("mayPickup", true);
            map.put("mayPlace", slot.mayPlace(slot.getItem()));
            map.put("active", slot.isActive());
            return map;
        }
        private static Map<String, Object> stackMap(int slot, ItemStack stack) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>(stackSummary(stack));
            map.put("slot", slot);
            return map;
        }
        private static Map<String, Object> stackSummary(ItemStack stack) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            boolean empty = stack == null || stack.isEmpty();
            map.put("item", empty ? "minecraft:air" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            map.put("count", empty ? 0 : stack.getCount());
            map.put("empty", empty);
            map.put("maxStackSize", empty ? 64 : stack.getMaxStackSize());
            map.put("displayName", empty ? "Air" : stack.getHoverName().getString());
            return map;
        }
        public Map<String, Object> blockAt(int x, int y, int z) {
            if (mc.level == null) return Map.of("inWorld", false, "x", x, "y", y, "z", z);
            BlockState state = mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            return Map.of("inWorld", true, "x", x, "y", y, "z", z, "block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }

        @Override
        public Map<String, Object> exportSchematic(Map<String, Object> args) {
            return submit(() -> {
                if (mc.level == null) return java.util.Map.<String, Object>of("status", "no_world");
                try {
                    Path path = SchematicPathResolver.resolve(gameDirectory(), String.valueOf(args.getOrDefault("path", "export.schem")));
                    Map<?, ?> from = (Map<?, ?>) args.get("from");
                    Map<?, ?> to = (Map<?, ?>) args.get("to");
                    if (from == null || to == null) throw new IllegalArgumentException("from and to coordinates are required");
                    int minX = Math.min(coord(from, "x"), coord(to, "x"));
                    int minY = Math.min(coord(from, "y"), coord(to, "y"));
                    int minZ = Math.min(coord(from, "z"), coord(to, "z"));
                    int maxX = Math.max(coord(from, "x"), coord(to, "x"));
                    int maxY = Math.max(coord(from, "y"), coord(to, "y"));
                    int maxZ = Math.max(coord(from, "z"), coord(to, "z"));
                    int width = maxX - minX + 1;
                    int height = maxY - minY + 1;
                    int length = maxZ - minZ + 1;
                    int volume = width * height * length;
                    int maxBlocks = ((Number) args.getOrDefault("maxBlocks", 32768)).intValue();
                    if (volume > maxBlocks) throw new IllegalArgumentException("schematic volume " + volume + " exceeds maxBlocks " + maxBlocks);
                    java.util.Map<String, Integer> paletteIndex = new java.util.LinkedHashMap<>();
                    java.util.List<String> palette = new java.util.ArrayList<>();
                    int[] blockData = new int[volume];
                    for (int y = 0; y < height; y++) {
                        for (int z = 0; z < length; z++) {
                            for (int x = 0; x < width; x++) {
                                BlockState state = mc.level.getBlockState(new BlockPos(minX + x, minY + y, minZ + z));
                                String serialized = BlockStateParser.serialize(state);
                                int idx = paletteIndex.computeIfAbsent(serialized, key -> { palette.add(key); return palette.size() - 1; });
                                blockData[x + z * width + y * width * length] = idx;
                            }
                        }
                    }
                    java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    Object metaArg = args.get("metadata");
                    if (metaArg instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) metadata.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    Schematic schematic = new Schematic(width, height, length, new int[] {minX, minY, minZ}, palette, blockData, List.of(), new int[0], metadata, new net.minecraft.nbt.ListTag(), new net.minecraft.nbt.ListTag());
                    SpongeSchematicV3.write(path, schematic, SharedConstants.getCurrentVersion().dataVersion().version());
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("status", "exported");
                    result.put("path", path.toAbsolutePath().normalize().toString());
                    result.put("format", "sponge-v3");
                    result.put("width", width);
                    result.put("height", height);
                    result.put("length", length);
                    result.put("volume", volume);
                    result.put("paletteSize", palette.size());
                    return result;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to export Sponge v3 schematic: " + e.getMessage(), e);
                }
            }).join();
        }

        @Override
        public Map<String, Object> pasteSchematic(Map<String, Object> args) {
            return submit(() -> {
                if (mc.level == null) return java.util.Map.<String, Object>of("status", "no_world");
                try {
                    Path path = SchematicPathResolver.resolve(gameDirectory(), String.valueOf(args.getOrDefault("path", "")));
                    Schematic schematic = SpongeSchematicV3.read(path);
                    int maxBlocks = ((Number) args.getOrDefault("maxBlocks", 32768)).intValue();
                    if (schematic.volume() > maxBlocks) throw new IllegalArgumentException("schematic volume " + schematic.volume() + " exceeds maxBlocks " + maxBlocks);
                    Map<?, ?> origin = (Map<?, ?>) args.get("origin");
                    int originX = origin == null ? (mc.player == null ? 0 : (int) Math.floor(mc.player.getX())) : coord(origin, "x");
                    int originY = origin == null ? (mc.player == null ? 0 : (int) Math.floor(mc.player.getY())) : coord(origin, "y");
                    int originZ = origin == null ? (mc.player == null ? 0 : (int) Math.floor(mc.player.getZ())) : coord(origin, "z");
                    boolean ignoreAir = Boolean.parseBoolean(String.valueOf(args.getOrDefault("ignoreAir", false)));
                    int placed = 0;
                    int skippedAir = 0;
                    var blockLookup = mc.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
                    for (int y = 0; y < schematic.height(); y++) {
                        for (int z = 0; z < schematic.length(); z++) {
                            for (int x = 0; x < schematic.width(); x++) {
                                String serialized = schematic.blockStateAt(x, y, z);
                                if (ignoreAir && "minecraft:air".equals(serialized)) { skippedAir++; continue; }
                                BlockState state = BlockStateParser.parseForBlock(blockLookup, serialized, true).blockState();
                                mc.level.setBlock(new BlockPos(originX + x, originY + y, originZ + z), state, 3);
                                placed++;
                            }
                        }
                    }
                    java.util.Map<String, Object> originResult = new java.util.LinkedHashMap<>();
                    originResult.put("x", originX); originResult.put("y", originY); originResult.put("z", originZ);
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("status", "pasted");
                    result.put("path", path.toAbsolutePath().normalize().toString());
                    result.put("format", "sponge-v3");
                    result.put("origin", originResult);
                    result.put("placedBlocks", placed);
                    result.put("skippedAir", skippedAir);
                    result.put("width", schematic.width());
                    result.put("height", schematic.height());
                    result.put("length", schematic.length());
                    return result;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to paste Sponge v3 schematic: " + e.getMessage(), e);
                }
            }).join();
        }

        private static int coord(Map<?, ?> map, String key) {
            Object value = map.get(key);
            if (!(value instanceof Number number)) throw new IllegalArgumentException("coordinate " + key + " is required");
            return number.intValue();
        }

        @Override
        public Map<String, Object> startPacketRecording(Map<String, Object> args) {
            Map<String, Object> result = MinecraftClientBridge.super.startPacketRecording(args);
            Map<String, Object> hook = mc.getConnection() == null ? Map.of("packetHandler", "not_connected") : PacketRecorderChannelInstaller.install(mc.getConnection().getConnection(), packetRecorder());
            java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>(result);
            merged.putAll(hook);
            return merged;
        }
        @Override
        public Map<String, Object> stopPacketRecording() {
            Map<String, Object> result = MinecraftClientBridge.super.stopPacketRecording();
            Map<String, Object> hook = mc.getConnection() == null ? Map.of("packetHandler", "not_connected") : PacketRecorderChannelInstaller.remove(mc.getConnection().getConnection());
            java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>(result);
            merged.putAll(hook);
            return merged;
        }
        public Map<String, Object> moveWaypoints(List<Vec3> waypoints, boolean loop, int maxLoops, double tolerance, long timeoutMs, boolean sprint, boolean sneak, boolean controlView) {
            if (waypoints == null || waypoints.isEmpty()) throw new IllegalArgumentException("waypoints must not be empty");
            requireOffGameThread("walking waypoints");
            long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
            double speed = sprint ? 0.28D : 0.16D;
            int loops = 0;
            int visited = 0;
            while (System.currentTimeMillis() <= deadline) {
                for (Vec3 target : waypoints) {
                    while (System.currentTimeMillis() <= deadline) {
                        Vec3 pos = submit(() -> mc.player == null ? null : mc.player.position()).join();
                        if (pos == null) return Map.of("status", "no_player", "visited", visited, "loops", loops, "sprint", sprint, "sneak", sneak);
                        Vec3 delta = target.subtract(pos);
                        double distance = delta.length();
                        if (distance <= tolerance) { visited++; break; }
                        Vec3 step = delta.normalize().scale(Math.min(speed, distance));
                        execute(() -> {
                            if (mc.player != null) {
                                mc.player.setSprinting(sprint);
                                mc.player.setShiftKeyDown(sneak);
                                mc.options.keySprint.setDown(sprint);
                                mc.options.keyShift.setDown(sneak);
                                if (controlView) {
                                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
                                    mc.player.setXRot((float) Math.toDegrees(-Math.atan2(step.y, Math.sqrt(step.x * step.x + step.z * step.z))));
                                }
                                mc.player.move(MoverType.PLAYER, step);
                            }
                        });
                        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Map.of("status", "interrupted", "visited", visited, "loops", loops, "sprint", sprint, "sneak", sneak); }
                    }
                }
                loops++;
                if (!loop || (maxLoops > 0 && loops >= maxLoops)) return Map.of("status", "completed", "waypoints", waypoints.size(), "visited", visited, "loops", loops, "sprint", sprint, "sneak", sneak, "controlView", controlView);
            }
            return Map.of("status", "timeout", "waypoints", waypoints.size(), "visited", visited, "loops", loops, "timeoutMs", timeoutMs, "sprint", sprint, "sneak", sneak, "controlView", controlView);
        }

        public ClientSnapshot snapshot() {
            String screen = mc.screen == null ? null : mc.screen.getClass().getName();
            if (mc.player == null) return new ClientSnapshot(true, false, mc.level != null, screen, null, 0, 0, 0, 0, 0);
            boolean rawInWorld = mc.level != null;
            boolean playableInWorld = rawInWorld && mc.screen == null;
            return new ClientSnapshot(true, playableInWorld, rawInWorld, screen, mc.player.getGameProfile().name(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
        }
    }

}
