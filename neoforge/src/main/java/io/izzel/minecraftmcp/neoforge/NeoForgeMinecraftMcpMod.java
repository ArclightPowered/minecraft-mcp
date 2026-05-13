package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.MinecraftMcpBootstrap;
import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.LocalHttpMcpServer;
import io.izzel.minecraftmcp.input.KeyAliases;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Map;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.WorldDataConfiguration;

@Mod("minecraft_mcp")
public final class NeoForgeMinecraftMcpMod {
    private static LocalHttpMcpServer server;
    public NeoForgeMinecraftMcpMod() {
        try {
            server = MinecraftMcpBootstrap.start(new NeoForgeBridge());
            System.out.println("[Minecraft MCP] NeoForge MCP server started on port " + server.port());
        } catch (Exception e) { throw new RuntimeException("Failed to start Minecraft MCP", e); }
    }
    static final class NeoForgeBridge implements MinecraftClientBridge {
        private final Minecraft mc = Minecraft.getInstance();
        public String loader() { return "neoforge"; }
        public String minecraftVersion() { return SharedConstants.getCurrentVersion().getName(); }
        public Path gameDirectory() { return FMLPaths.GAMEDIR.get(); }
        public boolean isOnClientThread() { return mc.isSameThread(); }
        public void execute(Runnable runnable) { mc.execute(runnable); }
        public void pressKey(String key) {
            setKeyDown(key, true);
            setKeyDown(key, false);
        }
        public void setKeyDown(String key, boolean down) {
            KeyMapping.set(InputConstants.getKey(KeyAliases.normalize(key)), down);
        }
        public void shutdownClient() { mc.stop(); }

        public void createTestWorld(String name, Map<String, Object> options) {
            if (mc.level != null) {
                return;
            }
            String levelName = name == null || name.isBlank() ? "minecraft_mcp_test_world" : name;
            LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
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
        private static Map<String, Object> stackMap(int slot, ItemStack stack) {
            return Map.of("slot", slot, "item", stack.isEmpty() ? "minecraft:air" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount());
        }
        public Map<String, Object> blockAt(int x, int y, int z) {
            if (mc.level == null) return Map.of("inWorld", false, "x", x, "y", y, "z", z);
            BlockState state = mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            return Map.of("inWorld", true, "x", x, "y", y, "z", z, "block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }

        public ClientSnapshot snapshot() {
            String screen = mc.screen == null ? null : mc.screen.getClass().getName();
            if (mc.player == null) return new ClientSnapshot(true, false, screen, null, 0, 0, 0, 0, 0);
            return new ClientSnapshot(true, mc.level != null, screen, mc.player.getGameProfile().getName(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
        }
    }
}
