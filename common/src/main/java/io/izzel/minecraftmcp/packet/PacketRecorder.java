package io.izzel.minecraftmcp.packet;

import net.minecraft.network.protocol.BundlePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketRecorder {
    private final List<RecordedPacket> packets = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private boolean recording;
    private PacketFilter recordingFilter = PacketFilter.empty();
    private int maxPackets = 1000;
    private Map<String, Integer> namedFilterCounts = new LinkedHashMap<>();

    public synchronized void start(PacketFilter filter, int maxPackets, boolean clear) {
        this.recordingFilter = filter == null ? PacketFilter.empty() : filter;
        this.maxPackets = Math.max(1, maxPackets);
        if (clear) clear();
        this.namedFilterCounts = new LinkedHashMap<>();
        for (String name : this.recordingFilter.namedFilters().keySet()) {
            this.namedFilterCounts.put(name, 0);
        }
        this.recording = true;
    }

    public synchronized void stop() {
        this.recording = false;
        this.namedFilterCounts = new LinkedHashMap<>();
    }

    public synchronized void clear() {
        packets.clear();
        namedFilterCounts.replaceAll((k, v) -> 0);
    }

    public synchronized boolean recording() {
        return recording;
    }

    public void record(PacketDirection direction, String packetClass, Map<String, Object> summary) {
        if (direction == null || packetClass == null || packetClass.isBlank()) return;
        RecordedPacket candidate = new RecordedPacket(
                sequence.getAndIncrement(),
                System.currentTimeMillis(),
                direction,
                packetClass,
                simpleName(packetClass),
                "",
                "",
                summary == null ? Map.of() : new LinkedHashMap<>(summary)
        );
        synchronized (this) {
            if (!recording || !recordingFilter.matches(candidate)) return;
            for (Map.Entry<String, PacketNamedFilter> entry : recordingFilter.namedFilters().entrySet()) {
                if (entry.getValue().matches(candidate)) {
                    namedFilterCounts.merge(entry.getKey(), 1, Integer::sum);
                }
            }
            packets.add(candidate);
            while (packets.size() > maxPackets) packets.removeFirst();
        }
    }

    public void record(PacketDirection direction, Object packet) {
        if (packet == null) return;
        PacketFilter filter;
        synchronized (this) {
            filter = this.recordingFilter;
        }
        if (filter.parseBundlePackets() && packet instanceof BundlePacket<?> bundlePacket) {
            for (Object subPacket : bundlePacket.subPackets()) {
                record(direction, subPacket);
            }
            return;
        }
        record(direction, packet.getClass().getName(), Map.of("toString", String.valueOf(packet)));
    }

    public synchronized PacketRecorderSnapshot status() {
        int serverbound = 0;
        int clientbound = 0;
        for (RecordedPacket packet : packets) {
            if (packet.direction() == PacketDirection.SERVERBOUND) serverbound++;
            if (packet.direction() == PacketDirection.CLIENTBOUND) clientbound++;
        }
        RecordedPacket last = packets.isEmpty() ? null : packets.getLast();
        return new PacketRecorderSnapshot(recording, packets.size(), serverbound, clientbound, sequence.get(), last, Map.copyOf(namedFilterCounts));
    }

    public synchronized PacketDump dump(PacketFilter filter) {
        if (filter == null) filter = PacketFilter.empty();
        List<RecordedPacket> matched = new ArrayList<>();
        for (RecordedPacket packet : packets) {
            if (filter.matches(packet)) matched.add(packet);
        }
        int total = matched.size();
        if (filter.reverse()) Collections.reverse(matched);
        if (matched.size() > filter.limit()) matched = new ArrayList<>(matched.subList(0, filter.limit()));
        long next = sequence.get();
        PacketDump dump = new PacketDump(recording, total, matched.size(), next, List.copyOf(matched));
        if (filter.clearAfterDump()) clear();
        return dump;
    }

    private static String simpleName(String className) {
        int idx = className.lastIndexOf('.');
        return idx >= 0 ? className.substring(idx + 1) : className;
    }
}
