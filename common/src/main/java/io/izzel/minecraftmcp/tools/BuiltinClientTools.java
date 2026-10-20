package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.chat.ChatLog;
import io.izzel.minecraftmcp.mcp.*;
import io.izzel.minecraftmcp.packet.PacketFilter;
import io.izzel.minecraftmcp.util.Images;

import static io.izzel.minecraftmcp.mcp.McpTools.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.phys.Vec3;

public final class BuiltinClientTools {
    private BuiltinClientTools() {
    }

    // Arguments shared by several tools. Each Param reads the value and describes it in the tool's schema.
    private static final Param<Integer> X = Param.integer("x", "block X").required();
    private static final Param<Integer> Y = Param.integer("y", "block Y").required();
    private static final Param<Integer> Z = Param.integer("z", "block Z").required();
    private static final Param<String> FACE = Param.string("face", "block face: up, down, north, south, west, east").or("up");
    private static final Param<String> HAND = Param.string("hand", "main or offhand").or("main");
    private static final Param<Long> TIMEOUT = Param.longInt("timeoutMs", "give up after this many milliseconds").or(30000L);
    private static final Param<Long> ACTION_TIMEOUT = Param.longInt("timeoutMs", "how long to wait for the action to finish").or(10000L);
    private static final Param<Long> TICKS = Param.longInt("ticks", "client ticks").or(1L);
    private static final Param<String> KEY = Param.string("key", "key name: escape, enter, e, t, space, f3, left.shift, mouse.left ...").required();
    private static final Param<Integer> BUTTON = Param.integer("button", "mouse button, 0 = left").or(0);
    private static final Param<Integer> SLOT = Param.integer("slot", "menu slot index, see container.state").required();
    private static final Param<String> ITEM = Param.string("item", "item id, e.g. minecraft:diamond").required();
    private static final Param<String> SECTION = Param.string("section", "all, hotbar, main, armor or offhand").or("all");
    private static final Param<Boolean> WAIT = Param.bool("wait", "block until it finished (or timeoutMs passed)").or(false);
    private static final Param<String> CONDITION = Param.string("condition", "expression over client state, e.g. client.inWorld == true").required();
    private static final Param<Long> INTERVAL = Param.longInt("intervalTicks", "ticks between checks").or(1L);
    private static final Param<Long> SINCE = Param.longInt("since", "only messages after this sequence number (from a previous read)").or(0L);
    private static final Param<String> CONTAINS = Param.string("contains", "text the message contains");
    private static final Param<String> REGEX = Param.string("regex", "Java regular expression the message matches (find)");

    public static void register(ToolRegistry registry, MinecraftClientBridge bridge) {
        // --- state and screens
        registry.register(tool("mc.client.state", "Get Minecraft client state: in world, screen class and title, player and position",
            args -> bridge.submit(() -> bridge.snapshot().toMap()).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.screen.current", "Get current screen class and title", args -> {
            var snapshot = bridge.submit(bridge::snapshot).get(10, TimeUnit.SECONDS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("screen", snapshot.screen());
            result.put("title", snapshot.screenTitle());
            return result;
        }));
        registry.register(tool("mc.client.screen.state", "Get structured state for the current screen: title, widgets with ids, text and positions",
            args -> bridge.submit(bridge::screenState).get(10, TimeUnit.SECONDS)));
        Param<String> text = Param.string("text", "text typed into the focused field").required();
        Param<Boolean> submit = Param.bool("submit", "press Enter afterwards").or(false);
        registry.register(tool("mc.client.screen.text.type", "Type text into the current screen and optionally submit with Enter", List.of(text, submit),
            args -> bridge.submit(() -> bridge.typeText(text.get(args), submit.get(args))).get(10, TimeUnit.SECONDS)));
        Param<Double> guiX = Param.number("x", "GUI x (scaled screen coordinates, see screen.state)").required();
        Param<Double> guiY = Param.number("y", "GUI y").required();
        registry.register(tool("mc.client.screen.click.at", "Click the current screen at absolute GUI coordinates", List.of(guiX, guiY, BUTTON),
            args -> bridge.submit(() -> bridge.clickScreen(guiX.get(args), guiY.get(args), BUTTON.get(args))).get(10, TimeUnit.SECONDS)));
        Param<String> widgetId = Param.string("id", "widget id from screen.state, e.g. widget-3").or("");
        Param<String> widgetMessage = Param.string("message", "exact widget text, used when id is empty").or("").alias("text");
        registry.register(tool("mc.client.screen.widget.click", "Click a widget from mc.client.screen.state by id, or by exact message text",
            List.of(widgetId, widgetMessage, BUTTON),
            args -> bridge.submit(() -> bridge.clickWidget(widgetId.get(args), widgetMessage.get(args), BUTTON.get(args))).get(10, TimeUnit.SECONDS)));
        Param<String> screenshotName = Param.string("name", "file name under screenshots/ (default: timestamped)").or("");
        Param<Boolean> inline = Param.bool("inline", "also return the picture as MCP image content").or(false);
        Param<Integer> maxWidth = Param.integer("maxWidth", "with inline: scale the returned picture down to this width (0 = full size)").or(1024);
        registry.register(tool("mc.client.screenshot.take", "Take a client screenshot, save it under the game directory and optionally return it inline",
            List.of(screenshotName, inline, maxWidth), args -> {
                Map<String, Object> saved = bridge.takeScreenshot(screenshotName.get(args));
                if (!inline.get(args)) return saved;
                Map<String, Object> result = new LinkedHashMap<>(saved);
                result.put("image", Map.of("mimeType", "image/png", "data",
                    Images.pngBase64(java.nio.file.Path.of(String.valueOf(saved.get("absolutePath"))), maxWidth.get(args))));
                return result;
            }));

        // --- input
        registry.register(tool("mc.client.keyboard.press", "Press and release a key by name", List.of(KEY), args -> {
            String key = KEY.get(args);
            bridge.submit(() -> {
                bridge.pressKey(key);
                return null;
            }).get(10, TimeUnit.SECONDS);
            return Map.of("status", "pressed", "key", key);
        }));
        registry.register(tool("mc.client.keyboard.hold", "Hold a key by name for a number of ticks", List.of(KEY, TICKS), args -> {
            String key = KEY.get(args);
            long ticks = TICKS.get(args);
            bridge.submit(() -> {
                bridge.setKeyDown(key, true);
                return null;
            }).get(10, TimeUnit.SECONDS);
            bridge.waitTicks(ticks);
            bridge.submit(() -> {
                bridge.setKeyDown(key, false);
                return null;
            }).get(10, TimeUnit.SECONDS);
            return Map.of("status", "held", "key", key, "ticks", ticks);
        }));
        registry.register(tool("mc.client.ticks.wait", "Wait client ticks", List.of(TICKS), args -> {
            long ticks = TICKS.get(args);
            bridge.waitTicks(ticks);
            return Map.of("waitedTicks", ticks);
        }));
        registry.register(tool("mc.client.condition.wait", "Wait until a client condition is true", List.of(CONDITION, TIMEOUT, INTERVAL), args -> {
            String condition = CONDITION.get(args);
            long intervalTicks = INTERVAL.get(args);
            boolean matched = bridge.waitUntil(condition, TIMEOUT.get(args), intervalTicks);
            return Map.of("condition", condition, "matched", matched, "intervalTicks", Math.max(1, intervalTicks));
        }));

        // --- player actions
        registry.register(tool("mc.client.player.swing", "Swing player hand and send the normal client interaction packet", List.of(HAND), args -> {
            String hand = HAND.get(args);
            bridge.submit(() -> {
                bridge.swing(hand);
                return null;
            }).get(10, TimeUnit.SECONDS);
            return Map.of("status", "swung", "hand", hand);
        }));
        Param<Double> yaw = Param.number("yaw", "yaw in degrees").or(0.0);
        Param<Double> pitch = Param.number("pitch", "pitch in degrees").or(0.0);
        registry.register(tool("mc.client.player.look", "Set player yaw and pitch", List.of(yaw, pitch, ACTION_TIMEOUT),
            args -> bridge.look(yaw.get(args).floatValue(), pitch.get(args).floatValue())));
        Param<Double> lookX = Param.number("x", "target X").required();
        Param<Double> lookY = Param.number("y", "target Y").required();
        Param<Double> lookZ = Param.number("z", "target Z").required();
        registry.register(tool("mc.client.player.look_at", "Rotate player to look at a world position", List.of(lookX, lookY, lookZ, ACTION_TIMEOUT),
            args -> bridge.lookAt(lookX.get(args), lookY.get(args), lookZ.get(args))));
        registry.register(tool("mc.client.player.use_item", "Use the currently held item with main hand or offhand", List.of(HAND, ACTION_TIMEOUT),
            args -> bridge.useItem(normalizeHand(HAND.get(args)))));
        registry.register(tool("mc.client.player.attack.block", "Attack or start breaking a block through the normal client interaction path",
            List.of(X, Y, Z, FACE, ACTION_TIMEOUT),
            args -> bridge.attackBlock(X.get(args), Y.get(args), Z.get(args), normalizeFace(FACE.get(args)))));
        registry.register(tool("mc.client.player.destroy.block", "Keep breaking a block through the normal client interaction path until it is gone or timeout expires",
            List.of(X, Y, Z, FACE, ACTION_TIMEOUT),
            args -> bridge.destroyBlock(X.get(args), Y.get(args), Z.get(args), normalizeFace(FACE.get(args)))));
        Param<Boolean> dropAll = Param.bool("all", "drop the whole stack instead of one item").or(false);
        registry.register(tool("mc.client.player.drop", "Drop the selected item stack or a single item", List.of(dropAll, ACTION_TIMEOUT),
            args -> bridge.dropSelected(dropAll.get(args))));
        registry.register(tool("mc.client.player.jump", "Make the player jump once", List.of(ACTION_TIMEOUT), args -> bridge.jump()));
        registry.register(tool("mc.client.vehicle.state", "Get player vehicle state", args -> bridge.submit(bridge::vehicleState).get(10, TimeUnit.SECONDS)));
        Param<List<?>> waypoints = Param.array("waypoints", "points to walk through: [{x,y,z}, ...] or [[x,y,z], ...]").required();
        Param<Boolean> loop = Param.bool("loop", "repeat the route").or(false);
        Param<Integer> maxLoops = Param.integer("maxLoops", "route repetitions when looping (0 = until timeout)");
        Param<Double> tolerance = Param.number("tolerance", "distance that counts as arrived").or(0.75);
        Param<Boolean> sprint = Param.bool("sprint", "sprint while moving").or(false);
        Param<Boolean> sneak = Param.bool("sneak", "sneak while moving").or(false);
        Param<Boolean> controlView = Param.bool("controlView", "turn the camera towards each point").or(true);
        registry.register(tool("mc.client.movement.waypoints", "Move the client player through one or more waypoints",
            List.of(waypoints, loop, maxLoops, tolerance, TIMEOUT, sprint, sneak, controlView), args -> {
                boolean looping = loop.get(args);
                Integer loops = maxLoops.get(args);
                return bridge.moveWaypoints(parseWaypoints(waypoints.get(args)), looping, loops != null ? loops : (looping ? 0 : 1),
                    tolerance.get(args), TIMEOUT.get(args), sprint.get(args), sneak.get(args), controlView.get(args));
            }));

        // --- world around the player
        registry.register(tool("mc.client.block.state", "Get block state at coordinates", List.of(X, Y, Z),
            args -> bridge.submit(() -> bridge.blockAt(X.get(args), Y.get(args), Z.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.block.interact", "Right-click a block through the normal client interaction path", List.of(X, Y, Z, FACE, HAND),
            args -> bridge.submit(() -> bridge.interactBlock(X.get(args), Y.get(args), Z.get(args), FACE.get(args), HAND.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.target", "What the crosshair points at: a block (position, face, id), an entity, or nothing",
            args -> bridge.submit(bridge::crosshairTarget).get(10, TimeUnit.SECONDS)));
        Param<Double> radius = Param.number("radius", "search radius in blocks").or(16.0);
        Param<String> entityType = Param.string("type", "only this entity type id, e.g. minecraft:zombie");
        Param<Integer> limit = Param.integer("limit", "max results, nearest first").or(20);
        registry.register(tool("mc.client.entities.nearby", "Entities the client sees around the player, nearest first; works on any server",
            List.of(radius, entityType, limit),
            args -> bridge.submit(() -> bridge.nearbyEntities(radius.get(args), entityType.get(args), limit.get(args))).get(10, TimeUnit.SECONDS)));
        Param<String> blockId = Param.string("block", "block id, e.g. minecraft:diamond_ore").required();
        Param<Integer> blockRadius = Param.integer("radius", "half size of the searched cube around the player, up to 32").or(8);
        registry.register(tool("mc.client.blocks.find", "Find blocks of one type around the player, nearest first",
            List.of(blockId, blockRadius, limit), args -> {
                int r = Math.min(32, Math.max(0, blockRadius.get(args)));
                return bridge.submit(() -> bridge.findBlocks(blockId.get(args), r, limit.get(args))).get(30, TimeUnit.SECONDS);
            }));

        // --- worlds and connections
        Param<String> worldName = Param.string("name", "singleplayer world name; created when missing").or("minecraft_mcp_test_world");
        Param<Long> seed = Param.longInt("seed", "seed for a new world").or(0L);
        Param<String> preset = Param.string("preset", "normal or flat, for a new world").or("normal").alias("generator");
        Param<Boolean> allowCommands = Param.bool("allowCommands", "accepted for compatibility: a new test world always allows commands");
        registry.register(tool("mc.client.world.join", "Join a singleplayer world, creating it when missing; wait=true returns once in the world",
            List.of(worldName, seed, preset, allowCommands, WAIT, TIMEOUT), args -> {
                String name = worldName.get(args);
                String kind = preset.get(args).trim().toLowerCase(Locale.ROOT);
                boolean created = bridge.joinWorld(name, seed.get(args), kind.equals("flat") || kind.equals("superflat"));
                Map<String, Object> result = new LinkedHashMap<>(Map.of("status", "joining", "created", created, "name", name));
                if (WAIT.get(args)) result.put("ready", bridge.waitUntil("client.inWorld == true", TIMEOUT.get(args), 10));
                return result;
            }));
        registry.register(tool("mc.client.world.leave", "Leave the current world to the title screen; wait=true returns once out of it",
            List.of(WAIT, TIMEOUT), args -> {
                bridge.execute(bridge::leaveWorldToTitle);
                Map<String, Object> result = new LinkedHashMap<>(Map.of("status", "left_to_title"));
                if (WAIT.get(args)) result.put("ready", bridge.waitUntil("client.rawInWorld == false", TIMEOUT.get(args), 5));
                return result;
            }));
        registry.register(tool("mc.client.world.snapshot", "Get current world snapshot", args -> bridge.submit(bridge::worldSnapshot).get(10, TimeUnit.SECONDS)));
        Param<String> address = Param.string("address", "server address, host[:port]").required().alias("server");
        Param<String> serverName = Param.string("name", "server list name (default: the address)");
        registry.register(tool("mc.client.connection.connect", "Connect to a multiplayer server, reconnecting if already connected; wait=true returns once in the world or disconnected",
            List.of(address, serverName, WAIT, TIMEOUT), args -> {
                String target = address.get(args);
                String name = serverName.get(args) == null ? target : serverName.get(args);
                Map<String, Object> result = new LinkedHashMap<>(bridge.submit(() -> bridge.connectServer(target, name)).get(10, TimeUnit.SECONDS));
                if (WAIT.get(args) && "connecting".equals(result.get("status"))) {
                    bridge.waitUntil("client.inWorld == true || connection.disconnected == true", TIMEOUT.get(args), 10);
                    result.put("inWorld", bridge.submit(() -> bridge.snapshot().inWorld()).get(10, TimeUnit.SECONDS));
                    result.put("connection", bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS));
                }
                return result;
            }));
        registry.register(tool("mc.client.connection.state", "Return disconnect screen/message state if the client is disconnected",
            args -> bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.connection.sync", "Synchronize with the server using a vanilla command suggestion round-trip", List.of(TIMEOUT), args -> {
            Map<String, Object> synced = new LinkedHashMap<>(bridge.commandSuggest("/", TIMEOUT.get(args)));
            synced.put("status", "synced");
            return synced;
        }));
        Param<String> messageContains = Param.string("messageContains", "only match a disconnect message containing this").or("");
        registry.register(tool("mc.client.connection.wait_disconnect", "Wait for a disconnect screen, optionally matching messageContains",
            List.of(messageContains, TIMEOUT), args -> {
                String needle = messageContains.get(args);
                long deadline = System.currentTimeMillis() + Math.max(0, TIMEOUT.get(args));
                Map<String, Object> state;
                do {
                    state = bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS);
                    Object msg = state.get("message");
                    if (Boolean.TRUE.equals(state.get("disconnected")) && (needle.isBlank() || (msg != null && String.valueOf(msg).contains(needle))))
                        return state;
                    bridge.waitTicks(1);
                } while (System.currentTimeMillis() < deadline);
                state = bridge.submit(bridge::disconnectState).get(10, TimeUnit.SECONDS);
                Map<String, Object> result = new LinkedHashMap<>(state);
                result.put("matched", false);
                result.put("messageContains", needle);
                return result;
            }));

        // --- chat and commands
        Param<String> message = Param.string("message", "chat message; use command.run for commands").required().alias("text");
        registry.register(tool("mc.client.chat.send", "Send a normal chat message through the current client connection", List.of(message),
            args -> bridge.submit(() -> bridge.sendChat(message.get(args))).get(10, TimeUnit.SECONDS)));
        Param<Integer> chatLimit = Param.integer("limit", "max messages, newest last").or(50);
        registry.register(tool("mc.client.chat.read", "Chat and system messages this client received (command feedback, server messages); pass the returned last as since next time",
            List.of(SINCE, CONTAINS, REGEX, chatLimit), args -> {
                var log = bridge.chatLog();
                long last = log.lastSequence();
                List<Map<String, Object>> messages = log.read(SINCE.get(args), chatLimit.get(args), chatFilter(args)).stream()
                    .map(ChatLog.Entry::toMap).toList();
                return Map.of("messages", messages, "last", last);
            }));
        registry.register(tool("mc.client.chat.wait", "Wait for a chat or system message matching contains/regex, received after since",
            List.of(SINCE, CONTAINS, REGEX, TIMEOUT), args -> {
                var log = bridge.chatLog();
                ChatLog.Entry entry = log.await(SINCE.get(args), chatFilter(args), TIMEOUT.get(args));
                return entry == null ? Map.of("matched", false, "last", log.lastSequence())
                    : Map.of("matched", true, "message", entry.toMap(), "last", log.lastSequence());
            }));
        Param<String> command = Param.string("command", "command, with or without the leading /").required();
        registry.register(tool("mc.client.command.run", "Send a slash command through the current client connection; read its feedback with chat.read",
            List.of(command), args -> bridge.submit(() -> bridge.runCommand(command.get(args))).get(10, TimeUnit.SECONDS)));
        Param<String> partial = Param.string("command", "partial command to complete, e.g. /tim").or("/").alias("text");
        registry.register(tool("mc.client.command.suggest", "Request vanilla command suggestions and wait for the matching server response",
            List.of(partial, TIMEOUT), args -> bridge.commandSuggest(partial.get(args), TIMEOUT.get(args))));

        // --- inventory and containers
        registry.register(tool("mc.client.inventory.state", "Get player inventory snapshot", args -> bridge.submit(bridge::inventorySnapshot).get(10, TimeUnit.SECONDS)));
        Param<Integer> findLimit = Param.integer("limit", "max matching stacks returned").or(50);
        registry.register(tool("mc.client.inventory.find", "Find item stacks in the player inventory", List.of(ITEM, SECTION, findLimit),
            args -> bridge.submit(() -> bridge.findInventoryItem(ITEM.get(args), SECTION.get(args), findLimit.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.inventory.count", "Count matching items in the player inventory", List.of(ITEM, SECTION),
            args -> bridge.submit(() -> bridge.countInventoryItem(ITEM.get(args), SECTION.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.inventory.selected", "Get the selected hotbar item", args -> bridge.submit(bridge::selectedInventoryItem).get(10, TimeUnit.SECONDS)));
        Param<Integer> hotbarSlot = Param.integer("slot", "hotbar slot 0-8").or(0).alias("index");
        registry.register(tool("mc.client.hotbar.select", "Select a hotbar slot by zero-based index", List.of(hotbarSlot),
            args -> bridge.submit(() -> bridge.selectHotbarSlot(hotbarSlot.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.client.container.state", "Get current player container/menu state", args -> bridge.submit(bridge::containerState).get(10, TimeUnit.SECONDS)));
        Param<String> clickTypeParam = Param.string("clickType", "PICKUP, QUICK_MOVE or THROW").or("PICKUP");
        registry.register(tool("mc.client.container.click", "Click a container slot using the normal client interaction path", List.of(SLOT, BUTTON, clickTypeParam), args -> {
            int slot = slot(args);
            int button = BUTTON.get(args);
            String type = clickType(clickTypeParam.get(args));
            return bridge.submit(() -> bridge.clickContainer(slot, button, type)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(tool("mc.client.container.quick_move", "Shift-click / quick-move a container slot", List.of(SLOT), args -> {
            int slot = slot(args);
            return bridge.submit(() -> bridge.clickContainer(slot, 0, "QUICK_MOVE")).get(10, TimeUnit.SECONDS);
        }));
        registry.register(tool("mc.client.container.drop", "Drop one or all items from a container slot", List.of(SLOT, dropAll), args -> {
            int slot = slot(args);
            boolean all = dropAll.get(args);
            return bridge.submit(() -> bridge.clickContainer(slot, all ? 1 : 0, "THROW")).get(10, TimeUnit.SECONDS);
        }));
        registry.register(tool("mc.client.container.close", "Close the currently open container/menu", args -> bridge.submit(bridge::closeContainer).get(10, TimeUnit.SECONDS)));

        // --- packets
        Param<Integer> maxPackets = Param.integer("maxPackets", "ring buffer size").or(1000);
        Param<Boolean> clear = Param.bool("clear", "drop packets recorded earlier").or(true);
        registry.register(tool("mc.client.packet.recording.start", "Start client packet recording", concat(PacketFilter.PARAMS, maxPackets, clear),
            args -> bridge.startPacketRecording(PacketFilter.from(args), maxPackets.get(args), clear.get(args))));
        registry.register(tool("mc.client.packet.recording.stop", "Stop client packet recording", args -> bridge.stopPacketRecording()));
        registry.register(tool("mc.client.packet.recording.clear", "Clear recorded packets", args -> bridge.clearPacketRecording()));
        registry.register(tool("mc.client.packet.recording.status", "Return packet recording status", args -> bridge.packetRecordingStatus()));
        registry.register(tool("mc.client.packet.dump", "Dump recorded packets with optional filters", PacketFilter.PARAMS,
            args -> bridge.dumpPackets(PacketFilter.from(args))));
        Param<Integer> count = Param.integer("count", "packets required").or(1).alias("required");
        registry.register(tool("mc.client.packet.wait", "Wait until recorded packets matching a filter reach a required count",
            concat(PacketFilter.PARAMS, count, TIMEOUT),
            args -> bridge.waitForPackets(PacketFilter.from(args), count.get(args), TIMEOUT.get(args))));
    }

    private static List<Param<?>> concat(List<Param<?>> base, Param<?>... more) {
        List<Param<?>> all = new ArrayList<>(base);
        all.addAll(List.of(more));
        return all;
    }

    private static java.util.function.Predicate<String> chatFilter(Map<String, Object> args) {
        String contains = CONTAINS.get(args);
        String regex = REGEX.get(args);
        java.util.regex.Pattern pattern = regex == null ? null : java.util.regex.Pattern.compile(regex);
        return text -> (contains == null || text.contains(contains)) && (pattern == null || pattern.matcher(text).find());
    }

    private static List<Vec3> parseWaypoints(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("waypoints must be an array");
        }
        List<Vec3> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(parseWaypoint(item));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("waypoints must not be empty");
        }
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
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return number.doubleValue();
    }

    private static int slot(Map<String, Object> args) {
        int slot = SLOT.get(args);
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
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
        if (hand.equals("off") || hand.equals("off_hand")) {
            hand = "offhand";
        }
        if (!Set.of("main", "mainhand", "offhand").contains(hand)) {
            throw new IllegalArgumentException("Unsupported hand: " + value);
        }
        return hand.equals("mainhand") ? "main" : hand;
    }

    private static String normalizeFace(Object value) {
        String face = String.valueOf(value == null ? "up" : value).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("up", "down", "north", "south", "west", "east").contains(face)) {
            throw new IllegalArgumentException("Unsupported face: " + value);
        }
        return face;
    }

}
