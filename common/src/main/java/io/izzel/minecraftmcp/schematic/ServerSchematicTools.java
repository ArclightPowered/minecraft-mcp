package io.izzel.minecraftmcp.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
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
    private ServerSchematicTools() {}

    public static Map<String, Object> exportSchematic(ServerLevel level, Path gameDirectory, Map<String, Object> args) {
        try {
            Path path = SchematicPathResolver.resolve(gameDirectory, String.valueOf(args.getOrDefault("path", "")));
            Map<?, ?> from = (Map<?, ?>) args.get("from");
            Map<?, ?> to = (Map<?, ?>) args.get("to");
            if (from == null || to == null) throw new IllegalArgumentException("from and to are required");
            int x1 = coord(from, "x"), y1 = coord(from, "y"), z1 = coord(from, "z");
            int x2 = coord(to, "x"), y2 = coord(to, "y"), z2 = coord(to, "z");
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int length = maxZ - minZ + 1;
            int volume = width * height * length;
            int maxBlocks = ((Number) args.getOrDefault("maxBlocks", 32768)).intValue();
            if (volume > maxBlocks) throw new IllegalArgumentException("selection volume " + volume + " exceeds maxBlocks " + maxBlocks);
            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            List<String> palette = new ArrayList<>();
            int[] blockData = new int[volume];
            Map<String, Integer> biomePaletteIndex = new LinkedHashMap<>();
            List<String> biomePalette = new ArrayList<>();
            int[] biomeData = new int[volume];
            ListTag blockEntities = new ListTag();
            var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                        String state = level.getBlockState(pos).toString();
                        int stateIndex = paletteIndex.computeIfAbsent(state, key -> { palette.add(key); return palette.size() - 1; });
                        blockData[x + z * width + y * width * length] = stateIndex;
                        Holder<Biome> biome = level.getBiome(pos);
                        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElseGet(() -> biomeRegistry.getKey(biome.value()).toString());
                        int biomeIndex = biomePaletteIndex.computeIfAbsent(biomeId, key -> { biomePalette.add(key); return biomePalette.size() - 1; });
                        biomeData[x + z * width + y * width * length] = biomeIndex;
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) {
                            CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                            tag.putInt("x", x);
                            tag.putInt("y", y);
                            tag.putInt("z", z);
                            blockEntities.add(tag);
                        }
                    }
                }
            }
            ListTag entities = new ListTag();
            boolean includeEntities = Boolean.parseBoolean(String.valueOf(args.getOrDefault("entities", true)));
            if (includeEntities) {
                AABB box = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
                for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    CompoundTag tag = new CompoundTag();
                    if (entity.save(tag)) {
                        entities.add(tag);
                    }
                }
            }
            Map<String, Object> metadata = metadata(args.get("metadata"));
            Schematic schematic = new Schematic(width, height, length, new int[] {minX, minY, minZ}, palette, blockData, biomePalette, biomeData, metadata, blockEntities, entities);
            SpongeSchematicV3.write(path, schematic, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
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
            Path path = SchematicPathResolver.resolve(gameDirectory, String.valueOf(args.getOrDefault("path", "")));
            Schematic schematic = SpongeSchematicV3.read(path);
            int maxBlocks = ((Number) args.getOrDefault("maxBlocks", 32768)).intValue();
            if (schematic.volume() > maxBlocks) throw new IllegalArgumentException("schematic volume " + schematic.volume() + " exceeds maxBlocks " + maxBlocks);
            Map<?, ?> origin = (Map<?, ?>) args.get("origin");
            if (origin == null) throw new IllegalArgumentException("origin is required for server-side schematic paste");
            int originX = coord(origin, "x"), originY = coord(origin, "y"), originZ = coord(origin, "z");
            boolean ignoreAir = Boolean.parseBoolean(String.valueOf(args.getOrDefault("ignoreAir", false)));
            boolean pasteBlockEntities = Boolean.parseBoolean(String.valueOf(args.getOrDefault("blockEntities", true)));
            boolean pasteEntities = Boolean.parseBoolean(String.valueOf(args.getOrDefault("entities", true)));
            boolean pasteBiomes = Boolean.parseBoolean(String.valueOf(args.getOrDefault("biomes", true)));
            var blockLookup = level.holderLookup(Registries.BLOCK);
            int placed = 0, skippedAir = 0;
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
            if (pasteBlockEntities) {
                for (int i = 0; i < schematic.blockEntities().size(); i++) {
                    CompoundTag tag = schematic.blockEntities().getCompound(i).copy();
                    int x = tag.getInt("x"), y = tag.getInt("y"), z = tag.getInt("z");
                    BlockPos pos = new BlockPos(originX + x, originY + y, originZ + z);
                    tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
                    BlockEntity blockEntity = BlockEntity.loadStatic(pos, level.getBlockState(pos), tag, level.registryAccess());
                    if (blockEntity != null) {
                        level.setBlockEntity(blockEntity);
                        blockEntity.setChanged();
                        blockEntityCount++;
                    }
                }
            }
            int biomeCount = 0;
            if (pasteBiomes && !schematic.biomePalette().isEmpty() && schematic.biomeData().length == schematic.volume()) {
                var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
                for (int y = 0; y < schematic.height(); y++) {
                    for (int z = 0; z < schematic.length(); z++) {
                        for (int x = 0; x < schematic.width(); x++) {
                            String biomeId = schematic.biomePalette().get(schematic.biomeData()[schematic.index(x, y, z)]);
                            Holder<Biome> holder = biomeRegistry.getHolder(ResourceLocation.parse(biomeId)).orElse(null);
                            if (holder != null) {
                                setBiome(level, originX + x, originY + y, originZ + z, holder);
                                biomeCount++;
                            }
                        }
                    }
                }
            }
            int entityCount = 0;
            if (pasteEntities) {
                for (int i = 0; i < schematic.entities().size(); i++) {
                    CompoundTag tag = schematic.entities().getCompound(i).copy();
                    EntityType.create(tag, level).ifPresent(entity -> {
                        entity.moveTo(entity.getX() + originX, entity.getY() + originY, entity.getZ() + originZ, entity.getYRot(), entity.getXRot());
                        level.addFreshEntity(entity);
                    });
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
        section.fillBiomesFromNoise((qx, qy, qz, sampler) -> qx == targetQx && qy == targetQy && qz == targetQz ? biome : section.getNoiseBiome(qx - baseQx, qy - baseQy, qz - baseQz), level.getChunkSource().randomState().sampler(), baseQx, baseQy, baseQz);
        chunk.setUnsaved(true);
    }

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

    private static int coord(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("coordinate " + key + " is required");
        return number.intValue();
    }
}
