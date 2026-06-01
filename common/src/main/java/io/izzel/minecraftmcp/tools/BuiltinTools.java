package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.*;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.phys.Vec3;

public final class BuiltinTools {
    private BuiltinTools() {}
    public static void register(ToolRegistry registry, MinecraftClientBridge bridge, ScenarioEngine scenarios) {
        registry.register(simple("mc.client.state", "Get Minecraft client state", args -> bridge.submit(() -> bridge.snapshot().toMap()).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.player.state", "Get player state", args -> bridge.submit(() -> bridge.snapshot().toMap()).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.screen.current", "Get current screen", args -> Map.of("screen", bridge.submit(() -> bridge.snapshot().screen()).get(10, TimeUnit.SECONDS))));
        registry.register(simple("mc.ticks.wait", "Wait client ticks", args -> { long ticks = ((Number)args.getOrDefault("ticks", 1)).longValue(); bridge.waitTicks(ticks); return Map.of("waitedTicks", ticks); }));
        registry.register(simple("mc.debug.capabilities", "Return MCP mod capabilities", args -> bridge.capabilities()));
        registry.register(simple("mc.scenario.batch.run", "Run scenarios from a directory", args -> { ScenarioRunOptions.Builder options = ScenarioRunOptions.builder().loader(bridge.loader()); addTags(args.get("includeTags"), true, options); addTags(args.get("excludeTags"), false, options); return scenarios.runBatch(String.valueOf(args.getOrDefault("directory", "")), options.build()).toMap(); }));
        registry.register(simple("mc.scenario.report", "Return latest scenario report", args -> scenarios.latestReport().map(r -> r.toMap()).orElse(Map.of("status", "none"))));
        registry.register(simple("mc.keyboard.press", "Press a key by name", args -> { String key = String.valueOf(args.getOrDefault("key", "")); bridge.submit(() -> { bridge.pressKey(key); return null; }).get(10, TimeUnit.SECONDS); return Map.of("status", "pressed", "key", key); }));
        registry.register(simple("mc.keyboard.hold", "Hold a key by name for a number of ticks", args -> { String key = String.valueOf(args.getOrDefault("key", "")); long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue(); bridge.submit(() -> { bridge.setKeyDown(key, true); return null; }).get(10, TimeUnit.SECONDS); bridge.waitTicks(ticks); bridge.submit(() -> { bridge.setKeyDown(key, false); return null; }).get(10, TimeUnit.SECONDS); return Map.of("status", "held", "key", key, "ticks", ticks); }));
        registry.register(simple("mc.player.swing", "Swing player hand and send the normal client interaction packet", args -> { String hand = String.valueOf(args.getOrDefault("hand", "main")); bridge.submit(() -> { bridge.swing(hand); return null; }).get(10, TimeUnit.SECONDS); return Map.of("status", "swung", "hand", hand); }));
        registry.register(simple("mc.player.look", "Set player yaw and pitch", args -> { float yaw = ((Number) args.getOrDefault("yaw", 0)).floatValue(); float pitch = ((Number) args.getOrDefault("pitch", 0)).floatValue(); return bridge.look(yaw, pitch); }));
        registry.register(simple("mc.player.look_at", "Rotate player to look at a world position", args -> { double x = number(args.get("x"), "x"); double y = number(args.get("y"), "y"); double z = number(args.get("z"), "z"); return bridge.lookAt(x, y, z); }));
        registry.register(simple("mc.player.use_item", "Use the currently held item with main hand or offhand", args -> { String hand = normalizeHand(args.getOrDefault("hand", "main")); return bridge.useItem(hand); }));
        registry.register(simple("mc.player.attack.block", "Attack or start breaking a block through the normal client interaction path", args -> { int x = ((Number) args.getOrDefault("x", 0)).intValue(); int y = ((Number) args.getOrDefault("y", 0)).intValue(); int z = ((Number) args.getOrDefault("z", 0)).intValue(); String face = normalizeFace(args.getOrDefault("face", "up")); return bridge.attackBlock(x, y, z, face); }));
        registry.register(simple("mc.player.destroy.block", "Keep breaking a block through the normal client interaction path until it is gone or timeout expires", args -> { int x = ((Number) args.getOrDefault("x", 0)).intValue(); int y = ((Number) args.getOrDefault("y", 0)).intValue(); int z = ((Number) args.getOrDefault("z", 0)).intValue(); String face = normalizeFace(args.getOrDefault("face", "up")); return bridge.destroyBlock(x, y, z, face); }));
        registry.register(simple("mc.player.drop", "Drop the selected item stack or a single item", args -> { boolean all = Boolean.parseBoolean(String.valueOf(args.getOrDefault("all", false))); return bridge.dropSelected(all); }));
        registry.register(simple("mc.player.jump", "Make the player jump once", args -> bridge.jump()));
        registry.register(simple("mc.vehicle.state", "Get player vehicle state", args -> bridge.submit(bridge::vehicleState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.command.run", "Send a slash command through the current client connection", args -> { String command = String.valueOf(args.getOrDefault("command", "")); return bridge.submit(() -> bridge.runCommand(command)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.command.suggest", "Request vanilla command suggestions and wait for the matching server response", args -> { String command = String.valueOf(args.getOrDefault("command", args.getOrDefault("text", "/"))); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); return bridge.commandSuggest(command, timeoutMs); }));
        registry.register(simple("mc.server.sync", "Synchronize with the server using a vanilla command suggestion round-trip", args -> { long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); Map<String,Object> result = bridge.commandSuggest("/", timeoutMs); java.util.Map<String,Object> synced = new java.util.LinkedHashMap<>(result); synced.put("status", "synced"); return synced; }));
        registry.register(simple("mc.server.connect", "Connect the client to a multiplayer server, reconnecting if already connected", args -> { String address = String.valueOf(args.getOrDefault("address", args.getOrDefault("server", ""))); String name = String.valueOf(args.getOrDefault("name", address)); return bridge.submit(() -> bridge.connectServer(address, name)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.server.call", "Proxy a server-side MCP tool through the connected server plugin channel", args -> { String tool = String.valueOf(args.getOrDefault("tool", "")); Object rawArguments = args.get("arguments"); Map<String,Object> toolArgs = rawArguments instanceof Map<?,?> map ? (Map<String,Object>) map : Map.of(); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); return bridge.serverMcpCall(tool, toolArgs, timeoutMs); }));
        registry.register(simple("mc.chat.send", "Send a normal chat message through the current client connection", args -> { String message = String.valueOf(args.getOrDefault("message", args.getOrDefault("text", ""))); return bridge.submit(() -> bridge.sendChat(message)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.screen.state", "Get structured state for the current screen", args -> bridge.submit(bridge::screenState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.screen.text.type", "Type text into the current screen and optionally submit with Enter", args -> { String text = String.valueOf(args.getOrDefault("text", "")); boolean submit = Boolean.parseBoolean(String.valueOf(args.getOrDefault("submit", false))); return bridge.submit(() -> bridge.typeText(text, submit)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.screen.click.at", "Click the current screen at absolute GUI coordinates", args -> { double x = ((Number) args.getOrDefault("x", 0)).doubleValue(); double y = ((Number) args.getOrDefault("y", 0)).doubleValue(); int button = ((Number) args.getOrDefault("button", 0)).intValue(); return bridge.submit(() -> bridge.clickScreen(x, y, button)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.screen.widget.click", "Click a widget from mc.screen.state by id, or by exact message text", args -> { String id = String.valueOf(args.getOrDefault("id", "")); String message = String.valueOf(args.getOrDefault("message", args.getOrDefault("text", ""))); int button = ((Number) args.getOrDefault("button", 0)).intValue(); return bridge.submit(() -> bridge.clickWidget(id, message, button)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.server.disconnect.state", "Return disconnect screen/message state if the client is disconnected", args -> bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.disconnect.wait", "Wait for a disconnect screen, optionally matching messageContains", args -> { String needle = String.valueOf(args.getOrDefault("messageContains", "")); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs); Map<String,Object> state; do { state = bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS); Object msg = state.get("message"); if (Boolean.TRUE.equals(state.get("disconnected")) && (needle.isBlank() || (msg != null && String.valueOf(msg).contains(needle)))) return state; bridge.waitTicks(1); } while (System.currentTimeMillis() < deadline); state = bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS); java.util.Map<String,Object> result = new java.util.LinkedHashMap<>(state); result.put("matched", false); result.put("messageContains", needle); return result; }));
        registry.register(simple("mc.block.interact", "Right-click a block through the normal client interaction path", args -> { int x = ((Number) args.getOrDefault("x", 0)).intValue(); int y = ((Number) args.getOrDefault("y", 0)).intValue(); int z = ((Number) args.getOrDefault("z", 0)).intValue(); String face = String.valueOf(args.getOrDefault("face", "up")); String hand = String.valueOf(args.getOrDefault("hand", "main")); return bridge.submit(() -> bridge.interactBlock(x, y, z, face, hand)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.world.join", "Join a singleplayer world, creating it with supplied options when missing", args -> { String name = String.valueOf(args.getOrDefault("name", "minecraft_mcp_test_world")); boolean created = bridge.joinWorld(name, args); return Map.of("status", "joining", "created", created, "name", name); }));
        registry.register(simple("mc.world.leave", "Leave the current world to title", args -> { bridge.execute(bridge::leaveWorldToTitle); return Map.of("status", "left_to_title"); }));
        registry.register(simple("mc.condition.wait", "Wait until a client condition is true", args -> { String condition = String.valueOf(args.getOrDefault("condition", "client.inWorld == true")); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); boolean matched = bridge.waitUntil(condition, timeoutMs); return Map.of("condition", condition, "matched", matched); }));
        registry.register(simple("mc.world.snapshot", "Get current world snapshot", args -> bridge.submit(bridge::worldSnapshot).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.inventory.state", "Get player inventory snapshot", args -> bridge.submit(bridge::inventorySnapshot).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.inventory.find", "Find item stacks in the player inventory", args -> { Map<String,Object> checked = requireInventoryItem(args); return bridge.submit(() -> bridge.findInventoryItem(checked)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.inventory.count", "Count matching items in the player inventory", args -> { Map<String,Object> checked = requireInventoryItem(args); return bridge.submit(() -> bridge.countInventoryItem(checked)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.inventory.selected", "Get the selected hotbar item", args -> bridge.submit(bridge::selectedInventoryItem).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.container.state", "Get current player container/menu state", args -> bridge.submit(bridge::containerState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.container.click", "Click a container slot using the normal client interaction path", args -> { int slot = slot(args); int button = ((Number) args.getOrDefault("button", 0)).intValue(); String clickType = clickType(args.getOrDefault("clickType", "PICKUP")); return bridge.submit(() -> bridge.clickContainer(slot, button, clickType)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.container.quick_move", "Shift-click / quick-move a container slot", args -> { int slot = slot(args); return bridge.submit(() -> bridge.clickContainer(slot, 0, "QUICK_MOVE")).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.container.drop", "Drop one or all items from a container slot", args -> { int slot = slot(args); boolean all = Boolean.parseBoolean(String.valueOf(args.getOrDefault("all", false))); return bridge.submit(() -> bridge.clickContainer(slot, all ? 1 : 0, "THROW")).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.container.close", "Close the currently open container/menu", args -> bridge.submit(bridge::closeContainer).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.hotbar.select", "Select a hotbar slot by zero-based index", args -> { int slot = ((Number) args.getOrDefault("slot", args.getOrDefault("index", 0))).intValue(); return bridge.submit(() -> bridge.selectHotbarSlot(slot)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.block.state", "Get block state at coordinates", args -> { int x = ((Number) args.getOrDefault("x", 0)).intValue(); int y = ((Number) args.getOrDefault("y", 0)).intValue(); int z = ((Number) args.getOrDefault("z", 0)).intValue(); return bridge.submit(() -> bridge.blockAt(x, y, z)).get(10, TimeUnit.SECONDS); }));
        registry.register(simple("mc.packet.recording.start", "Start client packet recording", bridge::startPacketRecording));
        registry.register(simple("mc.packet.recording.stop", "Stop client packet recording", args -> bridge.stopPacketRecording()));
        registry.register(simple("mc.packet.recording.clear", "Clear recorded packets", args -> bridge.clearPacketRecording()));
        registry.register(simple("mc.packet.recording.status", "Return packet recording status", args -> bridge.packetRecordingStatus()));
        registry.register(simple("mc.packet.dump", "Dump recorded packets with optional filters", bridge::dumpPackets));
        registry.register(simple("mc.packet.wait", "Wait until recorded packets matching a filter reach a required count", bridge::waitForPackets));
        registry.register(simple("mc.screenshot.take", "Take a client screenshot and save it under the game directory", args -> bridge.submit(() -> bridge.takeScreenshot(args)).get(30, TimeUnit.SECONDS)));
        registry.register(simple("mc.movement.waypoints", "Move the client player through one or more waypoints", args -> { List<Vec3> waypoints = parseWaypoints(args.get("waypoints")); boolean loop = Boolean.parseBoolean(String.valueOf(args.getOrDefault("loop", false))); int maxLoops = ((Number) args.getOrDefault("maxLoops", loop ? 0 : 1)).intValue(); double tolerance = ((Number) args.getOrDefault("tolerance", 0.75)).doubleValue(); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); boolean sprint = Boolean.parseBoolean(String.valueOf(args.getOrDefault("sprint", false))); boolean sneak = Boolean.parseBoolean(String.valueOf(args.getOrDefault("sneak", false))); boolean controlView = Boolean.parseBoolean(String.valueOf(args.getOrDefault("controlView", true))); return bridge.moveWaypoints(waypoints, loop, maxLoops, tolerance, timeoutMs, sprint, sneak, controlView); }));
    }
    private static void addTags(Object value, boolean include, ScenarioRunOptions.Builder options) {
        if (value instanceof Iterable<?> iterable) {
            for (Object tag : iterable) {
                if (include) options.includeTags(String.valueOf(tag)); else options.excludeTags(String.valueOf(tag));
            }
        } else if (value instanceof String tag && !tag.isBlank()) {
            if (include) options.includeTags(tag); else options.excludeTags(tag);
        }
    }
    private static List<Vec3> parseWaypoints(Object value) {
        if (!(value instanceof Iterable<?> iterable)) throw new IllegalArgumentException("waypoints must be an array");
        List<Vec3> result = new ArrayList<>();
        for (Object item : iterable) result.add(parseWaypoint(item));
        if (result.isEmpty()) throw new IllegalArgumentException("waypoints must not be empty");
        return result;
    }
    private static Vec3 parseWaypoint(Object item) {
        if (item instanceof Map<?, ?> map) {
            double x = number(map.get("x"), "waypoint.x");
            double y = number(map.get("y"), "waypoint.y");
            double z = number(map.get("z"), "waypoint.z");
            return new Vec3(x, y, z);
        }
        if (item instanceof List<?> list && list.size() >= 3) {
            return new Vec3(number(list.get(0), "waypoint[0]"), number(list.get(1), "waypoint[1]"), number(list.get(2), "waypoint[2]"));
        }
        throw new IllegalArgumentException("waypoint must be {x,y,z} or [x,y,z]");
    }
    private static double number(Object value, String name) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(name + " must be a number");
        return number.doubleValue();
    }
    private static int slot(Map<String,Object> args) {
        Object value = args.get("slot");
        if (!(value instanceof Number number)) throw new IllegalArgumentException("slot must be a non-negative number");
        int slot = number.intValue();
        if (slot < 0) throw new IllegalArgumentException("slot must be non-negative");
        return slot;
    }
    private static String clickType(Object value) {
        String type = String.valueOf(value == null ? "PICKUP" : value).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PICKUP", "QUICK_MOVE", "THROW").contains(type)) {
            throw new IllegalArgumentException("Unsupported clickType: " + value);
        }
        return type;
    }
    private static String normalizeHand(Object value) {
        String hand = String.valueOf(value == null ? "main" : value).trim().toLowerCase(Locale.ROOT);
        if (hand.equals("off") || hand.equals("off_hand")) hand = "offhand";
        if (!Set.of("main", "mainhand", "offhand").contains(hand)) throw new IllegalArgumentException("Unsupported hand: " + value);
        return hand.equals("mainhand") ? "main" : hand;
    }
    private static String normalizeFace(Object value) {
        String face = String.valueOf(value == null ? "up" : value).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("up", "down", "north", "south", "west", "east").contains(face)) throw new IllegalArgumentException("Unsupported face: " + value);
        return face;
    }
    private static Map<String,Object> requireInventoryItem(Map<String,Object> args) {
        Object value = args.get("item");
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("item is required");
        return args;
    }
    private static McpTool simple(String name, String desc, ToolBody body) {
        return new McpTool() {
            public String name() { return name; }
            public String description() { return desc; }
            public Map<String,Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String,Object> arguments) throws Exception { return body.call(arguments); }
        };
    }
    interface ToolBody { Object call(Map<String,Object> args) throws Exception; }
}
