package io.izzel.minecraftmcp.scenario;

import io.izzel.minecraftmcp.util.PathReader;

import java.util.*;

public final class ScenarioAssertion {
    private ScenarioAssertion() {}

    @SuppressWarnings("unchecked")
    public static void assertExpect(Map<String, Object> stepReport, Map<String, Object> expect) {
        for (Map.Entry<String, Object> entry : expect.entrySet()) {
            Object actual = readPath(stepReport, entry.getKey());
            Object expected = entry.getValue();
            if (expected instanceof Map<?, ?> matcher) {
                if (matcher.containsKey("contains")) {
                    String needle = String.valueOf(matcher.get("contains"));
                    if (actual == null || !String.valueOf(actual).contains(needle)) {
                        throw new AssertionError("Expectation failed at " + entry.getKey() + ": expected contains " + needle + " but was " + actual);
                    }
                } else if (matcher.containsKey("equals")) {
                    assertEquals(entry.getKey(), matcher.get("equals"), actual);
                } else {
                    throw new AssertionError("Unsupported matcher at " + entry.getKey() + ": " + matcher);
                }
            } else {
                assertEquals(entry.getKey(), expected, actual);
            }
        }
    }

    public static Object readPath(Object root, String path) {
        return PathReader.read(root, path);
    }

    private static void assertEquals(String path, Object expected, Object actual) {
        if (expected instanceof Number e && actual instanceof Number a) {
            if (Double.compare(e.doubleValue(), a.doubleValue()) == 0) return;
        } else if (Objects.equals(expected, actual)) {
            return;
        }
        throw new AssertionError("Expectation failed at " + path + ": expected " + expected + " but was " + actual);
    }
}
