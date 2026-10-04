package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;

import java.util.ArrayList;
import java.util.List;

public class CEMExpressionParser {
    private final String expression;
    private int pos;
    private final int length;

    public CEMExpressionParser(String expression) {
        this.expression = expression.trim();
        this.length = this.expression.length();
        this.pos = 0;
    }

    public static CEMExpression parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return new CEMLiteral(0);
        }
        try {
            CEMExpressionParser parser = new CEMExpressionParser(expression);
            return parser.parseExpression();
        } catch (Exception e) {
            DatariumMain.LOGGER.warn("[CEM] Failed to parse expression: {}", expression, e);
            return new CEMLiteral(0);
        }
    }

    private CEMExpression parseExpression() {
        return parseLogical();
    }

    private CEMExpression parseLogical() {
        CEMExpression left = parseComparison();
        skipWhitespace();

        while (pos < length) {
            if (match("||")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.OR, left, parseComparison());
            } else if (match("&&")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.AND, left, parseComparison());
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseComparison() {
        CEMExpression left = parseAddSub();
        skipWhitespace();

        while (pos < length) {
            if (match("==")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.EQ, left, parseAddSub());
            } else if (match("!=")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.NEQ, left, parseAddSub());
            } else if (match("<=")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.LTE, left, parseAddSub());
            } else if (match(">=")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.GTE, left, parseAddSub());
            } else if (match("<")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.LT, left, parseAddSub());
            } else if (match(">")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.GT, left, parseAddSub());
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseAddSub() {
        CEMExpression left = parseMulDiv();
        skipWhitespace();

        while (pos < length) {
            if (match("+")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.ADD, left, parseMulDiv());
            } else if (match("-")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.SUB, left, parseMulDiv());
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseMulDiv() {
        CEMExpression left = parseUnary();
        skipWhitespace();

        while (pos < length) {
            if (match("*")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.MUL, left, parseUnary());
            } else if (match("/")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.DIV, left, parseUnary());
            } else if (match("%")) {
                left = CEMBinaryOp.of(CEMBinaryOp.Op.MOD, left, parseUnary());
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseUnary() {
        skipWhitespace();

        if (match("-")) {
            return CEMUnaryOp.of(CEMUnaryOp.Op.NEG, parseUnary());
        }
        if (match("!")) {
            return CEMUnaryOp.of(CEMUnaryOp.Op.NOT, parseUnary());
        }
        if (match("+")) {
            return parseUnary();
        }

        return parsePrimary();
    }

    private CEMExpression parsePrimary() {
        skipWhitespace();

        if (pos >= length) {
            return new CEMLiteral(0);
        }

        char c = expression.charAt(pos);

        if (c == '(') {
            pos++;
            CEMExpression expr = parseExpression();
            skipWhitespace();
            if (pos < length && expression.charAt(pos) == ')') {
                pos++;
            }
            return expr;
        }

        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        if (Character.isLetter(c) || c == '_') {
            return parseIdentifierOrFunction();
        }

        return new CEMLiteral(0);
    }

    private CEMExpression parseNumber() {
        int start = pos;
        boolean hasDecimal = false;
        boolean hasExponent = false;

        while (pos < length) {
            char c = expression.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' && !hasDecimal && !hasExponent) {
                hasDecimal = true;
                pos++;
            } else if ((c == 'e' || c == 'E') && !hasExponent) {
                hasExponent = true;
                pos++;
                if (pos < length && (expression.charAt(pos) == '+' || expression.charAt(pos) == '-')) {
                    pos++;
                }
            } else {
                break;
            }
        }

        String numStr = expression.substring(start, pos);
        try {
            return new CEMLiteral(Double.parseDouble(numStr));
        } catch (NumberFormatException e) {
            return new CEMLiteral(0);
        }
    }

    private CEMExpression parseIdentifierOrFunction() {
        int start = pos;

        while (pos < length) {
            char c = expression.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ':') {
                pos++;
            } else {
                break;
            }
        }

        String identifier = expression.substring(start, pos);
        skipWhitespace();

        if (identifier.equals("nbt")) {
            pos++;
            int depth = 1;
            int argStart = pos;
            while (pos < length && depth > 0) {
                char ch = expression.charAt(pos);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                if (depth > 0) pos++;
            }
            String raw = expression.substring(argStart, pos);
            if (pos < length) pos++;
            int comma = raw.indexOf(',');
            return new CEMNbtQuery(
                    (comma >= 0 ? raw.substring(0, comma) : raw).trim(),
                    (comma >= 0 ? raw.substring(comma + 1) : "").trim());
        }

        switch (identifier) {
            case "pi": return new CEMLiteral(Math.PI);
            case "true": return new CEMLiteral(1);
            case "false": return new CEMLiteral(0);
            case "e": return new CEMLiteral(Math.E);
            case "nan": return new CEMLiteral(Double.NaN);
            default: break;
        }

        if (pos < length && expression.charAt(pos) == '(') {
            pos++;
            List<CEMExpression> args = new ArrayList<>();
            boolean print = identifier.equals("print") || identifier.equals("printb");

            int argCount = countArgs();
            int rawIndex = -1;
            if (print && argCount == 3) rawIndex = 0;
            else if (identifier.equals("catch") && argCount == 3) rawIndex = 2;

            for (int i = 0; i < argCount; i++) {
                if (i > 0) {
                    skipWhitespace();
                    if (pos < length && expression.charAt(pos) == ',') pos++;
                }
                skipWhitespace();
                int argStart = pos;
                if (i == rawIndex) {
                    args.add(new CEMLiteral(CEMFunction.label(scanRawArg())));
                } else {
                    args.add(parseExpression());
                    if (print && argCount == 1) {
                        args.add(0, new CEMLiteral(CEMFunction.label(expression.substring(argStart, pos).trim())));
                        args.add(1, new CEMLiteral(1));
                    }
                }
            }

            skipWhitespace();
            if (pos < length && expression.charAt(pos) == ')') {
                pos++;
            }

            return makeFunction(identifier, args);
        }

        return new CEMVariable(identifier);
    }

    private int countArgs() {
        int i = pos;
        while (i < length && Character.isWhitespace(expression.charAt(i))) i++;
        if (i >= length || expression.charAt(i) == ')') return 0;
        int depth = 0;
        int count = 1;
        for (; i < length; i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                if (depth == 0) break;
                depth--;
            } else if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    private String scanRawArg() {
        int start = pos;
        int depth = 0;
        while (pos < length) {
            char c = expression.charAt(pos);
            if (c == '(') depth++;
            else if (c == ')') {
                if (depth == 0) break;
                depth--;
            } else if (c == ',' && depth == 0) break;
            pos++;
        }
        return expression.substring(start, pos).trim();
    }

    private void skipWhitespace() {
        while (pos < length && Character.isWhitespace(expression.charAt(pos))) {
            pos++;
        }
    }

    private boolean match(String s) {
        skipWhitespace();
        if (expression.startsWith(s, pos)) {
            pos += s.length();
            return true;
        }
        return false;
    }

    private static CEMExpression makeFunction(String name, List<CEMExpression> args) {
        // torad/todeg appear ~200 times, lower them to a multiply.
        if (args.size() == 1 && (name.equals("torad") || name.equals("todeg"))) {
            double k = name.equals("torad") ? Math.PI / 180.0 : 180.0 / Math.PI;
            return CEMBinaryOp.of(CEMBinaryOp.Op.MUL, args.getFirst(), new CEMLiteral(k));
        }
        return CEMFunction.of(name, args);
    }
}