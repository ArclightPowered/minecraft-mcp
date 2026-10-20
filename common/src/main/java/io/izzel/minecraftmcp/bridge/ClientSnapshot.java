package io.izzel.minecraftmcp.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * screen is the screen's class name; screenTitle is its title text, which also identifies the screen on Fabric,
 * whose production class names are intermediary (class_442).
 */
public record ClientSnapshot(boolean running, boolean inWorld, boolean rawInWorld, String screen, String screenTitle, String playerName, double x, double y, double z, float yaw, float pitch) {
    public ClientSnapshot(boolean running, boolean inWorld, boolean rawInWorld, String screen, String playerName, double x, double y, double z, float yaw, float pitch) {
        this(running, inWorld, rawInWorld, screen, null, playerName, x, y, z, yaw, pitch);
    }
    public ClientSnapshot(boolean running, boolean inWorld, String screen, String playerName, double x, double y, double z, float yaw, float pitch) {
        this(running, inWorld, inWorld, screen, null, playerName, x, y, z, yaw, pitch);
    }
    public Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("running", running); m.put("inWorld", inWorld); m.put("rawInWorld", rawInWorld); m.put("screen", screen); m.put("screenTitle", screenTitle); m.put("playerName", playerName);
        m.put("position", Map.of("x", x, "y", y, "z", z)); m.put("rotation", Map.of("yaw", yaw, "pitch", pitch));
        return m;
    }
}
