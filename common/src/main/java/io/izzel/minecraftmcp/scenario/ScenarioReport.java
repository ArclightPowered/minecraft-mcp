package io.izzel.minecraftmcp.scenario;

import java.time.Instant;
import java.util.*;

public final class ScenarioReport {
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<Map<String,Object>> scenarios = new ArrayList<>();

    public ScenarioReport(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void add(String name, String status, List<Map<String,Object>> steps, String error) {
        add(name, status, steps, error, Map.of());
    }

    public void add(String name, String status, List<Map<String,Object>> steps, String error, Map<String, Object> metadata) {
        Map<String,Object> scenario = new LinkedHashMap<>();
        scenario.put("name", name);
        scenario.put("status", status);
        scenario.put("steps", steps);
        scenario.putAll(metadata);
        if (error != null) scenario.put("error", error);
        scenarios.add(scenario);
    }

    public void finish() {
        finishedAt = Instant.now();
    }

    public int passed() { return count("passed"); }
    public int failed() { return count("failed"); }
    public int skipped() { return count("skipped"); }
    public int expectedFailed() { return count("expected_failed"); }
    public int unexpectedPassed() { return count("unexpected_passed"); }

    private int count(String status) {
        return (int) scenarios.stream().filter(s -> status.equals(s.get("status"))).count();
    }

    public Map<String,Object> toMap() {
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("startedAt", startedAt.toString());
        map.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
        map.put("scenarios", scenarios);
        map.put("summary", Map.of(
                "passed", passed(),
                "failed", failed(),
                "skipped", skipped(),
                "expectedFailed", expectedFailed(),
                "unexpectedPassed", unexpectedPassed()
        ));
        map.put("expectedFailed", expectedFailed());
        map.put("unexpectedPassed", unexpectedPassed());
        return map;
    }
}
