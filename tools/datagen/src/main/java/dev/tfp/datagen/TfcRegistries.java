/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TFC registry ids that exist only in TFC's Java code (fluids, entity types, food traits), read from the pinned
 * checkout's sources. The parsers recognise exactly the registration shapes used at the pinned commit and fail on
 * anything else, so a TFC update that changes them stops datagen instead of silently dropping ids.
 */
final class TfcRegistries {
    static final String JAVA = "src/main/java/";
    static final String PACKAGE = "net/dries007/tfc/";
    static final String FLUIDS = JAVA + PACKAGE + "common/fluids/TFCFluids.java";
    static final String ENTITIES = JAVA + PACKAGE + "common/entities/TFCEntities.java";
    static final String FOOD_TRAITS = JAVA + PACKAGE + "common/component/food/FoodTraits.java";

    private TfcRegistries() {
    }

    /**
     * Fluid paths (without namespace). Every TFC flowing fluid has a liquid block ({@code blockstates/fluid/<name>})
     * and is registered by {@code TFCFluids.register(name, ...)} as a source {@code name} plus a flowing fluid named
     * {@code flowing_<name>} ({@code metal/foo} → {@code metal/flowing_foo}). Fluids registered directly with
     * {@code FLUIDS.register("x", ...)} (river water) have no flowing variant.
     */
    static SortedSet<String> fluids(TfcCheckout tfc) {
        String src = tfc.readString(FLUIDS);
        if (!src.contains("\"/flowing_\"") || !src.contains("\"flowing_\" + name")) {
            throw new DatagenException(FLUIDS + ": the flowing-fluid naming rule changed; update TfcRegistries.fluids");
        }
        SortedSet<String> direct = new TreeSet<>();
        Matcher m = Pattern.compile("FLUIDS\\.register\\(\"([a-z0-9_/]+)\"").matcher(src);
        while (m.find()) {
            direct.add(m.group(1));
        }
        String dir = TfcCheckout.ASSETS + "blockstates/fluid/";
        SortedSet<String> out = new TreeSet<>(direct);
        for (String f : tfc.list(dir)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            tfc.read(f);
            String name = f.substring(dir.length(), f.length() - ".json".length());
            out.add(name);
            if (!direct.contains(name)) {
                int i = name.lastIndexOf('/');
                out.add(i < 0 ? "flowing_" + name : name.substring(0, i) + "/flowing_" + name.substring(i + 1));
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException("no TFC fluids found (" + dir + ")");
        }
        return out;
    }

    private static final Pattern DECLARATION = Pattern.compile("public static final ");
    private static final Pattern MAP_OF = Pattern.compile("Helpers\\.mapOf\\((\\w+)\\.class,\\s*(\\w+)\\s*->");
    private static final Pattern REGISTER = Pattern.compile("\\bregister\\(\\s*([^,]+?)\\s*,\\s*EntityType\\.Builder");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z0-9_/]+)\"");
    private static final Pattern PREFIXED = Pattern.compile("(?:\"([a-z0-9_/]*)\"\\s*\\+\\s*)?(\\w+)\\.(?:name|getSerializedName)\\(\\)");

    /** Entity type paths from {@code TFCEntities}: literal names and names built from an enum in {@code Helpers.mapOf}. */
    static SortedSet<String> entityTypes(TfcCheckout tfc) {
        String src = tfc.readString(ENTITIES);
        if (!src.contains("name.toLowerCase(Locale.ROOT)")) {
            throw new DatagenException(ENTITIES + ": register() no longer lower-cases names; update TfcRegistries");
        }
        SortedSet<String> out = new TreeSet<>();
        for (String decl : DECLARATION.split(src)) {
            Matcher map = MAP_OF.matcher(decl);
            String enumType = map.find() ? map.group(1) : null;
            String var = enumType == null ? null : map.group(2);
            Matcher r = REGISTER.matcher(decl);
            while (r.find()) {
                String expr = r.group(1);
                Matcher lit = LITERAL.matcher(expr);
                Matcher pre = PREFIXED.matcher(expr);
                if (expr.matches("String\\s+\\w+")) {
                    continue; // the register(String name, EntityType.Builder<E> builder) helper itself
                } else if (lit.matches()) {
                    out.add(lit.group(1));
                } else if (pre.matches() && pre.group(2).equals(var)) {
                    String prefix = pre.group(1) == null ? "" : pre.group(1);
                    for (String constant : enumConstants(tfc, src, ENTITIES, enumType)) {
                        out.add(prefix + constant.toLowerCase(Locale.ROOT));
                    }
                } else {
                    throw new DatagenException(ENTITIES + ": unrecognised entity registration name '" + expr
                        + "'; update TfcRegistries.entityTypes");
                }
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException("no TFC entity types found in " + ENTITIES);
        }
        return out;
    }

    /** Food trait paths from {@code FoodTraits.register("name", ...)}. */
    static SortedSet<String> foodTraits(TfcCheckout tfc) {
        String src = tfc.readString(FOOD_TRAITS);
        SortedSet<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("= register\\(\"([a-z0-9_/]+)\"").matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
        if (out.isEmpty()) {
            throw new DatagenException("no TFC food traits found in " + FOOD_TRAITS);
        }
        return out;
    }

    /**
     * Constants of enum {@code type}, located through {@code user}'s imports (or its package). The enum's ids are
     * {@code name().toLowerCase(ROOT)}, which the caller applies; checked here for {@code getSerializedName}.
     */
    static List<String> enumConstants(TfcCheckout tfc, String userSource, String userPath, String type) {
        Matcher imp = Pattern.compile("import net\\.dries007\\.tfc\\.([\\w.]+)\\." + type + ";").matcher(userSource);
        String path = imp.find()
            ? JAVA + PACKAGE + imp.group(1).replace('.', '/') + "/" + type + ".java"
            : userPath.substring(0, userPath.lastIndexOf('/') + 1) + type + ".java";
        if (!tfc.exists(path)) {
            throw new DatagenException("cannot find enum " + type + " (" + path + ")");
        }
        String src = tfc.readString(path);
        if (src.contains("getSerializedName") && !src.contains("name().toLowerCase(Locale.ROOT)")) {
            throw new DatagenException(path + ": serialized names are not name().toLowerCase(); update TfcRegistries");
        }
        Matcher head = Pattern.compile("enum " + type + "\\b[^{]*\\{").matcher(src);
        if (!head.find()) {
            throw new DatagenException(path + " does not declare enum " + type);
        }
        int end = src.indexOf(';', head.end());
        String body = src.substring(head.end(), end < 0 ? src.length() : end)
            .replaceAll("//[^\\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder token = new StringBuilder();
        boolean atStart = true;
        for (char c : body.toCharArray()) {
            if (depth == 0 && atStart) {
                if (Character.isJavaIdentifierPart(c)) {
                    token.append(c);
                    continue;
                } else if (!token.isEmpty()) {
                    out.add(token.toString());
                    token.setLength(0);
                    atStart = false;
                }
            }
            if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (depth == 0 && c == ',') {
                atStart = true;
            }
        }
        if (!token.isEmpty()) {
            out.add(token.toString());
        }
        if (out.isEmpty()) {
            throw new DatagenException(path + ": enum " + type + " has no constants");
        }
        return out;
    }
}
