package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable Sponge v3 data model. Raw metadata NBT is retained so read/write does not alter its type structure. */
public final class Schematic {
    private final int width;
    private final int height;
    private final int length;
    private final int[] offset;
    private final List<String> palette;
    private final int[] blockData;
    private final List<String> biomePalette;
    private final int[] biomeData;
    private final Map<String, Object> metadata;
    private final CompoundTag metadataNbt;
    private final ListTag blockEntities;
    private final ListTag entities;

    public Schematic(int width, int height, int length, int[] offset, List<String> palette, int[] blockData,
                     List<String> biomePalette, int[] biomeData, Map<String, Object> metadata,
                     ListTag blockEntities, ListTag entities) {
        this(width, height, length, offset, palette, blockData, biomePalette, biomeData, metadata,
                SpongeSchematicV3.metadataToTag(metadata), blockEntities, entities);
    }

    public Schematic(int width, int height, int length, int[] offset, List<String> palette, int[] blockData,
                     List<String> biomePalette, int[] biomeData, Map<String, Object> metadata,
                     CompoundTag metadataNbt, ListTag blockEntities, ListTag entities) {
        if (width <= 0 || height <= 0 || length <= 0) throw new IllegalArgumentException("schematic dimensions must be positive");
        int expected;
        try {
            expected = Math.multiplyExact(Math.multiplyExact(width, height), length);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("schematic dimensions overflow", e);
        }
        if (blockData == null || blockData.length != expected) throw new IllegalArgumentException("block data length must equal width * height * length");
        if (offset == null || offset.length != 3) throw new IllegalArgumentException("offset must contain exactly 3 integers");
        if (palette == null || palette.isEmpty()) throw new IllegalArgumentException("block palette must not be empty");
        this.width = width;
        this.height = height;
        this.length = length;
        this.offset = offset.clone();
        this.palette = List.copyOf(palette);
        this.blockData = blockData.clone();
        this.biomePalette = List.copyOf(biomePalette == null ? List.of() : biomePalette);
        this.biomeData = biomeData == null ? new int[0] : biomeData.clone();
        this.metadata = copyMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        this.metadataNbt = metadataNbt == null ? new CompoundTag() : metadataNbt.copy();
        this.blockEntities = blockEntities == null ? new ListTag() : blockEntities.copy();
        this.entities = entities == null ? new ListTag() : entities.copy();
    }

    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public int[] offset() { return offset.clone(); }
    public List<String> palette() { return palette; }
    public int[] blockData() { return blockData.clone(); }
    public List<String> biomePalette() { return biomePalette; }
    public int[] biomeData() { return biomeData.clone(); }
    public Map<String, Object> metadata() { return copyMetadata(metadata); }
    CompoundTag metadataNbt() { return metadataNbt.copy(); }
    public ListTag blockEntities() { return blockEntities.copy(); }
    public ListTag entities() { return entities.copy(); }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() instanceof net.minecraft.nbt.Tag tag ? tag.copy() : entry.getValue());
        }
        return Map.copyOf(copy);
    }

    public int index(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) throw new IndexOutOfBoundsException("position outside schematic");
        return x + z * width + y * width * length;
    }

    public String blockStateAt(int x, int y, int z) {
        int paletteIndex = blockData[index(x, y, z)];
        if (paletteIndex < 0 || paletteIndex >= palette.size()) throw new IllegalArgumentException("block data references palette index " + paletteIndex + " outside palette size " + palette.size());
        return palette.get(paletteIndex);
    }

    public int volume() { return blockData.length; }
}
