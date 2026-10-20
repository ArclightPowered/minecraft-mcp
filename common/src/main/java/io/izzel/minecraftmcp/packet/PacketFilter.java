package io.izzel.minecraftmcp.packet;

import io.izzel.minecraftmcp.mcp.Param;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PacketFilter(
        PacketDirection direction,
        String classContains,
        String nameContains,
        long sinceSequence,
        int limit,
        boolean reverse,
        boolean clearAfterDump,
        boolean parseBundlePackets,
        Map<String, PacketNamedFilter> namedFilters
) {
    public static PacketFilter empty() {
        return new PacketFilter(null, "", "", 0, 100, false, false, true, Map.of());
    }

    public static final Param<String> DIRECTION = Param.string("direction", "serverbound or clientbound (default both)");
    public static final Param<String> CLASS_CONTAINS = Param.string("classContains", "packet class name contains").or("");
    public static final Param<String> NAME_CONTAINS = Param.string("nameContains",
            "packet name contains (Fabric reports intermediary names such as class_2879)").or("").alias("packetContains");
    public static final Param<Long> SINCE_SEQUENCE = Param.longInt("sinceSequence", "only packets after this sequence number").or(0L);
    public static final Param<Integer> LIMIT = Param.integer("limit", "max packets returned, up to 1000").or(100);
    public static final Param<Boolean> REVERSE = Param.bool("reverse", "newest first").or(false);
    public static final Param<Boolean> CLEAR_AFTER_DUMP = Param.bool("clearAfterDump", "clear the recording afterwards").or(false);
    public static final Param<Boolean> PARSE_BUNDLES = Param.bool("parseBundlePackets", "list packets inside bundles").or(true);
    public static final Param<Map<String, Object>> FILTERS = Param.object("filters",
            "named condition filters {name: expression}, see docs/packet-tools.md");
    /** Everything {@link #from} reads. */
    public static final List<Param<?>> PARAMS = List.of(DIRECTION, CLASS_CONTAINS, NAME_CONTAINS, SINCE_SEQUENCE, LIMIT,
            REVERSE, CLEAR_AFTER_DUMP, PARSE_BUNDLES, FILTERS);

    public static PacketFilter from(Map<String, Object> args) {
        if (args == null) args = Map.of();
        PacketDirection direction = PacketDirection.parse(DIRECTION.get(args));
        int limit = Math.min(1000, Math.max(0, LIMIT.get(args)));
        return new PacketFilter(direction, CLASS_CONTAINS.get(args), NAME_CONTAINS.get(args), SINCE_SEQUENCE.get(args), limit,
                REVERSE.get(args), CLEAR_AFTER_DUMP.get(args), PARSE_BUNDLES.get(args), parseNamedFilters(FILTERS.get(args)));
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
