package com.slize.datarium.client.cem.expr;

import java.util.ArrayList;
import java.util.List;

public class CEMExpressionParser {
    private final String expression;
    private int pos;
    private int length;

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
            CEMExpression result = parser.parseExpression();
            return result;
        } catch (Exception e) {
            System.err.println("[CEM] Failed to parse expression: " + expression);
            e.printStackTrace();
            return new CEMLiteral(0);
        }
    }

    private CEMExpression parseExpression() {
        return parseOr();
    }

    private CEMExpression parseOr() {
        CEMExpression left = parseAnd();
        skipWhitespace();

        while (pos < length) {
            if (match("||")) {
                CEMExpression right = parseAnd();
                left = new CEMBinaryOp(CEMBinaryOp.Op.OR, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseAnd() {
        CEMExpression left = parseComparison();
        skipWhitespace();

        while (pos < length) {
            if (match("&&")) {
                CEMExpression right = parseComparison();
                left = new CEMBinaryOp(CEMBinaryOp.Op.AND, left, right);
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
                left = new CEMBinaryOp(CEMBinaryOp.Op.EQ, left, parseAddSub());
            } else if (match("!=")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.NEQ, left, parseAddSub());
            } else if (match("<=")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.LTE, left, parseAddSub());
            } else if (match(">=")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.GTE, left, parseAddSub());
            } else if (match("<")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.LT, left, parseAddSub());
            } else if (match(">")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.GT, left, parseAddSub());
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
                left = new CEMBinaryOp(CEMBinaryOp.Op.ADD, left, parseMulDiv());
            } else if (match("-")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.SUB, left, parseMulDiv());
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
                left = new CEMBinaryOp(CEMBinaryOp.Op.MUL, left, parseUnary());
            } else if (match("/")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.DIV, left, parseUnary());
            } else if (match("%")) {
                left = new CEMBinaryOp(CEMBinaryOp.Op.MOD, left, parseUnary());
            } else {
                break;
            }
        }
        return left;
    }

    private CEMExpression parseUnary() {
        skipWhitespace();

        if (match("-")) {
            return new CEMUnaryOp(CEMUnaryOp.Op.NEG, parseUnary());
        }
        if (match("!")) {
            return new CEMUnaryOp(CEMUnaryOp.Op.NOT, parseUnary());
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

        // Parentheses
        if (c == '(') {
            pos++;
            CEMExpression expr = parseExpression();
            skipWhitespace();
            if (pos < length && expression.charAt(pos) == ')') {
                pos++;
            }
            return expr;
        }

        // Number
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        // Identifier (variable or function)
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
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                pos++;
            } else {
                break;
            }
        }

        String identifier = expression.substring(start, pos);
        skipWhitespace();

        // Check for function call
        if (pos < length && expression.charAt(pos) == '(') {
            pos++; // consume '('
            List<CEMExpression> args = new ArrayList<>();

            skipWhitespace();
            if (pos < length && expression.charAt(pos) != ')') {
                args.add(parseExpression());

                skipWhitespace();
                while (pos < length && expression.charAt(pos) == ',') {
                    pos++; // consume ','
                    args.add(parseExpression());
                    skipWhitespace();
                }
            }

            if (pos < length && expression.charAt(pos) == ')') {
                pos++; // consume ')'
            }

            return new CEMFunction(identifier, args);
        }

        // It's a variable
        return new CEMVariable(identifier);
    }

    private void skipWhitespace() {
        while (pos < length && Character.isWhitespace(expression.charAt(pos))) {
            pos++;
        }
    }

    private boolean match(String s) {
        skipWhitespace();
        if (pos + s.length() <= length && expression.substring(pos, pos + s.length()).equals(s)) {
            // Make sure we're not matching a prefix of a longer operator
            if (s.length() == 1 && (s.equals("<") || s.equals(">") || s.equals("="))) {
                if (pos + 1 < length && expression.charAt(pos + 1) == '=') {
                    return false;
                }
            }
            pos += s.length();
            return true;
        }
        return false;
    }
}