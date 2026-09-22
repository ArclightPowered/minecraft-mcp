package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchematicEntityExportTest {
    @Test
    void skipsEntityEntryWhenSaveDeclinesSerialization() {
        AtomicBoolean serializerCalled = new AtomicBoolean();

        CompoundTag entry = ServerSchematicTools.exportIfSaved(false, () -> {
            serializerCalled.set(true);
            return new CompoundTag();
        });

        assertNull(entry);
        assertFalse(serializerCalled.get());
    }
}
