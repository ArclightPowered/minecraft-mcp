
package io.izzel.minecraftmcp.input;

import java.util.Locale;
import java.util.Map;

public final class KeyAliases {
    private KeyAliases() {}

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("SPACE", "key.keyboard.space"),
            Map.entry("ENTER", "key.keyboard.enter"),
            Map.entry("RETURN", "key.keyboard.enter"),
            Map.entry("ESC", "key.keyboard.escape"),
            Map.entry("ESCAPE", "key.keyboard.escape"),
            Map.entry("TAB", "key.keyboard.tab"),
            Map.entry("LEFT_SHIFT", "key.keyboard.left.shift"),
            Map.entry("RIGHT_SHIFT", "key.keyboard.right.shift"),
            Map.entry("LEFT_CONTROL", "key.keyboard.left.control"),
            Map.entry("RIGHT_CONTROL", "key.keyboard.right.control"),
            Map.entry("UP", "key.keyboard.up"),
            Map.entry("DOWN", "key.keyboard.down"),
            Map.entry("LEFT", "key.keyboard.left"),
            Map.entry("RIGHT", "key.keyboard.right")
    );

    public static String normalize(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        String trimmed = key.trim();
        if (trimmed.startsWith("key.keyboard.") || trimmed.startsWith("key.mouse.")) return trimmed;
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        if (NAMED.containsKey(upper)) return NAMED.get(upper);
        if (upper.length() == 1) {
            char c = upper.charAt(0);
            if (c >= 'A' && c <= 'Z') return "key.keyboard." + Character.toLowerCase(c);
            if (c >= '0' && c <= '9') return "key.keyboard." + c;
        }
        if (upper.matches("F([1-9]|1[0-2])")) return "key.keyboard." + upper.toLowerCase(Locale.ROOT);
        return "key.keyboard." + trimmed.toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
