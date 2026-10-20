package io.izzel.minecraftmcp.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the client sees around the player, for tools that work on any server, modded or not. Client thread only. */
public final class ClientQueries {
    private ClientQueries() {}

    /** Entities within {@code radius} of the player, nearest first; {@code type} filters by id when not blank. */
    public static Map<String, Object> nearbyEntities(Minecraft mc, double radius, String type, int limit) {
        if (mc.player == null || mc.level == null) return Map.of("inWorld", false, "entities", List.of());
        List<Entity> found = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.distanceTo(mc.player) > radius) continue;
            if (type != null && !type.isBlank() && !EntityType.getKey(entity.getType()).toString().equals(type)) continue;
            found.add(entity);
        }
        found.sort(Comparator.comparingDouble(entity -> entity.distanceTo(mc.player)));
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Entity entity : found.subList(0, Math.min(found.size(), Math.max(0, limit)))) entities.add(entity(mc, entity));
        return Map.of("inWorld", true, "total", found.size(), "entities", entities);
    }

    /** What the crosshair points at: a block, an entity, or nothing. */
    public static Map<String, Object> target(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (mc.player == null || hit == null || hit.getType() == HitResult.Type.MISS) return Map.of("type", "miss");
        if (hit instanceof BlockHitResult block) {
            BlockPos pos = block.getBlockPos();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "block");
            result.put("x", pos.getX());
            result.put("y", pos.getY());
            result.put("z", pos.getZ());
            result.put("face", block.getDirection().getName());
            result.put("block", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "entity");
        result.putAll(entity(mc, ((EntityHitResult) hit).getEntity()));
        return result;
    }

    /** Positions of {@code block} (an id such as minecraft:diamond_ore) within a cube around the player, nearest first. */
    public static Map<String, Object> findBlocks(Minecraft mc, String block, int radius, int limit) {
        if (mc.player == null || mc.level == null) return Map.of("inWorld", false, "blocks", List.of());
        BlockPos center = mc.player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals(block)) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (BlockPos pos : found.subList(0, Math.min(found.size(), Math.max(0, limit)))) {
            blocks.add(Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "distance", Math.sqrt(pos.distSqr(center))));
        }
        return Map.of("inWorld", true, "block", block, "total", found.size(), "blocks", blocks);
    }

    private static Map<String, Object> entity(Minecraft mc, Entity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("uuid", entity.getUUID().toString());
        map.put("type", EntityType.getKey(entity.getType()).toString());
        map.put("name", entity.getName().getString());
        map.put("x", entity.getX());
        map.put("y", entity.getY());
        map.put("z", entity.getZ());
        map.put("distance", entity.distanceTo(mc.player));
        if (entity instanceof LivingEntity living) map.put("health", living.getHealth());
        return map;
    }
}
