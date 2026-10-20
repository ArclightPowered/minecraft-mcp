package io.izzel.minecraftmcp.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchematicBlockStateTest {
    @Test
    void exportedBlockStateIsAcceptedByPasteParser() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var method = ServerSchematicTools.class.getDeclaredMethod("serializeBlockState", net.minecraft.world.level.block.state.BlockState.class);
        method.setAccessible(true);
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.AIR, Blocks.CHEST, Blocks.OAK_LOG}) {
            var state = block.defaultBlockState();
            var serialized = (String) method.invoke(null, state);
            assertTrue(serialized.startsWith("minecraft:"));
            //? if >=1.21.2 {
            assertEquals(state, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, serialized, true).blockState());
            //?} else {
            /*assertEquals(state, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), serialized, true).blockState());
            *///?}
        }
    }
}
