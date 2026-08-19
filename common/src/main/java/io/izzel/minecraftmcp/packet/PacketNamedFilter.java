package io.izzel.minecraftmcp.packet;

import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionValidator;

import java.util.List;
import java.util.Set;

public final class PacketNamedFilter {
    static final List<String> PROPERTY_NAMES = List.of(
        "sequence", "timeMillis", "direction", "packetClass", "packetSimpleName", "protocol", "phase", "summary");

    private final ConditionExpression expression;

    private PacketNamedFilter(ConditionExpression expression) {
        this.expression = expression;
    }

    static PacketNamedFilter from(Object value) {
        if (value instanceof String expression) {
            ConditionExpression parsed = ConditionParser.parse(expression);
            ConditionValidator.validate(parsed, Set.copyOf(PROPERTY_NAMES), " in packet filters");
            return new PacketNamedFilter(parsed);
        }
        throw new IllegalArgumentException("Packet filter must be an expression string: " + value);
    }

    boolean matches(RecordedPacket packet) {
        return ConditionEvaluator.evaluateBoolean(expression, ConditionContext.overValues(packet.toMap()));
    }
}
