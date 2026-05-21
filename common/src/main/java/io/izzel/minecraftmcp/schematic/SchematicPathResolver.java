package io.izzel.minecraftmcp.schematic;

import java.nio.file.Path;

public final class SchematicPathResolver {
    private SchematicPathResolver() {}

    public static Path resolve(Path gameDirectory, String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("path is required");
        Path base = gameDirectory.toAbsolutePath().normalize().resolve("schematics").normalize();
        Path raw = Path.of(input);
        if (raw.isAbsolute()) throw new IllegalArgumentException("absolute schematic paths are not allowed");
        Path relative = raw;
        if (raw.getNameCount() > 0 && "schematics".equals(raw.getName(0).toString())) {
            relative = raw.getNameCount() == 1 ? Path.of("") : raw.subpath(1, raw.getNameCount());
        }
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) throw new IllegalArgumentException("schematic path must stay under " + base);
        return resolved;
    }
}
