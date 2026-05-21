package io.izzel.minecraftmcp.packet;

import java.util.LinkedHashMap;
import java.util.Map;

public record RecordedPacket(
        long sequence,
        long timeMillis,
        PacketDirection direction,
        String packetClass,
        String packetSimpleName,
        String protocol,
        String phase,
        Map<String, Object> summary
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sequence", sequence);
        map.put("timeMillis", timeMillis);
        map.put("direction", direction.id());
        map.put("packetClass", packetClass);
        map.put("packetSimpleName", packetSimpleName);
        map.put("protocol", protocol);
        map.put("phase", phase);
        map.put("summary", summary == null ? Map.of() : summary);
        return map;
    }
}
