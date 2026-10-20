package io.izzel.minecraftmcp.compat;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;
import java.util.Optional;
import java.util.Set;

//? if <1.21.5
/*import net.minecraft.nbt.Tag;*/
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
//?} else
/*import net.minecraft.resources.ResourceLocation;*/
//? if >=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
//? if >=26.2
/*import net.minecraft.world.entity.EntitySpawnRequest;*/
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

/**
 * The Minecraft APIs whose shape changed between the supported versions, behind one signature each.
 * Sources are written for the version Stonecutter is set to; the {@code //?} blocks switch the bodies
 * for the others. Renames that keep their meaning are in {@code gradle/replacements.gradle} instead.
 */
public final class MinecraftCompat {
    private MinecraftCompat() {}

    // --- names and ids (1.21.11 renamed ResourceLocation to Identifier)

    /** "namespace:path" of a registry key. */
    public static String id(ResourceKey<?> key) {
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();
        *///?}
    }

    /** A plugin-channel payload type for a "namespace:path" channel id. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String channel) {
        String[] parts = channel.split(":", 2);
        //? if >=1.21.11 {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(parts[0], parts[1]));
        //?} else {
        /*return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]));
        *///?}
    }

    public static String profileName(Player player) {
        //? if >=1.21.9 {
        return player.getGameProfile().name();
        //?} else {
        /*return player.getGameProfile().getName();
        *///?}
    }

    public static String gameVersion() {
        //? if >=1.21.6 {
        return SharedConstants.getCurrentVersion().name();
        //?} else {
        /*return SharedConstants.getCurrentVersion().getName();
        *///?}
    }

    public static int dataVersion() {
        //? if >=1.21.6 {
        return SharedConstants.getCurrentVersion().dataVersion().version();
        //?} else {
        /*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        *///?}
    }

    /** Whether the player holds owner-level permissions (the old permission level 4). */
    public static boolean isOwner(ServerPlayer player) {
        //? if >=1.21.11 {
        return player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        //?} else {
        /*return player.hasPermissions(4);
        *///?}
    }

    // --- world

    public static long chunkKey(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }

    public static Registry<Biome> biomes(ServerLevel level) {
        //? if >=1.21.2 {
        return level.registryAccess().lookupOrThrow(Registries.BIOME);
        //?} else {
        /*return level.registryAccess().registryOrThrow(Registries.BIOME);
        *///?}
    }

    /** A biome by "namespace:path"; empty when the registry has no such biome. */
    public static Optional<? extends Holder<Biome>> biome(Registry<Biome> biomes, String id) {
        //? if >=1.21.11 {
        return biomes.get(Identifier.parse(id));
        //?} else if >=1.21.2 {
        /*return biomes.get(ResourceLocation.parse(id));
        *///?} else {
        /*return biomes.getHolder(ResourceLocation.parse(id));
        *///?}
    }

    public static void markUnsaved(ChunkAccess chunk) {
        //? if >=1.21.2 {
        chunk.markUnsaved();
        //?} else {
        /*chunk.setUnsaved(true);
        *///?}
    }

    // --- player inventory (1.21.5 hid the fields behind accessors and split out equipment)

    public static List<ItemStack> inventoryItems(Inventory inventory) {
        //? if >=1.21.5 {
        return inventory.getNonEquipmentItems();
        //?} else {
        /*return inventory.items;
        *///?}
    }

    public static int selectedSlot(Inventory inventory) {
        //? if >=1.21.5 {
        return inventory.getSelectedSlot();
        //?} else {
        /*return inventory.selected;
        *///?}
    }

    public static void selectSlot(Inventory inventory, int slot) {
        //? if >=1.21.5 {
        inventory.setSelectedSlot(slot);
        //?} else {
        /*inventory.selected = slot;
        *///?}
    }

    public static ItemStack selectedItem(Inventory inventory) {
        //? if >=1.21.5 {
        return inventory.getSelectedItem();
        //?} else {
        /*return inventory.getSelected();
        *///?}
    }

    public static GameType gameMode(ServerPlayer player) {
        //? if >=1.21.5 {
        return player.gameMode();
        //?} else {
        /*return player.gameMode.getGameModeForPlayer();
        *///?}
    }

    // --- entity (de)serialisation

    /** The entity's saved data, or null when the entity does not save (a passenger, a player). */
    public static CompoundTag saveEntity(Entity entity, ServerLevel level) {
        //? if >=1.21.6 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        return entity.save(output) ? output.buildResult() : null;
        //?} else {
        /*CompoundTag data = new CompoundTag();
        return entity.save(data) ? data : null;
        *///?}
    }

    public static Optional<Entity> loadEntity(CompoundTag data, ServerLevel level) {
        //? if >=26.2 {
        /*return EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data), level,
                new EntitySpawnRequest(EntitySpawnReason.LOAD, false));
        *///?} else if >=1.21.6 {
        return EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data), level, EntitySpawnReason.LOAD);
        //?} else if >=1.21.2 {
        /*return EntityType.create(data, level, EntitySpawnReason.LOAD);
        *///?} else {
        /*return EntityType.create(data, level);
        *///?}
    }

    // --- NBT reads. 1.21.5 made keyed getters return Optional; these give every version that shape.

    public static Optional<CompoundTag> compound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getCompound(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(tag.getCompound(key)) : Optional.empty();
        *///?}
    }

    public static Optional<CompoundTag> compound(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getCompound(index);
        //?} else {
        /*return list.get(index) instanceof CompoundTag compound ? Optional.of(compound) : Optional.empty();
        *///?}
    }

    /** A list of compounds or doubles; older versions need the element type to read it. */
    public static Optional<ListTag> list(CompoundTag tag, String key, int elementType) {
        //? if >=1.21.5 {
        return tag.getList(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_LIST) ? Optional.of(tag.getList(key, elementType)) : Optional.empty();
        *///?}
    }

    public static double doubleAt(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getDoubleOr(index, 0.0D);
        //?} else {
        /*return list.getDouble(index);
        *///?}
    }

    public static Optional<String> string(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getString(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
        *///?}
    }

    public static Optional<int[]> intArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntArray(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_INT_ARRAY) ? Optional.of(tag.getIntArray(key)) : Optional.empty();
        *///?}
    }

    public static Optional<byte[]> byteArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getByteArray(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_BYTE_ARRAY) ? Optional.of(tag.getByteArray(key)) : Optional.empty();
        *///?}
    }

    public static Optional<Short> shortValue(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getShort(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getShort(key)) : Optional.empty();
        *///?}
    }

    public static Optional<Integer> intValue(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getInt(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getInt(key)) : Optional.empty();
        *///?}
    }

    public static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.keySet();
        //?} else {
        /*return tag.getAllKeys();
        *///?}
    }
}
