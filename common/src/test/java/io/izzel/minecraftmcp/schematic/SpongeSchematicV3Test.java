package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
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

    @Test
    void writesReadsAndReportsUnsignedShortDimensionsAtTheBoundary() throws Exception {
        for (int width : List.of(32768, 65535)) {
            Path file = Files.createTempFile("minecraft-mcp-schematic-unsigned-dimension", ".schem");
            Schematic schematic = new Schematic(width, 1, 1, new int[] {0, 0, 0}, List.of("minecraft:stone"), new int[width], List.of(), new int[0], Map.of(), new ListTag(), new ListTag());

            SpongeSchematicV3.write(file, schematic, 3955);

            assertEquals(width, SpongeSchematicV3.read(file).width());
            assertEquals(width, SpongeSchematicV3.info(file).width());
        }
    }

    @Test
    void rejectsDimensionAboveUnsignedShortMaximum() throws Exception {
        Path file = Files.createTempFile("minecraft-mcp-schematic-dimension-overflow", ".schem");
        Schematic schematic = new Schematic(65536, 1, 1, new int[] {0, 0, 0}, List.of("minecraft:stone"), new int[65536], List.of(), new int[0], Map.of(), new ListTag(), new ListTag());

        assertThrows(IllegalArgumentException.class, () -> SpongeSchematicV3.write(file, schematic, 3955));
    }

    @Test
    void preservesCompleteNbtForMetadataBlockEntitiesAndEntitiesAcrossReadWrite() throws Exception {
        Path source = Files.createTempFile("minecraft-mcp-schematic-source", ".schem");
        Path roundTrip = Files.createTempFile("minecraft-mcp-schematic-roundtrip", ".schem");
        CompoundTag metadata = new CompoundTag();
        metadata.putInt("NumericMetadata", 42);
        CompoundTag nestedMetadata = new CompoundTag();
        nestedMetadata.putString("author", "unit");
        metadata.put("Nested", nestedMetadata);
        ListTag authors = new ListTag();
        authors.add(StringTag.valueOf("one"));
        authors.add(StringTag.valueOf("two"));
        metadata.put("Authors", authors);

        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:chest");
        blockEntity.putInt("x", 0);
        blockEntity.putInt("y", 0);
        blockEntity.putInt("z", 0);
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        ListTag items = new ListTag();
        items.add(item);
        blockEntity.put("Items", items);
        ListTag blockEntities = new ListTag();
        blockEntities.add(blockEntity);

        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:armor_stand");
        entity.putBoolean("Invisible", true);
        ListTag entities = new ListTag();
        entities.add(entity);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putInt("DataVersion", 4550);
        root.putShort("Width", (short) 1);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 1);
        root.put("BlockPalette", palette("minecraft:chest"));
        root.putByteArray("BlockData", new byte[] {0});
        root.put("Metadata", metadata);
        root.put("BlockEntities", blockEntities);
        root.put("Entities", entities);
        NbtIo.writeCompressed(root, source);

        Schematic schematic = SpongeSchematicV3.read(source);
        SpongeSchematicV3.write(roundTrip, schematic, 4550);
        CompoundTag written = NbtIo.readCompressed(roundTrip, NbtAccounter.unlimitedHeap());

        assertEquals(metadata, io.izzel.minecraftmcp.compat.MinecraftCompat.compound(written, "Metadata").orElseThrow());
        assertEquals(blockEntities, io.izzel.minecraftmcp.compat.MinecraftCompat.list(written, "BlockEntities", net.minecraft.nbt.Tag.TAG_COMPOUND).orElseThrow());
        assertEquals(entities, io.izzel.minecraftmcp.compat.MinecraftCompat.list(written, "Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).orElseThrow());
    }

    private static CompoundTag palette(String state) {
        CompoundTag palette = new CompoundTag();
        palette.putInt(state, 0);
        return palette;
    }
}
