package io.izzel.minecraftmcp.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.function.Consumer;

//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}

/**
 * Client-side counterpart of {@link MinecraftCompat}: the screen input, screenshot, world and container calls
 * whose shape changed between the supported versions. Only client code paths may call it.
 */
public final class ClientCompat {
    private ClientCompat() {}

    // --- the open screen (26.2 moved it from Minecraft to Gui)

    public static Screen screen(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.screen();
        *///?} else {
        return mc.screen;
        //?}
    }

    /** True while an overlay (the resource loading screen) covers the game and input does not reach the screen. */
    public static boolean loading(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.overlay() != null;
        *///?} else {
        return mc.getOverlay() != null;
        //?}
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        //? if >=26.2 {
        /*mc.gui.setScreen(screen);
        *///?} else {
        mc.setScreen(screen);
        //?}
    }

    // --- screen input (1.21.9 wrapped key, character and mouse input in event records)

    public static boolean keyPressed(Screen screen, int key) {
        //? if >=1.21.9 {
        return screen.keyPressed(new KeyEvent(key, 0, 0));
        //?} else {
        /*return screen.keyPressed(key, 0, 0);
        *///?}
    }

    public static boolean charTyped(Screen screen, int codePoint) {
        //? if >=26.1 {
        /*return screen.charTyped(new CharacterEvent(codePoint));
        *///?} else if >=1.21.9 {
        return screen.charTyped(new CharacterEvent(codePoint, 0));
        //?} else {
        /*return screen.charTyped((char) codePoint, 0);
        *///?}
    }

    public static boolean mouseClicked(Screen screen, double x, double y, int button) {
        //? if >=1.21.9 {
        return screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)), false);
        //?} else {
        /*return screen.mouseClicked(x, y, button);
        *///?}
    }

    /**
     * Reads the main framebuffer; call on the client thread. From 1.21.5 the read-back is asynchronous and
     * {@code done} runs on a later frame, so never block the client thread waiting for it.
     */
    public static void takeScreenshot(Minecraft mc, Consumer<NativeImage> done) {
        //? if >=26.2 {
        /*net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), done);
        *///?} else if >=1.21.5 {
        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), done);
        //?} else {
        /*done.accept(net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget()));
        *///?}
    }

    // --- player actions (26.3 made the swing animation explicit and moved dropping to the game mode)

    public static void swing(Minecraft mc, net.minecraft.world.InteractionHand hand) {
        //? if >=26.3 {
        /*mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        *///?} else {
        mc.player.swing(hand);
        //?}
    }

    /** Drops the selected stack, or one item of it; false when the hand was empty. */
    public static boolean dropSelected(Minecraft mc, boolean all) {
        //? if >=26.3 {
        /*boolean holding = !mc.player.getMainHandItem().isEmpty();
        mc.gameMode.dropItem(mc.player, all);
        return holding;
        *///?} else {
        return mc.player.drop(all);
        //?}
    }

    // --- leaving a world or a server

    /** Drops the current connection before connecting somewhere else. */
    public static void disconnect(Minecraft mc) {
        //? if >=1.21.5 {
        if (mc.level != null) mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        mc.disconnectWithProgressScreen();
        //?} else {
        /*if (mc.level != null) mc.level.disconnect();
        mc.disconnect();
        *///?}
    }

    /**
     * Leaves the current world to the title screen in the pause menu's order: closing the level connection
     * first is what stops an integrated server; without it disconnect() waits for a server that never stops.
     */
    public static void leaveWorld(Minecraft mc) {
        //? if >=1.21.5 {
        mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        mc.disconnectWithSavingScreen();
        //?} else {
        /*mc.level.disconnect();
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        *///?}
        setScreen(mc, new TitleScreen());
    }

    // --- worlds

    /** Creates and joins a creative, peaceful, cheats-enabled singleplayer world. */
    public static void createTestWorld(Minecraft mc, String levelName, long seed, boolean flat) {
        //? if >=26.1 {
        /*LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        *///?} else if >=1.21.11 {
        LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.gamerules.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        //?} else if >=1.21.2 {
        /*LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        *///?} else {
        /*LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        *///?}
        Screen parent = screen(mc) == null ? new GenericMessageScreen(Component.literal("Minecraft MCP")) : screen(mc);
        //? if >=1.21.2 {
        mc.createWorldOpenFlows().createFreshLevel(levelName, settings, new WorldOptions(seed, false, false), registries -> flat
                ? registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                : WorldPresets.createNormalWorldDimensions(registries), parent);
        //?} else {
        /*mc.createWorldOpenFlows().createFreshLevel(levelName, settings, new WorldOptions(seed, false, false), registries -> flat
                ? registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                : WorldPresets.createNormalWorldDimensions(registries), parent);
        *///?}
    }

    // --- containers

    /** A slot click through the normal client path; {@code clickType} is the enum constant name (PICKUP, ...). */
    public static void clickSlot(Minecraft mc, int containerId, int slot, int button, String clickType) {
        //? if >=26.1 {
        /*mc.gameMode.handleContainerInput(containerId, slot, button,
                net.minecraft.world.inventory.ContainerInput.valueOf(clickType), mc.player);
        *///?} else {
        mc.gameMode.handleInventoryMouseClick(containerId, slot, button,
                net.minecraft.world.inventory.ClickType.valueOf(clickType), mc.player);
        //?}
    }
}
