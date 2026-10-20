package io.izzel.minecraftmcp.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The chat and system messages this client received, newest last, each with a sequence number so a reader can
 * ask for "everything after what I saw" ({@code since}) and never miss or repeat a line. The loaders feed it from
 * their chat-received events.
 */
public final class ChatLog {
    public static final ChatLog CLIENT = new ChatLog(500);

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long lastSequence;

    public ChatLog(int capacity) {
        this.capacity = capacity;
    }

    public record Entry(long sequence, long timeMillis, String kind, String text) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sequence", sequence);
            map.put("timeMillis", timeMillis);
            map.put("kind", kind);
            map.put("text", text);
            return map;
        }
    }

    /** kind: chat (a player's message) or system (command feedback, server and game messages). */
    public synchronized void add(String kind, String text) {
        entries.addLast(new Entry(++lastSequence, System.currentTimeMillis(), kind, text));
        while (entries.size() > capacity) entries.removeFirst();
        notifyAll();
    }

    public synchronized long lastSequence() {
        return lastSequence;
    }

    /** Up to {@code limit} newest entries after {@code since} that match. */
    public synchronized List<Entry> read(long since, int limit, Predicate<String> filter) {
        List<Entry> matched = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.sequence > since && filter.test(entry.text)) matched.add(entry);
        }
        return matched.subList(Math.max(0, matched.size() - Math.max(0, limit)), matched.size());
    }

    /** The first entry after {@code since} that matches, waiting up to {@code timeoutMs}; null on timeout. */
    public synchronized Entry await(long since, Predicate<String> filter, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + Math.max(0, timeoutMs) * 1_000_000L;
        while (true) {
            for (Entry entry : entries) {
                if (entry.sequence > since && filter.test(entry.text)) return entry;
            }
            long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
            if (remainingMs <= 0) return null;
            wait(remainingMs);
        }
    }
}
