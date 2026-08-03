package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

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
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putInt("DataVersion", dataVersion);
        root.putShort("Width", (short) schematic.width());
        root.putShort("Height", (short) schematic.height());
        root.putShort("Length", (short) schematic.length());
        root.putIntArray("Offset", schematic.offset());

        CompoundTag palette = new CompoundTag();
        for (int i = 0; i < schematic.palette().size(); i++) {
            palette.putInt(schematic.palette().get(i), i);
        }
        root.put("BlockPalette", palette);
        root.putByteArray("BlockData", VarIntBlockData.encode(schematic.blockData()));
        if (!schematic.biomePalette().isEmpty() && schematic.biomeData().length > 0) {
            CompoundTag biomePalette = new CompoundTag();
            for (int i = 0; i < schematic.biomePalette().size(); i++) biomePalette.putInt(schematic.biomePalette().get(i), i);
            root.put("BiomePalette", biomePalette);
            root.putByteArray("BiomeData", VarIntBlockData.encode(schematic.biomeData()));
        }
        if (!schematic.blockEntities().isEmpty()) root.put("BlockEntities", schematic.blockEntities().copy());
        if (!schematic.entities().isEmpty()) root.put("Entities", schematic.entities().copy());

        CompoundTag metadata = new CompoundTag();
        for (Map.Entry<String, Object> entry : schematic.metadata().entrySet()) {
            if (entry.getValue() != null) metadata.putString(entry.getKey(), String.valueOf(entry.getValue()));
        }
        root.put("Metadata", metadata);
        NbtIo.writeCompressed(root, path);
    }

    public static Schematic read(Path path) throws IOException {
        CompoundTag tag = readRoot(path);
        validateV3(tag);
        int width = tag.getShortOr("Width", (short) 0);
        int height = tag.getShortOr("Height", (short) 0);
        int length = tag.getShortOr("Length", (short) 0);
        int volume = width * height * length;
        CompoundTag paletteTag = tag.getCompoundOrEmpty("BlockPalette");
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String key : paletteTag.keySet()) {
            entries.add(Map.entry(key, paletteTag.getIntOr(key, 0)));
        }
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        List<String> palette = entries.stream().map(Map.Entry::getKey).toList();
        int[] blockData = VarIntBlockData.decode(tag.getByteArray("BlockData").orElse(new byte[0]), volume);
        List<String> biomePalette = List.of();
        int[] biomeData = new int[0];
        if (tag.getCompound("BiomePalette").isPresent() && tag.getByteArray("BiomeData").isPresent()) {
            List<Map.Entry<String, Integer>> biomeEntries = new ArrayList<>();
            CompoundTag biomePaletteTag = tag.getCompoundOrEmpty("BiomePalette");
            for (String key : biomePaletteTag.keySet()) biomeEntries.add(Map.entry(key, biomePaletteTag.getIntOr(key, 0)));
            biomeEntries.sort(Comparator.comparingInt(Map.Entry::getValue));
            biomePalette = biomeEntries.stream().map(Map.Entry::getKey).toList();
            byte[] biomeBytes = tag.getByteArray("BiomeData").orElse(new byte[0]);
            biomeData = VarIntBlockData.decode(biomeBytes, biomeBytes.length == 0 ? 0 : -1);
        }
        int[] offset = tag.getIntArray("Offset").orElse(new int[] {0, 0, 0});
        ListTag blockEntities = tag.getList("BlockEntities").map(ListTag::copy).orElseGet(ListTag::new);
        ListTag entities = tag.getList("Entities").map(ListTag::copy).orElseGet(ListTag::new);
        return new Schematic(width, height, length, offset, palette, blockData, biomePalette, biomeData, metadata(tag), blockEntities, entities);
    }

    public static SchematicInfo info(Path path) throws IOException {
        CompoundTag tag = readRoot(path);
        validateV3(tag);
        int width = tag.getShortOr("Width", (short) 0);
        int height = tag.getShortOr("Height", (short) 0);
        int length = tag.getShortOr("Length", (short) 0);
        return new SchematicInfo(
                "ok",
                path.toAbsolutePath().normalize().toString(),
                "sponge-v3",
                tag.getIntOr("Version", 0),
                tag.getIntOr("DataVersion", 0),
                width,
                height,
                length,
                width * height * length,
                tag.getCompoundOrEmpty("BlockPalette").size(),
                tag.contains("BlockEntities"),
                tag.contains("Entities"),
                tag.contains("BiomePalette") && tag.contains("BiomeData"),
                metadata(tag)
        );
    }

    public static void forceVersionForTest(Path path, int version) throws IOException {
        CompoundTag tag = readRoot(path);
        tag.putInt("Version", version);
        NbtIo.writeCompressed(tag, path);
    }

    private static CompoundTag readRoot(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    private static void validateV3(CompoundTag tag) {
        if (tag.getIntOr("Version", 0) != 3) throw new IllegalArgumentException("only Sponge schematic v3 is supported");
        if (tag.getCompound("BlockPalette").isEmpty()) throw new IllegalArgumentException("Sponge v3 schematic is missing BlockPalette");
        if (tag.getByteArray("BlockData").isEmpty()) throw new IllegalArgumentException("Sponge v3 schematic is missing BlockData");
        if (tag.getShort("Width").isEmpty() || tag.getShort("Height").isEmpty() || tag.getShort("Length").isEmpty()) {
            throw new IllegalArgumentException("Sponge v3 schematic is missing dimensions");
        }
    }

    private static Map<String, Object> metadata(CompoundTag tag) {
        CompoundTag metadata = tag.getCompound("Metadata").orElse(null);
        if (metadata == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : metadata.keySet()) {
            result.put(key, metadata.getStringOr(key, ""));
        }
        return result;
    }
}
