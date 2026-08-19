package io.izzel.minecraftmcp.packet;

import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.condition.ConditionContext;

public final class PacketNamedFilter {
    private final ConditionExpression expression;

    private PacketNamedFilter(ConditionExpression expression) {
        this.expression = expression;
    }

    static PacketNamedFilter from(Object value) {
        if (value instanceof String expression) {
            return new PacketNamedFilter(ConditionParser.parse(expression));
        }
        throw new IllegalArgumentException("Packet filter must be an expression string: " + value);
    }

    boolean matches(RecordedPacket packet) {
        return ConditionEvaluator.evaluateBoolean(expression, ConditionContext.overValues(packet.toMap()));
    }
}
