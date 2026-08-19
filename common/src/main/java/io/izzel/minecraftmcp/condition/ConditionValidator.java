package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.condition.property.DefaultConditionPropertyRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConditionValidator {
    private ConditionValidator() {
    }

    public static void validate(ConditionExpression expression, DefaultConditionPropertyRegistry registry, String where) {
        Set<String> known = new HashSet<>(registry.contextPropertyNames());
        known.addAll(registry.globalNames());
        validate(expression, known, where);
    }

    public static void validate(ConditionExpression expression, Set<String> knownNames, String where) {
        Set<String> unknownNames = new TreeSet<>();
        Set<String> unknownFunctions = new TreeSet<>();
        Set<String> callErrors = new LinkedHashSet<>();
        collect(expression, knownNames, unknownNames, unknownFunctions, callErrors);
        List<String> parts = new ArrayList<>();
        if (!unknownNames.isEmpty()) {
            Set<String> known = new TreeSet<>(knownNames);
            known.add("$");
            String plural = unknownNames.size() == 1 ? "property" : "properties";
            parts.add("Unknown condition " + plural + " " + unknownNames + where + "; known: " + known);
        }
        if (!unknownFunctions.isEmpty()) {
            String plural = unknownFunctions.size() == 1 ? "function" : "functions";
            parts.add("Unknown condition " + plural + " " + unknownFunctions + where + "; known functions: " + new TreeSet<>(ConditionEvaluator.FUNCTIONS.keySet()));
        }
        for (String error : callErrors) {
            parts.add(error + where);
        }
        if (parts.isEmpty()) {
            return;
        }
        throw new ConditionValidationException(String.join("; ", parts));
    }

    private static void collect(ConditionExpression expression, Set<String> knownNames, Set<String> unknownNames, Set<String> unknownFunctions, Set<String> callErrors) {
        if (expression instanceof ConditionExpression.Name(String name)) {
            if (!knownNames.contains(name)) {
                unknownNames.add(name);
            }
        } else if (expression instanceof ConditionExpression.Access access) {
            collect(access.base(), knownNames, unknownNames, unknownFunctions, callErrors);
        } else if (expression instanceof ConditionExpression.Unary unary) {
            collect(unary.expr(), knownNames, unknownNames, unknownFunctions, callErrors);
        } else if (expression instanceof ConditionExpression.Binary binary) {
            collect(binary.left(), knownNames, unknownNames, unknownFunctions, callErrors);
            collect(binary.right(), knownNames, unknownNames, unknownFunctions, callErrors);
        } else if (expression instanceof ConditionExpression.Call call) {
            checkCall(call, unknownFunctions, callErrors);
            for (ConditionExpression arg : call.args()) {
                collect(arg, knownNames, unknownNames, unknownFunctions, callErrors);
            }
        }
    }

    private static void checkCall(ConditionExpression.Call call, Set<String> unknownFunctions, Set<String> callErrors) {
        ConditionEvaluator.FunctionDef def = ConditionEvaluator.FUNCTIONS.get(call.name());
        if (def == null) {
            unknownFunctions.add(call.name());
            return;
        }
        if (call.args().size() != def.arity()) {
            callErrors.add(call.name() + " expects " + def.arity() + " args but got " + call.args().size());
            return;
        }
        if ("matches".equals(call.name()) && call.args().get(1) instanceof ConditionExpression.Literal(Object value)) {
            try {
                Pattern.compile(String.valueOf(value));
            } catch (PatternSyntaxException e) {
                callErrors.add("Invalid matches regex \"" + value + "\" (" + e.getDescription() + ")");
            }
        }
    }

    public static final class ConditionValidationException extends RuntimeException {
        public ConditionValidationException(String message) {
            super(message);
        }
    }
}
