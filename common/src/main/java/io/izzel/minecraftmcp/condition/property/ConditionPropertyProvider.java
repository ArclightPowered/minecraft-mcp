package io.izzel.minecraftmcp.condition.property;

import java.util.Set;

public interface ConditionPropertyProvider {
    void register(ConditionPropertyRegistry registry);

    default Set<String> sides() {
        return Set.of("client", "server");
    }
}
