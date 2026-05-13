package io.izzel.minecraftmcp.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {
    @Test
    void parsesNestedObjectsArraysStringsNumbersBooleansAndNull() {
        Object value = Json.parse("{\"name\":\"scenario\",\"steps\":[{\"ticks\":5}],\"ok\":true,\"none\":null}");

        Map<?, ?> object = assertInstanceOf(Map.class, value);
        assertEquals("scenario", object.get("name"));
        assertEquals(Boolean.TRUE, object.get("ok"));
        assertTrue(object.containsKey("none"));
        List<?> steps = assertInstanceOf(List.class, object.get("steps"));
        Map<?, ?> step = assertInstanceOf(Map.class, steps.get(0));
        assertEquals(5, ((Number) step.get("ticks")).intValue());
    }

    @Test
    void writesJsonWithEscapingAndRoundTrips() {
        Map<String, Object> original = Map.of(
                "text", "line\\nquoted \\\"value\\\"",
                "items", List.of(1, true, "x")
        );

        String encoded = Json.stringify(original);
        Object decoded = Json.parse(encoded);

        assertEquals(original.get("text"), ((Map<?, ?>) decoded).get("text"));
        List<?> items = (List<?>) ((Map<?, ?>) decoded).get("items");
        assertEquals(1, ((Number) items.get(0)).intValue());
        assertEquals(Boolean.TRUE, items.get(1));
        assertEquals("x", items.get(2));
    }
}
