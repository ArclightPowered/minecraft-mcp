package io.izzel.minecraftmcp.condition.property;

import java.util.function.Function;

public final class LazyPropertyObject {
    private final Function<String, Object> resolver;

    public LazyPropertyObject(Function<String, Object> resolver) {
        this.resolver = resolver;
    }

    public Object get(String name) {
        return resolver.apply(name);
    }
}
