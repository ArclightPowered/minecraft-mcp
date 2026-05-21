package io.izzel.minecraftmcp.schematic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchematicPathResolverTest {
    @Test
    void resolvesRelativePathsUnderGameSchematicsDirectory() throws Exception {
        Path gameDir = Files.createTempDirectory("minecraft-mcp-game");

        Path resolved = SchematicPathResolver.resolve(gameDir, "base.schem");

        assertEquals(gameDir.resolve("schematics/base.schem").normalize(), resolved);
        assertEquals(resolved, SchematicPathResolver.resolve(gameDir, "schematics/base.schem"));
    }

    @Test
    void rejectsPathTraversalOutsideSchematicsDirectory() throws Exception {
        Path gameDir = Files.createTempDirectory("minecraft-mcp-game");

        assertThrows(IllegalArgumentException.class, () -> SchematicPathResolver.resolve(gameDir, "../escape.schem"));
    }
}
