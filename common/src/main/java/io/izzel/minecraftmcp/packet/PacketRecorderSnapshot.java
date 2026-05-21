package io.izzel.minecraftmcp.packet;

import java.util.LinkedHashMap;
import java.util.Map;

public record PacketRecorderSnapshot(
        boolean recording,
        int count,
        int serverboundCount,
        int clientboundCount,
        long nextSequence,
        RecordedPacket last,
        Map<String, Integer> namedFilterCounts
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recording", recording);
        map.put("count", count);
        map.put("serverbound", Map.of("count", serverboundCount));
        map.put("clientbound", Map.of("count", clientboundCount));
        map.put("nextSequence", nextSequence);
        map.put("last", last == null ? null : last.toMap());
        Map<String, Object> filter = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : namedFilterCounts.entrySet()) {
            filter.put(entry.getKey(), Map.of("count", entry.getValue()));
        }
        if (!filter.isEmpty()) map.put("filter", filter);
        return map;
    }
}
