package io.izzel.minecraftmcp.condition.property;

import java.util.Set;

public interface ConditionPropertyRegistry {
    void registerContextProperty(String name, ConditionProperty property);

    void registerGlobal(String name, ConditionProperty property);

    Set<String> contextPropertyNames();

    Set<String> globalNames();
}
