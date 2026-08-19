package io.izzel.minecraftmcp.packet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record PacketRecorderSnapshot(
    boolean recording,
    int count,
    int serverboundCount,
    int clientboundCount,
    long nextSequence,
    RecordedPacket last,
    Map<String, Integer> namedFilterCounts,
    Map<String, Map<String, Integer>> namedFilterErrors
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
        Set<String> names = new LinkedHashSet<>(namedFilterCounts.keySet());
        names.addAll(namedFilterErrors.keySet());
        for (String name : names) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", namedFilterCounts.getOrDefault(name, 0));
            Map<String, Integer> errors = namedFilterErrors.get(name);
            if (errors != null && !errors.isEmpty()) {
                entry.put("errors", errors);
            }
            filter.put(name, entry);
        }
        if (!filter.isEmpty()) {
            map.put("filter", filter);
        }
        return map;
    }
}
