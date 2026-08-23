package io.izzel.minecraftmcp.scenario;

import io.izzel.minecraftmcp.json.Json;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public final class ScenarioEngine {
    private final ToolRegistry registry;
    private volatile ScenarioReport latest;

    public ScenarioEngine(ToolRegistry registry) {
        this.registry = registry;
    }

    public Optional<ScenarioReport> latestReport() {
        return Optional.ofNullable(latest);
    }

    public ScenarioReport runBatch(String directory) throws Exception {
        return runBatch(directory, ScenarioRunOptions.defaults());
    }

    @SuppressWarnings("unchecked")
    public ScenarioReport runBatch(String directory, ScenarioRunOptions options) throws Exception {
        Path dir = directory == null || directory.isBlank() ? Paths.get("examples/scenarios") : Paths.get(directory);
        ScenarioReport report = new ScenarioReport(Instant.now());
        latest = report;
        if (!Files.isDirectory(dir)) {
            report.add(dir.toString(), "failed", List.of(), "Scenario directory not found");
            report.finish();
            return report;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                runOne(path, report, options);
            }
        }
        report.finish();
        return report;
    }

    @SuppressWarnings("unchecked")
    private void runOne(Path path, ScenarioReport report, ScenarioRunOptions options) {
        List<Map<String, Object>> stepReports = new ArrayList<>();
        String name = path.getFileName().toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        String expected = "pass";
        try {
            Map<String, Object> scenario = (Map<String, Object>) Json.parse(Files.readString(path));
            name = String.valueOf(scenario.getOrDefault("name", name));
            expected = String.valueOf(scenario.getOrDefault("expected", "pass"));
            List<String> tags = stringList(scenario.get("tags"));
            if (!tags.isEmpty()) metadata.put("tags", tags);
            metadata.put("path", path.toString());

            String skipReason = skipReason(scenario, tags, options);
            if (skipReason != null) {
                metadata.put("skipReason", skipReason);
                report.add(name, "skipped", stepReports, null, metadata);
                return;
            }

            List<Object> steps = (List<Object>) scenario.getOrDefault("steps", List.of());
            for (Object stepObj : steps) {
                Map<String, Object> step = (Map<String, Object>) stepObj;
                String id = String.valueOf(step.getOrDefault("id", step.get("tool")));
                String tool = String.valueOf(step.get("tool"));
                Map<String, Object> args = step.get("args") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                Object result = registry.call(tool, args);
                Map<String, Object> stepReport = new LinkedHashMap<>();
                stepReport.put("id", id);
                stepReport.put("tool", tool);
                stepReport.put("status", "passed");
                stepReport.put("result", result);
                if (step.get("expect") instanceof Map<?, ?> expect) {
                    ScenarioAssertion.assertExpect(stepReport, (Map<String, Object>) expect);
                }
                stepReports.add(stepReport);
            }
            report.add(name, "fail".equals(expected) ? "unexpected_passed" : "passed", stepReports, null, metadata);
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            report.add(name, "fail".equals(expected) ? "expected_failed" : "failed", stepReports, message, metadata);
        }
    }

    @SuppressWarnings("unchecked")
    private String skipReason(Map<String, Object> scenario, List<String> tags, ScenarioRunOptions options) {
        if (!options.includeTags().isEmpty() && Collections.disjoint(tags, options.includeTags())) {
            return "missing includeTags " + options.includeTags();
        }
        if (!options.excludeTags().isEmpty() && !Collections.disjoint(tags, options.excludeTags())) {
            return "matched excludeTags " + options.excludeTags();
        }
        if (scenario.get("requires") instanceof Map<?, ?> requires) {
            List<String> loaders = stringList(requires.get("loaders"));
            if (options.loader() != null && !loaders.isEmpty() && !loaders.contains(options.loader())) {
                return "loader " + options.loader() + " not in " + loaders;
            }
            List<String> sides = stringList(requires.get("sides"));
            if (options.side() != null && !sides.isEmpty() && !sides.contains(options.side())) {
                return "side " + options.side() + " not in " + sides;
            }
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }
}
