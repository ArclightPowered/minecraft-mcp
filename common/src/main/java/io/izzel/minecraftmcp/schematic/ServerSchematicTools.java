package io.izzel.minecraftmcp.schematic;

import io.izzel.minecraftmcp.compat.MinecraftCompat;
import io.izzel.minecraftmcp.mcp.Param;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerSchematicTools {
    public static final Param<String> PATH = Param.string("path", "file under schematics/, .schem").required();
    private static final Param<Map<String, Object>> FROM = Param.object("from", "corner {x,y,z}").required();
    private static final Param<Map<String, Object>> TO = Param.object("to", "opposite corner {x,y,z}").required();
    private static final Param<Map<String, Object>> ORIGIN = Param.object("origin", "where the schematic's first corner goes {x,y,z}").required();
    private static final Param<Integer> MAX_BLOCKS = Param.integer("maxBlocks", "refuse regions larger than this").or(32768);
    private static final Param<Boolean> ENTITIES = Param.bool("entities", "include entities").or(true);
    private static final Param<Boolean> BLOCK_ENTITIES = Param.bool("blockEntities", "restore block entities (chests, signs ...)").or(true);
    private static final Param<Boolean> BIOMES = Param.bool("biomes", "restore biomes").or(true);
    private static final Param<Boolean> IGNORE_AIR = Param.bool("ignoreAir", "keep existing blocks where the schematic has air").or(false);
    private static final Param<Map<String, Object>> METADATA = Param.object("metadata", "extra metadata stored in the file");
    public static final List<Param<?>> EXPORT_PARAMS = List.of(PATH, FROM, TO, ENTITIES, METADATA, MAX_BLOCKS);
    public static final List<Param<?>> PASTE_PARAMS = List.of(PATH, ORIGIN, IGNORE_AIR, BLOCK_ENTITIES, ENTITIES, BIOMES, MAX_BLOCKS);

    private ServerSchematicTools() {}

    static String serializeBlockState(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    public static Map<String, Object> exportSchematic(ServerLevel level, Path gameDirectory, Map<String, Object> args) {
        try {
            Path path = SchematicPathResolver.resolve(gameDirectory, PATH.get(args));
            Map<?, ?> from = FROM.get(args);
            Map<?, ?> to = TO.get(args);
            int x1 = coord(from, "x"), y1 = coord(from, "y"), z1 = coord(from, "z");
            int x2 = coord(to, "x"), y2 = coord(to, "y"), z2 = coord(to, "z");
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            int width = Math.max(x1, x2) - minX + 1;
            int height = Math.max(y1, y2) - minY + 1;
            int length = Math.max(z1, z2) - minZ + 1;
            int volume = volume(width, height, length);
            int maxBlocks = maxBlocks(args);
            if (volume > maxBlocks) throw new IllegalArgumentException("selection volume " + volume + " exceeds maxBlocks " + maxBlocks);

            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            List<String> palette = new ArrayList<>();
            int[] blockData = new int[volume];
            Map<String, Integer> biomePaletteIndex = new LinkedHashMap<>();
            List<String> biomePalette = new ArrayList<>();
            int[] biomeData = new int[volume];
            ListTag blockEntities = new ListTag();
            Registry<Biome> biomeRegistry = MinecraftCompat.biomes(level);
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                        int index = index(width, length, x, y, z);
                        String state = serializeBlockState(level.getBlockState(pos));
                        blockData[index] = paletteIndex.computeIfAbsent(state, key -> { palette.add(key); return palette.size() - 1; });
                        Holder<Biome> biome = level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
                        String biomeId = biome.unwrapKey().map(MinecraftCompat::id)
                                .orElseGet(() -> biomeRegistry.getKey(biome.value()).toString());
                        biomeData[index] = biomePaletteIndex.computeIfAbsent(biomeId, key -> { biomePalette.add(key); return biomePalette.size() - 1; });
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) blockEntities.add(exportBlockEntity(blockEntity, level, x, y, z));
                    }
                }
            }
            ListTag entities = new ListTag();
            if (ENTITIES.get(args)) {
                AABB box = new AABB(minX, minY, minZ, minX + width, minY + height, minZ + length);
                for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    CompoundTag exported = exportEntity(entity, level, minX, minY, minZ);
                    if (exported != null) entities.add(exported);
                }
            }
            Schematic schematic = new Schematic(width, height, length, new int[] {minX, minY, minZ}, palette, blockData,
                    biomePalette, biomeData, metadata(METADATA.get(args)), blockEntities, entities);
            SpongeSchematicV3.write(path, schematic, MinecraftCompat.dataVersion());
            Map<String, Object> result = baseResult("exported", path, schematic);
            result.put("blockEntities", blockEntities.size());
            result.put("entities", entities.size());
            result.put("biomes", biomePalette.size());
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to export Sponge v3 schematic: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> pasteSchematic(ServerLevel level, Path gameDirectory, Map<String, Object> args) {
        try {
            Path path = SchematicPathResolver.resolve(gameDirectory, PATH.get(args));
            Schematic schematic = SpongeSchematicV3.read(path);
            int maxBlocks = maxBlocks(args);
            if (schematic.volume() > maxBlocks) throw new IllegalArgumentException("schematic volume " + schematic.volume() + " exceeds maxBlocks " + maxBlocks);
            Map<?, ?> origin = ORIGIN.get(args);
            int originX = coord(origin, "x"), originY = coord(origin, "y"), originZ = coord(origin, "z");
            boolean ignoreAir = IGNORE_AIR.get(args);
            int placed = 0, skippedAir = 0;
            var blockLookup = level.holderLookup(Registries.BLOCK);
            for (int y = 0; y < schematic.height(); y++) {
                for (int z = 0; z < schematic.length(); z++) {
                    for (int x = 0; x < schematic.width(); x++) {
                        String serialized = schematic.blockStateAt(x, y, z);
                        if (ignoreAir && "minecraft:air".equals(serialized)) { skippedAir++; continue; }
                        BlockState state = BlockStateParser.parseForBlock(blockLookup, serialized, true).blockState();
                        level.setBlock(new BlockPos(originX + x, originY + y, originZ + z), state, 3);
                        placed++;
                    }
                }
            }
            int blockEntityCount = 0;
            if (BLOCK_ENTITIES.get(args)) {
                ListTag blockEntities = schematic.blockEntities();
                for (int i = 0; i < blockEntities.size(); i++) {
                    CompoundTag tag = MinecraftCompat.compound(blockEntities, i).orElse(null);
                    if (tag == null) throw new IllegalArgumentException("BlockEntities entry " + i + " must be a compound");
                    BlockEntity blockEntity = importBlockEntity(tag, level, originX, originY, originZ);
                    if (blockEntity != null) {
                        level.setBlockEntity(blockEntity);
                        blockEntity.setChanged();
                        blockEntityCount++;
                    }
                }
            }
            int biomeCount = 0;
            if (BIOMES.get(args) && !schematic.biomePalette().isEmpty()) {
                Registry<Biome> biomeRegistry = MinecraftCompat.biomes(level);
                Map<Long, BiomePlacement> biomePlacements = new LinkedHashMap<>();
                int[] biomeData = schematic.biomeData();
                for (int y = 0; y < schematic.height(); y++) {
                    for (int z = 0; z < schematic.length(); z++) {
                        for (int x = 0; x < schematic.width(); x++) {
                            String biomeId = schematic.biomePalette().get(biomeData[schematic.index(x, y, z)]);
                            Holder<Biome> holder = MinecraftCompat.biome(biomeRegistry, biomeId).orElseThrow(() -> new IllegalArgumentException("unknown biome " + biomeId));
                            int worldX = originX + x, worldY = originY + y, worldZ = originZ + z;
                            long quartKey = BlockPos.asLong(QuartPos.fromBlock(worldX), QuartPos.fromBlock(worldY), QuartPos.fromBlock(worldZ));
                            BiomePlacement existing = biomePlacements.putIfAbsent(quartKey, new BiomePlacement(worldX, worldY, worldZ, biomeId, holder));
                            if (existing != null && !existing.id().equals(biomeId)) {
                                throw new IllegalArgumentException("Sponge v3 biome data assigns multiple biomes to Minecraft quart cell at " + worldX + "," + worldY + "," + worldZ + "; refusing to silently discard biome data");
                            }
                        }
                    }
                }
                for (BiomePlacement placement : biomePlacements.values()) {
                    setBiome(level, placement.x(), placement.y(), placement.z(), placement.holder());
                    biomeCount++;
                }
            }
            int entityCount = 0;
            if (ENTITIES.get(args)) {
                ListTag entities = schematic.entities();
                for (int i = 0; i < entities.size(); i++) {
                    CompoundTag tag = MinecraftCompat.compound(entities, i).orElse(null);
                    if (tag == null) throw new IllegalArgumentException("Entities entry " + i + " must be a compound");
                    Entity entity = importEntity(tag, level, originX, originY, originZ);
                    if (entity == null || !level.addFreshEntity(entity)) {
                        throw new IllegalStateException("Failed to add schematic entity " + i);
                    }
                    entityCount++;
                }
            }
            Map<String, Object> result = baseResult("pasted", path, schematic);
            result.put("origin", Map.of("x", originX, "y", originY, "z", originZ));
            result.put("placedBlocks", placed);
            result.put("skippedAir", skippedAir);
            result.put("blockEntities", blockEntityCount);
            result.put("entities", entityCount);
            result.put("biomes", biomeCount);
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to paste Sponge v3 schematic: " + e.getMessage(), e);
        }
    }

    private static CompoundTag exportBlockEntity(BlockEntity blockEntity, ServerLevel level, int x, int y, int z) {
        CompoundTag saved = blockEntity.saveWithFullMetadata(level.registryAccess());
        CompoundTag entry = new CompoundTag();
        entry.putIntArray("Pos", new int[] {x, y, z});
        entry.putString("Id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
        entry.put("Data", saved);
        return entry;
    }

    private static BlockEntity importBlockEntity(CompoundTag entry, ServerLevel level, int originX, int originY, int originZ) {
        int[] relative = MinecraftCompat.intArray(entry, "Pos").orElseThrow(() -> new IllegalArgumentException("BlockEntity is missing Pos"));
        if (relative.length != 3) throw new IllegalArgumentException("BlockEntity Pos must contain exactly 3 integers");
        String id = MinecraftCompat.string(entry, "Id").orElseThrow(() -> new IllegalArgumentException("BlockEntity is missing Id"));
        BlockPos pos = new BlockPos(originX + relative[0], originY + relative[1], originZ + relative[2]);
        CompoundTag saved = MinecraftCompat.compound(entry, "Data").orElseGet(CompoundTag::new).copy();
        saved.putString("id", id);
        saved.putInt("x", pos.getX());
        saved.putInt("y", pos.getY());
        saved.putInt("z", pos.getZ());
        return BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
    }

    private static CompoundTag exportEntity(Entity entity, ServerLevel level, int minX, int minY, int minZ) {
        CompoundTag data = MinecraftCompat.saveEntity(entity, level);
        return exportIfSaved(data != null, () -> {
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(entity.getX() - minX));
            pos.add(DoubleTag.valueOf(entity.getY() - minY));
            pos.add(DoubleTag.valueOf(entity.getZ() - minZ));
            entry.put("Pos", pos);
            entry.putString("Id", EntityType.getKey(entity.getType()).toString());
            entry.put("Data", data);
            return entry;
        });
    }

    static CompoundTag exportIfSaved(boolean saved, java.util.function.Supplier<CompoundTag> serializer) {
        return saved ? serializer.get() : null;
    }

    static CompoundTag copyForEntityPlacement(CompoundTag source) {
        CompoundTag copy = source.copy();
        copy.remove("UUID");
        copy.remove("UUIDMost");
        copy.remove("UUIDLeast");
        MinecraftCompat.list(copy, "Passengers", Tag.TAG_COMPOUND).ifPresent(passengers -> {
            for (int i = 0; i < passengers.size(); i++) {
                CompoundTag passenger = MinecraftCompat.compound(passengers, i).orElseThrow();
                passengers.set(i, copyForEntityPlacement(passenger));
            }
        });
        return copy;
    }

    private static Entity importEntity(CompoundTag entry, ServerLevel level, int originX, int originY, int originZ) {
        ListTag relative = MinecraftCompat.list(entry, "Pos", Tag.TAG_DOUBLE).orElseThrow(() -> new IllegalArgumentException("Entity is missing Pos"));
        if (relative.size() != 3) throw new IllegalArgumentException("Entity Pos must contain exactly 3 doubles");
        String id = MinecraftCompat.string(entry, "Id").orElseThrow(() -> new IllegalArgumentException("Entity is missing Id"));
        CompoundTag saved = copyForEntityPlacement(MinecraftCompat.compound(entry, "Data").orElseGet(CompoundTag::new));
        saved.putString("id", id);
        ListTag absolute = new ListTag();
        absolute.add(DoubleTag.valueOf(MinecraftCompat.doubleAt(relative, 0) + originX));
        absolute.add(DoubleTag.valueOf(MinecraftCompat.doubleAt(relative, 1) + originY));
        absolute.add(DoubleTag.valueOf(MinecraftCompat.doubleAt(relative, 2) + originZ));
        saved.put("Pos", absolute);
        return MinecraftCompat.loadEntity(saved, level).orElse(null);
    }

    private static void setBiome(ServerLevel level, int x, int y, int z, Holder<Biome> biome) {
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        var chunk = level.getChunk(sectionX, sectionZ);
        int sectionIndex = level.getSectionIndex(y);
        var section = chunk.getSection(sectionIndex);
        int targetQx = QuartPos.fromBlock(x);
        int targetQy = QuartPos.fromBlock(y);
        int targetQz = QuartPos.fromBlock(z);
        int baseQx = QuartPos.fromSection(sectionX);
        int baseQy = QuartPos.fromBlock(level.getSectionYFromSectionIndex(sectionIndex) << 4);
        int baseQz = QuartPos.fromSection(sectionZ);
        //? if >=26.3 {
        /*section.fillBiomesFromNoise((qx, qy, qz) -> qx == targetQx && qy == targetQy && qz == targetQz
                ? biome : section.getNoiseBiome(qx - baseQx, qy - baseQy, qz - baseQz), baseQx, baseQy, baseQz);
        *///?} else {
        section.fillBiomesFromNoise((qx, qy, qz, sampler) -> qx == targetQx && qy == targetQy && qz == targetQz
                ? biome : section.getNoiseBiome(qx - baseQx, qy - baseQy, qz - baseQz),
                level.getChunkSource().randomState().sampler(), baseQx, baseQy, baseQz);
        //?}
        MinecraftCompat.markUnsaved(chunk);
    }

    private record BiomePlacement(int x, int y, int z, String id, Holder<Biome> holder) {}

    private static Map<String, Object> baseResult(String status, Path path, Schematic schematic) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("path", path.toAbsolutePath().normalize().toString());
        result.put("format", "sponge-v3");
        result.put("width", schematic.width());
        result.put("height", schematic.height());
        result.put("length", schematic.length());
        result.put("volume", schematic.volume());
        result.put("paletteSize", schematic.palette().size());
        return result;
    }

    private static Map<String, Object> metadata(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }



    private static int maxBlocks(Map<String, Object> args) {
        int maxBlocks = MAX_BLOCKS.get(args);
        if (maxBlocks <= 0) throw new IllegalArgumentException("maxBlocks must be positive");
        return maxBlocks;
    }

    private static int coord(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("coordinate " + key + " is required");
        return number.intValue();
    }

    private static int volume(int width, int height, int length) {
        try { return Math.multiplyExact(Math.multiplyExact(width, height), length); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("selection volume overflows", e); }
    }

    private static int index(int width, int length, int x, int y, int z) { return x + z * width + y * width * length; }
}
