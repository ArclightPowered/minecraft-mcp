package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.util.PathReader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class ConditionEvaluator {
    record FunctionDef(int arity, Function<List<Object>, Object> eval) {
    }

    static final Map<String, FunctionDef> FUNCTIONS = Map.of(
        "exists", new FunctionDef(1, args -> args.get(0) != null),
        "missing", new FunctionDef(1, args -> args.get(0) == null),
        "contains", new FunctionDef(2, args -> stringPair(args, String::contains)),
        "startsWith", new FunctionDef(2, args -> stringPair(args, String::startsWith)),
        "endsWith", new FunctionDef(2, args -> stringPair(args, String::endsWith)),
        "matches", new FunctionDef(2, args -> stringPair(args, (value, regex) -> Pattern.compile(regex).matcher(value).matches())),
        "size", new FunctionDef(1, args -> switch (args.get(0)) {
            case Collection<?> c -> c.size();
            case Map<?, ?> m -> m.size();
            case CharSequence s -> s.length();
            case null, default -> 0;
        }));

    private ConditionEvaluator() {
    }

    public static boolean evaluateBoolean(ConditionExpression expression, ConditionContext context) {
        Object value = evaluate(expression, context);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.TRUE.equals(value);
    }

    public static Object evaluate(ConditionExpression expression, ConditionContext context) {
        if (expression instanceof ConditionExpression.Literal(Object value)) {
            return value;
        }
        if (expression instanceof ConditionExpression.Name(String name)) {
            return context.resolveName(name);
        }
        if (expression instanceof ConditionExpression.Access(ConditionExpression base, String name)) {
            return PathReader.read(evaluate(base, context), name);
        }
        if (expression instanceof ConditionExpression.Root) {
            return context.implicitRoot();
        }
        if (expression instanceof ConditionExpression.Unary(String op, ConditionExpression expr)) {
            if ("!".equals(op)) {
                return !truthy(evaluate(expr, context));
            }
            throw new IllegalArgumentException("Unsupported unary operator: " + op);
        }
        if (expression instanceof ConditionExpression.Binary binary) {
            return evalBinary(binary, context);
        }
        if (expression instanceof ConditionExpression.Call call) {
            return evalCall(call, context);
        }
        throw new IllegalArgumentException("Unsupported expression: " + expression);
    }

    private static Object evalBinary(ConditionExpression.Binary binary, ConditionContext context) {
        String op = binary.op();
        if ("&&".equals(op)) {
            return truthy(evaluate(binary.left(), context)) && truthy(evaluate(binary.right(), context));
        }
        if ("||".equals(op)) {
            return truthy(evaluate(binary.left(), context)) || truthy(evaluate(binary.right(), context));
        }
        Object left = evaluate(binary.left(), context);
        Object right = evaluate(binary.right(), context);
        return switch (op) {
            case "==" -> equalsValue(left, right);
            case "!=" -> !equalsValue(left, right);
            case ">" -> compare(left, right) > 0;
            case ">=" -> compare(left, right) >= 0;
            case "<" -> compare(left, right) < 0;
            case "<=" -> compare(left, right) <= 0;
            default -> throw new IllegalArgumentException("Unsupported binary operator: " + op);
        };
    }

    private static Object evalCall(ConditionExpression.Call call, ConditionContext context) {
        FunctionDef def = FUNCTIONS.get(call.name());
        if (def == null) {
            throw new IllegalArgumentException("Unsupported function: " + call.name());
        }
        if (call.args().size() != def.arity()) {
            throw new IllegalArgumentException(call.name() + " expects " + def.arity() + " args but got " + call.args().size());
        }
        List<Object> args = call.args().stream().map(arg -> evaluate(arg, context)).toList();
        return def.eval().apply(args);
    }

    private static boolean stringPair(List<Object> args, BiPredicate<String, String> test) {
        Object value = args.get(0);
        return value != null && test.test(String.valueOf(value), String.valueOf(args.get(1)));
    }

    static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value);
    }

    static boolean equalsValue(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return Double.compare(l.doubleValue(), r.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    static int compare(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return Double.compare(l.doubleValue(), r.doubleValue());
        }
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values");
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
