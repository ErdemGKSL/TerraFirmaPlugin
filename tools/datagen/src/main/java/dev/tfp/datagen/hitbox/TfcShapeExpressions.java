/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates the VoxelShape constants of TFC's block classes from their Java source, so the shape table is read from
 * the pinned checkout instead of being copied by hand.
 *
 * <p>Understood: {@code box(...)} and {@code Block.box(...)} (pixels), {@code Shapes.box(...)} (blocks),
 * {@code Shapes.or(a, b, ...)}, {@code Shapes.join(a, b, BooleanOp.ONLY_FIRST)}, {@code Shapes.empty()},
 * {@code Shapes.block()}, array initialisers ({@code new VoxelShape[] {...}} or {@code {...}}) with an index, and
 * references to other {@code VoxelShape} fields or local variables: unqualified ones are looked up in the given files in
 * order (a subclass, then its superclasses), {@code Class.FIELD} in the file registered for {@code Class}. Anything else
 * (a loop, arithmetic, a method call) is an error: such shapes get a small ported evaluator in {@link TfcShapes}.
 */
final class TfcShapeExpressions {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?[dDfF]?");
    private static final Pattern TOKEN = Pattern.compile("\\s*(new\\b|[A-Za-z_][A-Za-z0-9_.]*|-?\\d+(?:\\.\\d+)?[dDfF]?|[()\\[\\]{},])");

    private final TfcCheckout tfc;
    private final Map<String, String> classFiles;
    private final Map<String, String> stripped = new HashMap<>();

    /** @param classFiles simple class name to checkout path, for {@code Class.FIELD} references */
    TfcShapeExpressions(TfcCheckout tfc, Map<String, String> classFiles) {
        this.tfc = tfc;
        this.classFiles = classFiles;
    }

    /** The source of {@code file} without comments; fails when the checkout has no such file. */
    String source(String file) {
        return stripped.computeIfAbsent(file, f -> {
            if (!tfc.exists(f)) {
                throw new DatagenException("TFC checkout has no " + f);
            }
            return stripComments(tfc.readString(f));
        });
    }

    /** Evaluates {@code expr} (a VoxelShape expression) with unqualified names looked up in {@code files} in order. */
    Shape shape(List<String> files, String expr) {
        Object v = new Parser(files, expr).parseAll();
        if (!(v instanceof Shape s)) {
            throw new DatagenException("TFC shape expression " + expr + " in " + files + " is not a VoxelShape");
        }
        return s;
    }

    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        boolean inString = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    out.append(s.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
                i++;
            } else if (s.startsWith("//", i)) {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The initialiser text of a {@code VoxelShape} (or {@code VoxelShape[]}) field or local named {@code name}. */
    private String definition(String file, String name) {
        String src = source(file);
        Matcher m = Pattern.compile("VoxelShape(\\[\\])?\\s+" + Pattern.quote(name) + "\\s*=").matcher(src);
        if (!m.find()) {
            return null;
        }
        int depth = 0;
        for (int i = m.end(); i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return src.substring(m.end(), i).trim();
            }
        }
        throw new DatagenException(file + ": no end of the definition of " + name);
    }

    private final class Parser {
        private final List<String> files;
        private final List<String> tokens = new ArrayList<>();
        private final String text;
        private int pos;

        Parser(List<String> files, String text) {
            this.files = files;
            this.text = text;
            Matcher m = TOKEN.matcher(text);
            int at = 0;
            while (at < text.length()) {
                if (text.substring(at).isBlank()) {
                    break;
                }
                if (!m.find(at) || m.start() != at) {
                    throw new DatagenException("cannot read the TFC shape expression " + text + " at '" + text.substring(at) + "'");
                }
                tokens.add(m.group(1));
                at = m.end();
            }
        }

        Object parseAll() {
            Object v = expr();
            if (pos != tokens.size()) {
                throw error("unexpected " + tokens.get(pos));
            }
            return v;
        }

        private DatagenException error(String what) {
            return new DatagenException("TFC shape expression " + text + " (" + files + "): " + what);
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : "";
        }

        private String next() {
            if (pos >= tokens.size()) {
                throw error("ends early");
            }
            return tokens.get(pos++);
        }

        private void expect(String t) {
            String n = next();
            if (!n.equals(t)) {
                throw error("expected " + t + ", found " + n);
            }
        }

        private Object expr() {
            Object v = primary();
            while (peek().equals("[")) {
                next();
                Object index = expr();
                expect("]");
                if (!(v instanceof List<?> list) || !(index instanceof Double d) || d != Math.rint(d)) {
                    throw error("only an array of shapes can be indexed, by an integer");
                }
                int i = (int) (double) d;
                if (i < 0 || i >= list.size()) {
                    throw error("index " + i + " outside the array of " + list.size());
                }
                v = list.get(i);
            }
            return v;
        }

        private Object primary() {
            String t = next();
            if (NUMBER.matcher(t).matches()) {
                return Double.parseDouble(t.replaceAll("[dDfF]$", ""));
            }
            if (t.equals("new")) {
                expect("VoxelShape");
                expect("[");
                if (!peek().equals("]")) {
                    throw error("a sized array has no literal elements");
                }
                expect("]");
                expect("{");
                return list();
            }
            if (t.equals("{")) {
                return list();
            }
            if (peek().equals("(")) {
                next();
                List<Object> args = new ArrayList<>();
                if (!peek().equals(")")) {
                    args.add(expr());
                    while (peek().equals(",")) {
                        next();
                        args.add(expr());
                    }
                }
                expect(")");
                return call(t, args);
            }
            if (t.startsWith("BooleanOp.")) {
                return t;
            }
            return reference(t);
        }

        private List<Object> list() {
            List<Object> out = new ArrayList<>();
            if (!peek().equals("}")) {
                out.add(expr());
                while (peek().equals(",")) {
                    next();
                    if (peek().equals("}")) {
                        break;
                    }
                    out.add(expr());
                }
            }
            expect("}");
            return out;
        }

        private Object call(String name, List<Object> args) {
            switch (name) {
                case "box", "Block.box" -> {
                    double[] d = numbers(name, args, 6);
                    return Shape.box(d[0], d[1], d[2], d[3], d[4], d[5]);
                }
                case "Shapes.box" -> {
                    double[] d = numbers(name, args, 6);
                    return Shape.box(d[0] * 16, d[1] * 16, d[2] * 16, d[3] * 16, d[4] * 16, d[5] * 16);
                }
                case "Shapes.empty" -> {
                    numbers(name, args, 0);
                    return Shape.EMPTY;
                }
                case "Shapes.block" -> {
                    numbers(name, args, 0);
                    return Shape.FULL;
                }
                case "Shapes.or" -> {
                    Shape s = Shape.EMPTY;
                    for (Object a : args) {
                        if (!(a instanceof Shape sh)) {
                            throw error("Shapes.or takes shapes");
                        }
                        s = s.union(sh);
                    }
                    return s;
                }
                case "Shapes.join" -> {
                    if (args.size() != 3 || !(args.get(0) instanceof Shape a) || !(args.get(1) instanceof Shape b)
                        || !"BooleanOp.ONLY_FIRST".equals(args.get(2))) {
                        throw error("only Shapes.join(a, b, BooleanOp.ONLY_FIRST) is understood");
                    }
                    return a.minus(b);
                }
                default -> throw error("unknown call " + name + ": port this shape in TfcShapes");
            }
        }

        private double[] numbers(String name, List<Object> args, int n) {
            if (args.size() != n) {
                throw error(name + " takes " + n + " arguments");
            }
            double[] d = new double[n];
            for (int i = 0; i < n; i++) {
                if (!(args.get(i) instanceof Double v)) {
                    throw error(name + " takes numbers");
                }
                d[i] = v;
            }
            return d;
        }

        private Object reference(String name) {
            int dot = name.lastIndexOf('.');
            List<String> where;
            String field;
            if (dot > 0) {
                String cls = name.substring(0, dot);
                String file = classFiles.get(cls);
                if (file == null) {
                    throw error("no source file registered for class " + cls);
                }
                where = List.of(file);
                field = name.substring(dot + 1);
            } else {
                where = files;
                field = name;
            }
            for (int i = 0; i < where.size(); i++) {
                String def = definition(where.get(i), field);
                if (def != null) {
                    // names inside a definition resolve from its own file on (a superclass never sees a subclass)
                    return new Parser(where.subList(i, where.size()), def).parseAll();
                }
            }
            throw error("no VoxelShape " + field + " in " + where);
        }
    }
}
