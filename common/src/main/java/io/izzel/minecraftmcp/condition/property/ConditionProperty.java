package io.izzel.minecraftmcp.condition.property;

@FunctionalInterface
public interface ConditionProperty {
    Object load(ConditionPropertyContext context) throws Exception;
}
