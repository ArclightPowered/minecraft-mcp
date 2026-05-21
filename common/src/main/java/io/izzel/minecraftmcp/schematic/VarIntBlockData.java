package io.izzel.minecraftmcp.schematic;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class VarIntBlockData {
    private VarIntBlockData() {}

    public static byte[] encode(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : values) {
            if (value < 0) throw new IllegalArgumentException("varint values must be non-negative");
            int remaining = value;
            while ((remaining & ~0x7F) != 0) {
                out.write((remaining & 0x7F) | 0x80);
                remaining >>>= 7;
            }
            out.write(remaining);
        }
        return out.toByteArray();
    }

    public static int[] decode(byte[] bytes, int expectedCount) {
        List<Integer> values = new ArrayList<>(expectedCount < 0 ? 16 : expectedCount);
        int value = 0;
        int shift = 0;
        for (byte b : bytes) {
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                values.add(value);
                value = 0;
                shift = 0;
            } else {
                shift += 7;
                if (shift > 28) throw new IllegalArgumentException("varint is too large");
            }
        }
        if (shift != 0) throw new IllegalArgumentException("truncated varint block data");
        if (expectedCount >= 0 && values.size() != expectedCount) {
            throw new IllegalArgumentException("expected " + expectedCount + " block entries but found " + values.size());
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}
