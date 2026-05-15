package io.izzel.minecraftmcp.condition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ConditionEvaluator {
    private ConditionEvaluator() {}

    public static boolean evaluateBoolean(ConditionExpression expression, ConditionContext context) {
        Object value = evaluate(expression, context);
        if (value instanceof Boolean b) return b;
        return Boolean.TRUE.equals(value);
    }

    public static Object evaluate(ConditionExpression expression, ConditionContext context) {
        if (expression instanceof ConditionExpression.Literal literal) return literal.value();
        if (expression instanceof ConditionExpression.Path path) return context.resolve(path.root(), path.parts());
        if (expression instanceof ConditionExpression.Unary unary) {
            if ("!".equals(unary.op())) return !truthy(evaluate(unary.expr(), context));
            throw new IllegalArgumentException("Unsupported unary operator: " + unary.op());
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
        if ("&&".equals(op)) return truthy(evaluate(binary.left(), context)) && truthy(evaluate(binary.right(), context));
        if ("||".equals(op)) return truthy(evaluate(binary.left(), context)) || truthy(evaluate(binary.right(), context));
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
        List<Object> args = call.args().stream().map(arg -> evaluate(arg, context)).toList();
        return switch (call.name()) {
            case "exists" -> requireArgs(call, args, 1).get(0) != null;
            case "missing" -> requireArgs(call, args, 1).get(0) == null;
            case "contains" -> {
                requireArgs(call, args, 2);
                Object value = args.get(0);
                yield value != null && String.valueOf(value).contains(String.valueOf(args.get(1)));
            }
            case "startsWith" -> {
                requireArgs(call, args, 2);
                Object value = args.get(0);
                yield value != null && String.valueOf(value).startsWith(String.valueOf(args.get(1)));
            }
            case "endsWith" -> {
                requireArgs(call, args, 2);
                Object value = args.get(0);
                yield value != null && String.valueOf(value).endsWith(String.valueOf(args.get(1)));
            }
            case "matches" -> {
                requireArgs(call, args, 2);
                Object value = args.get(0);
                yield value != null && Pattern.compile(String.valueOf(args.get(1))).matcher(String.valueOf(value)).matches();
            }
            case "size" -> {
                requireArgs(call, args, 1);
                Object value = args.get(0);
                if (value instanceof Collection<?> c) yield c.size();
                if (value instanceof Map<?, ?> m) yield m.size();
                if (value instanceof CharSequence s) yield s.length();
                yield 0;
            }
            default -> throw new IllegalArgumentException("Unsupported function: " + call.name());
        };
    }

    private static List<Object> requireArgs(ConditionExpression.Call call, List<Object> args, int count) {
        if (args.size() != count) throw new IllegalArgumentException(call.name() + " expects " + count + " args but got " + args.size());
        return args;
    }

    static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value);
    }

    static boolean equalsValue(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) return Double.compare(l.doubleValue(), r.doubleValue()) == 0;
        return Objects.equals(left, right);
    }

    static int compare(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) return Double.compare(l.doubleValue(), r.doubleValue());
        if (left == null || right == null) throw new IllegalArgumentException("Cannot compare null values");
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
