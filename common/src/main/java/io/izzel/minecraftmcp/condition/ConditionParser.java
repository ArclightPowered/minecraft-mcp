package io.izzel.minecraftmcp.condition;

import java.util.ArrayList;
import java.util.List;

public final class ConditionParser {
    private final String input;
    private int pos;

    private ConditionParser(String input) {
        this.input = input == null ? "" : input;
    }

    public static ConditionExpression parse(String input) {
        ConditionParser parser = new ConditionParser(input);
        ConditionExpression expression = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isEnd()) throw parser.error("Unexpected token");
        return expression;
    }

    private ConditionExpression parseExpression() { return parseOr(); }

    private ConditionExpression parseOr() {
        ConditionExpression left = parseAnd();
        while (true) {
            skipWhitespace();
            if (match("||")) left = new ConditionExpression.Binary("||", left, parseAnd());
            else return left;
        }
    }

    private ConditionExpression parseAnd() {
        ConditionExpression left = parseUnary();
        while (true) {
            skipWhitespace();
            if (match("&&")) left = new ConditionExpression.Binary("&&", left, parseUnary());
            else return left;
        }
    }

    private ConditionExpression parseUnary() {
        skipWhitespace();
        if (match("!")) return new ConditionExpression.Unary("!", parseUnary());
        return parseComparison();
    }

    private ConditionExpression parseComparison() {
        ConditionExpression left = parsePrimary();
        skipWhitespace();
        for (String op : List.of("==", "!=", ">=", "<=", ">", "<")) {
            if (match(op)) return new ConditionExpression.Binary(op, left, parsePrimary());
        }
        return left;
    }

    private ConditionExpression parsePrimary() {
        skipWhitespace();
        if (isEnd()) throw error("Expected expression");
        char c = peek();
        if (c == '(') {
            pos++;
            ConditionExpression expression = parseExpression();
            skipWhitespace();
            expect(')');
            return expression;
        }
        if (c == '"' || c == '\'') return new ConditionExpression.Literal(parseString());
        if (c == '-' || Character.isDigit(c)) return parseNumber();
        if (isIdentifierStart(c)) {
            String ident = parseIdentifier();
            skipWhitespace();
            if (match("(")) {
                List<ConditionExpression> args = new ArrayList<>();
                skipWhitespace();
                if (!match(")")) {
                    do {
                        args.add(parseExpression());
                        skipWhitespace();
                    } while (match(","));
                    expect(')');
                }
                return new ConditionExpression.Call(ident, args);
            }
            return switch (ident) {
                case "true" -> new ConditionExpression.Literal(Boolean.TRUE);
                case "false" -> new ConditionExpression.Literal(Boolean.FALSE);
                case "null" -> new ConditionExpression.Literal(null);
                default -> parseShorthandPath(ident);
            };
        }
        throw error("Unexpected character: " + c);
    }

    private ConditionExpression parseShorthandPath(String first) {
        return parsePathFrom(first);
    }

    private ConditionExpression parsePathFrom(String first) {
        List<String> properties = new ArrayList<>();
        properties.add(first);
        while (match(".")) {
            if (isEnd()) throw error("Expected property");
            properties.add(parseProperty());
        }
        return new ConditionExpression.Path("", List.copyOf(properties));
    }

    private String parseProperty() {
        skipWhitespace();
        if (isEnd()) throw error("Expected property");
        if (Character.isDigit(peek())) return parseUnsignedInt();
        return parseIdentifier();
    }

    private ConditionExpression parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (!isEnd() && Character.isDigit(peek())) pos++;
        boolean decimal = false;
        if (!isEnd() && peek() == '.') {
            decimal = true;
            pos++;
            while (!isEnd() && Character.isDigit(peek())) pos++;
        }
        String text = input.substring(start, pos);
        try {
            return new ConditionExpression.Literal(decimal ? Double.parseDouble(text) : Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw error("Invalid number: " + text);
        }
    }

    private String parseString() {
        char quote = peek();
        pos++;
        StringBuilder sb = new StringBuilder();
        while (!isEnd()) {
            char c = input.charAt(pos++);
            if (c == quote) return sb.toString();
            if (c == '\\') {
                if (isEnd()) throw error("Unterminated escape sequence");
                char e = input.charAt(pos++);
                sb.append(switch (e) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '\'' -> '\'';
                    case '"' -> '"';
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        throw error("Unterminated string literal");
    }

    private String parseIdentifier() {
        skipWhitespace();
        if (isEnd() || !isIdentifierStart(peek())) throw error("Expected identifier");
        int start = pos++;
        while (!isEnd() && isIdentifierPart(peek())) pos++;
        return input.substring(start, pos);
    }

    private String parseUnsignedInt() {
        int start = pos;
        while (!isEnd() && Character.isDigit(peek())) pos++;
        return input.substring(start, pos);
    }

    private boolean isIdentifierStart(char c) { return Character.isLetter(c) || c == '_' || c == '$'; }
    private boolean isIdentifierPart(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$'; }
    private void skipWhitespace() { while (!isEnd() && Character.isWhitespace(peek())) pos++; }
    private boolean match(String s) { if (input.startsWith(s, pos)) { pos += s.length(); return true; } return false; }
    private void expect(char c) { if (isEnd() || input.charAt(pos) != c) throw error("Expected '" + c + "'"); pos++; }
    private char peek() { return input.charAt(pos); }
    private boolean isEnd() { return pos >= input.length(); }
    private ConditionSyntaxException error(String message) { return new ConditionSyntaxException(message + " at offset " + pos + " in: " + input); }
}
