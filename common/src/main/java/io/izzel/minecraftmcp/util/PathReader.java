package io.izzel.minecraftmcp.util;

import io.izzel.minecraftmcp.condition.property.LazyPropertyObject;

import java.util.List;
import java.util.Map;

public final class PathReader {
    private PathReader() {}

    public static Object read(Object root, String path) {
        if (path == null || path.isBlank()) return root;
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof LazyPropertyObject lazy) {
                current = lazy.get(part);
            } else if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= list.size()) return null;
                    current = list.get(index);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }
}
