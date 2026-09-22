package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpongeSchematicV3 {
    private SpongeSchematicV3() {}

    public static void write(Path path, Schematic schematic, int dataVersion) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putInt("DataVersion", dataVersion);
        root.putShort("Width", checkedDimension(schematic.width(), "Width"));
        root.putShort("Height", checkedDimension(schematic.height(), "Height"));
        root.putShort("Length", checkedDimension(schematic.length(), "Length"));
        root.putIntArray("Offset", schematic.offset());
        root.put("BlockPalette", writePalette(schematic.palette()));
        root.putByteArray("BlockData", VarIntBlockData.encode(schematic.blockData()));
        if (!schematic.biomePalette().isEmpty() || schematic.biomeData().length != 0) {
            if (schematic.biomePalette().isEmpty() || schematic.biomeData().length != schematic.volume()) {
                throw new IllegalArgumentException("Sponge v3 biome palette and data must both describe the complete schematic volume");
            }
            validatePaletteIndexes(schematic.biomeData(), schematic.biomePalette().size(), "biome data");
            root.put("BiomePalette", writePalette(schematic.biomePalette()));
            root.putByteArray("BiomeData", VarIntBlockData.encode(schematic.biomeData()));
        }
        if (!schematic.blockEntities().isEmpty()) root.put("BlockEntities", schematic.blockEntities());
        if (!schematic.entities().isEmpty()) root.put("Entities", schematic.entities());
        if (!schematic.metadataNbt().isEmpty()) root.put("Metadata", schematic.metadataNbt());
        NbtIo.writeCompressed(root, path);
    }

    public static Schematic read(Path path) throws IOException {
        CompoundTag tag = readRoot(path);
        validateV3(tag);
        int width = Short.toUnsignedInt(tag.getShortOr("Width", (short) 0));
        int height = Short.toUnsignedInt(tag.getShortOr("Height", (short) 0));
        int length = Short.toUnsignedInt(tag.getShortOr("Length", (short) 0));
        int volume = volume(width, height, length);
        List<String> palette = readPalette(tag.getCompoundOrEmpty("BlockPalette"), "BlockPalette");
        int[] blockData = VarIntBlockData.decode(tag.getByteArray("BlockData").orElseThrow(() -> new IllegalArgumentException("Sponge v3 schematic is missing BlockData")), volume);
        validatePaletteIndexes(blockData, palette.size(), "block data");

        List<String> biomePalette = List.of();
        int[] biomeData = new int[0];
        boolean hasBiomePalette = tag.contains("BiomePalette");
        boolean hasBiomeData = tag.contains("BiomeData");
        if (hasBiomePalette != hasBiomeData) throw new IllegalArgumentException("Sponge v3 schematic must contain both BiomePalette and BiomeData");
        if (hasBiomePalette) {
            biomePalette = readPalette(tag.getCompoundOrEmpty("BiomePalette"), "BiomePalette");
            biomeData = VarIntBlockData.decode(tag.getByteArray("BiomeData").orElseThrow(), volume);
            validatePaletteIndexes(biomeData, biomePalette.size(), "biome data");
        }
        int[] offset = tag.getIntArray("Offset").orElse(new int[] {0, 0, 0});
        if (offset.length != 3) throw new IllegalArgumentException("Sponge v3 Offset must contain exactly 3 integers");
        ListTag blockEntities = tag.getList("BlockEntities").orElseGet(ListTag::new).copy();
        ListTag entities = tag.getList("Entities").orElseGet(ListTag::new).copy();
        CompoundTag metadataNbt = tag.getCompound("Metadata").orElseGet(CompoundTag::new).copy();
        return new Schematic(width, height, length, offset, palette, blockData, biomePalette, biomeData,
                metadata(metadataNbt), metadataNbt, blockEntities, entities);
    }

    public static SchematicInfo info(Path path) throws IOException {
        CompoundTag tag = readRoot(path);
        validateV3(tag);
        int width = Short.toUnsignedInt(tag.getShortOr("Width", (short) 0));
        int height = Short.toUnsignedInt(tag.getShortOr("Height", (short) 0));
        int length = Short.toUnsignedInt(tag.getShortOr("Length", (short) 0));
        return new SchematicInfo("ok", path.toAbsolutePath().normalize().toString(), "sponge-v3",
                tag.getIntOr("Version", 0), tag.getIntOr("DataVersion", 0), width, height, length,
                volume(width, height, length), tag.getCompoundOrEmpty("BlockPalette").size(),
                tag.contains("BlockEntities"), tag.contains("Entities"),
                tag.contains("BiomePalette") && tag.contains("BiomeData"), metadata(tag.getCompoundOrEmpty("Metadata")));
    }

    public static void forceVersionForTest(Path path, int version) throws IOException {
        CompoundTag tag = readRoot(path);
        tag.putInt("Version", version);
        NbtIo.writeCompressed(tag, path);
    }

    static CompoundTag metadataToTag(Map<String, Object> metadata) {
        CompoundTag tag = new CompoundTag();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() != null) tag.putString(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return tag;
    }

    private static CompoundTag readRoot(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    private static void validateV3(CompoundTag tag) {
        if (tag.getIntOr("Version", -1) != 3) throw new IllegalArgumentException("only Sponge schematic v3 is supported");
        if (tag.getCompound("BlockPalette").isEmpty()) throw new IllegalArgumentException("Sponge v3 schematic is missing BlockPalette");
        if (tag.getByteArray("BlockData").isEmpty()) throw new IllegalArgumentException("Sponge v3 schematic is missing BlockData");
        if (tag.getShort("Width").isEmpty() || tag.getShort("Height").isEmpty() || tag.getShort("Length").isEmpty()) {
            throw new IllegalArgumentException("Sponge v3 schematic is missing dimensions");
        }
        volume(Short.toUnsignedInt(tag.getShortOr("Width", (short) 0)), Short.toUnsignedInt(tag.getShortOr("Height", (short) 0)), Short.toUnsignedInt(tag.getShortOr("Length", (short) 0)));
    }

    private static List<String> readPalette(CompoundTag paletteTag, String field) {
        if (paletteTag.isEmpty()) throw new IllegalArgumentException("Sponge v3 schematic has an empty " + field);
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String key : paletteTag.keySet()) entries.add(Map.entry(key, paletteTag.getIntOr(key, -1)));
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getValue() != i) throw new IllegalArgumentException(field + " must use contiguous indexes starting at zero");
        }
        return entries.stream().map(Map.Entry::getKey).toList();
    }

    private static CompoundTag writePalette(List<String> palette) {
        if (palette.isEmpty()) throw new IllegalArgumentException("Sponge v3 palette must not be empty");
        CompoundTag result = new CompoundTag();
        for (int i = 0; i < palette.size(); i++) result.putInt(palette.get(i), i);
        return result;
    }

    private static short checkedDimension(int dimension, String name) {
        int maximum = 0xFFFF;
        if (dimension <= 0 || dimension > maximum) throw new IllegalArgumentException("Sponge v3 " + name + " must be between 1 and " + maximum);
        return (short) dimension;
    }

    private static int volume(int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0) throw new IllegalArgumentException("Sponge v3 dimensions must be positive");
        try {
            return Math.multiplyExact(Math.multiplyExact(width, height), length);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Sponge v3 dimensions overflow", e);
        }
    }

    private static void validatePaletteIndexes(int[] values, int paletteSize, String field) {
        for (int value : values) {
            if (value < 0 || value >= paletteSize) throw new IllegalArgumentException(field + " references palette index " + value + " outside palette size " + paletteSize);
        }
    }

    private static Map<String, Object> metadata(CompoundTag metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Tag> entry : metadata.entrySet()) result.put(entry.getKey(), entry.getValue().copy());
        return result;
    }
}
