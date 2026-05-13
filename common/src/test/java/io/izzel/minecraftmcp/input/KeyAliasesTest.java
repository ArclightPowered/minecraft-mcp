package io.izzel.minecraftmcp.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyAliasesTest {
    @Test
    void normalizesCommonKeysToMinecraftTranslationKeys() {
        assertEquals("key.keyboard.e", KeyAliases.normalize("E"));
        assertEquals("key.keyboard.w", KeyAliases.normalize("w"));
        assertEquals("key.keyboard.space", KeyAliases.normalize("SPACE"));
        assertEquals("key.keyboard.escape", KeyAliases.normalize("esc"));
        assertEquals("key.keyboard.f3", KeyAliases.normalize("F3"));
        assertEquals("key.keyboard.left.shift", KeyAliases.normalize("LEFT_SHIFT"));
        assertEquals("key.keyboard.e", KeyAliases.normalize("key.keyboard.e"));
    }
}
