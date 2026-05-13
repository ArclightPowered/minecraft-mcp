package io.izzel.minecraftmcp.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClientSnapshot(boolean running, boolean inWorld, String screen, String playerName, double x, double y, double z, float yaw, float pitch) {
    public Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("running", running); m.put("inWorld", inWorld); m.put("screen", screen); m.put("playerName", playerName);
        m.put("position", Map.of("x", x, "y", y, "z", z)); m.put("rotation", Map.of("yaw", yaw, "pitch", pitch));
        return m;
    }
}
