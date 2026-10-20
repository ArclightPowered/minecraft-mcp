package io.izzel.minecraftmcp.condition;

import java.util.List;

public sealed interface ConditionExpression permits ConditionExpression.Literal,
    ConditionExpression.Name, ConditionExpression.Access,
    ConditionExpression.Root, ConditionExpression.Unary, ConditionExpression.Binary, ConditionExpression.Call {

    record Literal(Object value) implements ConditionExpression {
    }

    record Name(String name) implements ConditionExpression {
    }

    record Access(ConditionExpression base, String name) implements ConditionExpression {
    }

    record Root() implements ConditionExpression {
    }

    record Unary(String op, ConditionExpression expr) implements ConditionExpression {
    }

    record Binary(String op, ConditionExpression left, ConditionExpression right) implements ConditionExpression {
    }

    record Call(String name, List<ConditionExpression> args) implements ConditionExpression {
    }
}
