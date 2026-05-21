package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicV3Test {
    @Test
    void writesAndReadsSpongeV3PaletteAndBlockDataInXzyOrder() throws Exception {
        Path file = Files.createTempFile("minecraft-mcp-schematic", ".schem");
        Schematic schematic = new Schematic(
                2, 2, 2,
                new int[] {0, 0, 0},
                List.of("minecraft:air", "minecraft:stone", "minecraft:oak_log[axis=y]"),
                new int[] {0, 1, 2, 1, 2, 0, 1, 2},
                List.of(),
                new int[0],
                Map.of("Name", "unit"),
                new ListTag(),
                new ListTag()
        );

        SpongeSchematicV3.write(file, schematic, 3955);
        SchematicInfo info = SpongeSchematicV3.info(file);
        Schematic read = SpongeSchematicV3.read(file);

        assertEquals("sponge-v3", info.format());
        assertEquals(3, info.version());
        assertEquals(3955, info.dataVersion());
        assertEquals(2, info.width());
        assertEquals(2, info.height());
        assertEquals(2, info.length());
        assertEquals(8, info.volume());
        assertEquals(3, info.paletteSize());
        assertEquals("minecraft:oak_log[axis=y]", read.blockStateAt(0, 1, 0));
        assertEquals("minecraft:stone", read.blockStateAt(1, 0, 0));
        assertArrayEquals(schematic.blockData(), read.blockData());
    }

    @Test
    void rejectsNonV3Schematic() throws Exception {
        Path file = Files.createTempFile("minecraft-mcp-schematic-v2", ".schem");
        Schematic schematic = new Schematic(1, 1, 1, new int[] {0, 0, 0}, List.of("minecraft:stone"), new int[] {0}, List.of(), new int[0], Map.of(), new ListTag(), new ListTag());
        SpongeSchematicV3.write(file, schematic, 3955);
        SpongeSchematicV3.forceVersionForTest(file, 2);

        assertThrows(IllegalArgumentException.class, () -> SpongeSchematicV3.info(file));
    }
}
