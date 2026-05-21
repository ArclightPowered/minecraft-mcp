package io.izzel.minecraftmcp.schematic;

import java.util.Map;

public record SchematicInfo(
        String status,
        String path,
        String format,
        int version,
        int dataVersion,
        int width,
        int height,
        int length,
        int volume,
        int paletteSize,
        boolean hasBlockEntities,
        boolean hasEntities,
        boolean hasBiomes,
        Map<String, Object> metadata
) {}
