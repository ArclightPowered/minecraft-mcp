package io.izzel.minecraftmcp.packet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PacketDump(
        boolean recording,
        int total,
        int returned,
        long nextSinceSequence,
        List<RecordedPacket> packets
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recording", recording);
        map.put("total", total);
        map.put("returned", returned);
        map.put("nextSinceSequence", nextSinceSequence);
        map.put("packets", packets.stream().map(RecordedPacket::toMap).toList());
        return map;
    }
}
