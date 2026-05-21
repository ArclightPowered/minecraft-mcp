package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.List;
import java.util.Map;

public record Schematic(
        int width,
        int height,
        int length,
        int[] offset,
        List<String> palette,
        int[] blockData,
        List<String> biomePalette,
        int[] biomeData,
        Map<String, Object> metadata,
        ListTag blockEntities,
        ListTag entities
) {
    public Schematic {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("schematic dimensions must be positive");
        }
        int expected = width * height * length;
        if (blockData.length != expected) {
            throw new IllegalArgumentException("block data length must equal width * height * length");
        }
        if (offset.length != 3) {
            throw new IllegalArgumentException("offset must contain exactly 3 integers");
        }
        palette = List.copyOf(palette);
        biomePalette = List.copyOf(biomePalette == null ? List.of() : biomePalette);
        if (biomeData == null) biomeData = new int[0];
        metadata = Map.copyOf(metadata);
        blockEntities = blockEntities == null ? new ListTag() : blockEntities.copy();
        entities = entities == null ? new ListTag() : entities.copy();
    }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    public String blockStateAt(int x, int y, int z) {
        return palette.get(blockData[index(x, y, z)]);
    }

    public int volume() {
        return width * height * length;
    }
}
