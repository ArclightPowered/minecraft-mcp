package io.izzel.minecraftmcp.schematic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VarIntBlockDataTest {
    @Test
    void roundTripsPaletteIndexesUsingSpongeVarInts() {
        int[] values = {0, 1, 2, 127, 128, 255, 16384};

        byte[] encoded = VarIntBlockData.encode(values);
        int[] decoded = VarIntBlockData.decode(encoded, values.length);

        assertArrayEquals(values, decoded);
    }
}
