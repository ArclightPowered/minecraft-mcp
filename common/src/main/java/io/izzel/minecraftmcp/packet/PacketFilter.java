package io.izzel.minecraftmcp.packet;

import java.util.Map;
import java.util.LinkedHashMap;

public record PacketFilter(
        PacketDirection direction,
        String classContains,
        String nameContains,
        long sinceSequence,
        int limit,
        boolean reverse,
        boolean clearAfterDump,
        Map<String, PacketNamedFilter> namedFilters
) {
    public static PacketFilter empty() {
        return new PacketFilter(null, "", "", 0, 100, false, false, Map.of());
    }

    public static PacketFilter from(Map<String, Object> args) {
        if (args == null) args = Map.of();
        PacketDirection direction = PacketDirection.parse(args.get("direction"));
        String classContains = String.valueOf(args.getOrDefault("classContains", ""));
        String nameContains = String.valueOf(args.getOrDefault("nameContains", args.getOrDefault("packetContains", "")));
        long sinceSequence = ((Number) args.getOrDefault("sinceSequence", 0)).longValue();
        int limit = ((Number) args.getOrDefault("limit", 100)).intValue();
        if (limit < 0) limit = 0;
        if (limit > 1000) limit = 1000;
        boolean reverse = Boolean.parseBoolean(String.valueOf(args.getOrDefault("reverse", false)));
        boolean clearAfterDump = Boolean.parseBoolean(String.valueOf(args.getOrDefault("clearAfterDump", false)));
        Map<String, PacketNamedFilter> namedFilters = parseNamedFilters(args.get("filters"));
        return new PacketFilter(direction, classContains, nameContains, sinceSequence, limit, reverse, clearAfterDump, namedFilters);
    }

    public boolean matches(RecordedPacket packet) {
        if (packet == null) return false;
        if (direction != null && packet.direction() != direction) return false;
        if (!classContains.isBlank() && !packet.packetClass().contains(classContains)) return false;
        if (!nameContains.isBlank() && !packet.packetSimpleName().contains(nameContains)) return false;
        return packet.sequence() > sinceSequence;
    }

    private static Map<String, PacketNamedFilter> parseNamedFilters(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, PacketNamedFilter> filters = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid packet filter name: " + name);
            filters.put(name, PacketNamedFilter.from(entry.getValue()));
        }
        return Map.copyOf(filters);
    }
}
