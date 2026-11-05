/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (every registration),
 *       src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java @ c3b4d9536 (enum Ore, Ore.Grade)
 */
package dev.tfp.datagen.material;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every item {@code TFCItems} registers, read from the pinned checkout's source.
 *
 * <p>Each {@code public static final} field of {@code TFCItems} is one {@link Registration}. Its name expression (the first
 * argument of {@code register(...)}) is a concatenation of string and char literals and enum names
 * ({@code x.name()}, {@code x.getSerializedName()}, {@code x.toString()} or a bare {@code x}); each lambda variable is bound to
 * the class of its {@code Helpers.mapOf(X.class, ...)} (or {@code FluidId.mapOf}). The ids are the TFC item models
 * ({@code models/item/<id>.json}) that match the expression with every enum variable restricted to that enum's constants.
 * TFC's {@code mapOf} filters ({@code Ore::isGem}, {@code type.has(metal)}...) are not evaluated: TFC writes an item model for
 * exactly the registered items, so the model set applies them. A literal registration without a model, an expression shape or
 * an enum this reader does not know fails the run, so a TFC change shows up here.
 */
public final class TfcItemRegistry {
    static final String ITEMS_SOURCE = "src/main/java/net/dries007/tfc/common/items/TFCItems.java";
    static final String JAVA = "src/main/java/net/dries007/tfc/";
    static final String MODELS = TfcCheckout.ASSETS + "models/item/";

    /** The source file of each enum a {@code mapOf} names (nested enums by their outer file). */
    static final Map<String, String> ENUM_SOURCES = Map.ofEntries(
        Map.entry("Ore", "common/blocks/rock/Ore.java"),
        Map.entry("Ore.Grade", "common/blocks/rock/Ore.java"),
        Map.entry("RockCategory", "common/blocks/rock/RockCategory.java"),
        Map.entry("RockCategory.ItemType", "common/blocks/rock/RockCategory.java"),
        Map.entry("Rock", "common/blocks/rock/Rock.java"),
        Map.entry("Metal", "util/Metal.java"),
        Map.entry("Metal.ItemType", "util/Metal.java"),
        Map.entry("Wood", "common/blocks/wood/Wood.java"),
        Map.entry("Food", "common/items/Food.java"),
        Map.entry("Nutrient", "common/component/food/Nutrient.java"),
        Map.entry("Crop", "common/blocks/crop/Crop.java"),
        Map.entry("Coral", "common/blocks/plant/coral/Coral.java"),
        Map.entry("HideItemType", "common/items/HideItemType.java"),
        Map.entry("HideItemType.Size", "common/items/HideItemType.java"),
        Map.entry("Powder", "common/items/Powder.java"),
        Map.entry("Fish", "common/entities/aquatic/Fish.java"));

    /** Vanilla {@code DyeColor} serialized names (not in the checkout). */
    static final List<String> DYE_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /**
     * A variable bound to any fluid name ({@code FluidId} is not an enum: one segment or {@code metal/<metal>}), or, for the
     * spawn-egg candidates, any single segment.
     */
    private static final String ANY = "*";

    /**
     * One {@code TFCItems} field.
     *
     * @param field   the field name, e.g. {@code GEM_SAW}
     * @param factory the text after the name argument of {@code register(...)} ({@code ""} for {@code register(name)}), or
     *                {@code "spawn_egg"} for {@code registerSpawnEgg}
     * @param ids     the item ids (TFC paths) it registers, sorted
     */
    public record Registration(String field, String factory, List<String> ids) {
        /** Whether the item is a plain {@code new Item(...)}: {@code register(name)}, {@code register(name, Properties)}. */
        public boolean plain() {
            return factory.isEmpty() || factory.startsWith("new Properties()") || factory.startsWith("() -> new Item(");
        }
    }

    private static final Pattern DECLARATION = Pattern.compile("public static final [^=]+?\\b([A-Z][A-Z0-9_]*)\\s*=");
    private static final Pattern MAP_OF = Pattern.compile("(?:Helpers\\.mapOf\\(([A-Za-z.]+)\\.class|(FluidId)\\.mapOf\\()");
    private static final Pattern LAMBDA = Pattern.compile("(\\w+)\\s*->");
    private static final Pattern CALL = Pattern.compile("\\b(register|registerSpawnEgg)\\(");
    private static final Pattern TERM = Pattern.compile(
        "\"([a-z0-9_/]*)\"|'([a-z0-9_/])'|(\\w+)(?:\\.(?:name|getSerializedName|toString)\\(\\))?");

    private TfcItemRegistry() {
    }

    /** Every registration of the checkout's {@code TFCItems} in source order. */
    public static List<Registration> registrations(TfcCheckout tfc) {
        String src = tfc.readString(ITEMS_SOURCE).replace("\r\n", "\n");
        int end = src.indexOf("private static <T extends Mob> ItemId registerSpawnEgg");
        if (end < 0 || !src.contains("ITEMS.register(name.toLowerCase(Locale.ROOT)")
            || !src.contains("register(\"spawn_egg/\" + entity.getId().getPath()")) {
            throw new DatagenException(ITEMS_SOURCE + ": the register helpers changed; update TfcItemRegistry");
        }
        src = src.substring(0, end);
        Set<String> models = models(tfc);
        Map<String, List<String>> enums = new LinkedHashMap<>();
        List<Registration> out = new ArrayList<>();
        Set<Integer> wildcard = new TreeSet<>();
        Matcher d = DECLARATION.matcher(src);
        List<int[]> spans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (d.find()) {
            if (!spans.isEmpty()) {
                spans.getLast()[1] = d.start();
            }
            spans.add(new int[] {d.end(), src.length()});
            names.add(d.group(1));
        }
        for (int i = 0; i < spans.size(); i++) {
            String body = src.substring(spans.get(i)[0], spans.get(i)[1]);
            Matcher call = CALL.matcher(body);
            if (!call.find()) {
                continue; // ITEMS itself
            }
            Map<String, String> vars = bindings(body.substring(0, call.start()));
            String field = names.get(i);
            if (call.group(1).equals("registerSpawnEgg")) {
                Set<String> eggs = expand(List.of("spawn_egg/", ANY), models);
                List<String> fish = vars.isEmpty() ? List.of() : constants(tfc, "Fish", enums);
                out.add(new Registration(field, "spawn_egg", List.copyOf(spawnEggs(field, eggs, vars, body, fish))));
                continue;
            }
            String args = arguments(body, call.end(), field);
            int comma = topLevelComma(args);
            String name = (comma < 0 ? args : args.substring(0, comma)).strip();
            String factory = comma < 0 ? "" : args.substring(comma + 1).strip();
            List<String> pattern = new ArrayList<>();
            for (String term : name.split("\\+")) {
                String t = term.strip();
                Matcher m = TERM.matcher(t);
                if (!m.matches()) {
                    throw new DatagenException(ITEMS_SOURCE + " " + field + ": unknown name term " + t);
                }
                if (m.group(1) != null || m.group(2) != null) {
                    pattern.add(m.group(1) != null ? m.group(1) : m.group(2));
                } else {
                    String cls = vars.get(m.group(3));
                    if (cls == null) {
                        throw new DatagenException(ITEMS_SOURCE + " " + field + ": unbound name variable " + m.group(3));
                    }
                    pattern.add(cls.equals("FluidId") ? ANY : "#" + cls);
                }
            }
            List<String> resolved = new ArrayList<>();
            for (String p : pattern) {
                resolved.add(p.startsWith("#") ? String.join("|", constants(tfc, p.substring(1), enums)) : p);
            }
            Set<String> ids;
            if (vars.isEmpty()) {
                String id = String.join("", pattern).toLowerCase(Locale.ROOT);
                if (!models.contains(id)) {
                    throw new DatagenException(ITEMS_SOURCE + " " + field + " registers " + id + " but TFC has no item model for it");
                }
                ids = Set.of(id);
            } else {
                ids = expandEnums(pattern, resolved, models);
                if (ids.isEmpty()) {
                    throw new DatagenException(ITEMS_SOURCE + " " + field + ": no item model matches " + String.join(" + ", pattern));
                }
            }
            if (pattern.contains(ANY)) {
                wildcard.add(out.size());
            }
            out.add(new Registration(field, factory, List.copyOf(new TreeSet<>(ids))));
        }
        if (out.isEmpty()) {
            throw new DatagenException(ITEMS_SOURCE + " has no registrations");
        }
        // A FluidId map matches any segment: it keeps only the models no exact registration names (bucket/cod is COD_BUCKET).
        Set<String> exact = new TreeSet<>();
        for (int i = 0; i < out.size(); i++) {
            if (!wildcard.contains(i)) {
                exact.addAll(out.get(i).ids());
            }
        }
        for (int i : wildcard) {
            Registration r = out.get(i);
            List<String> kept = r.ids().stream().filter(id -> !exact.contains(id)).toList();
            if (kept.isEmpty()) {
                throw new DatagenException(ITEMS_SOURCE + " " + r.field() + ": every model it matches is registered elsewhere");
            }
            out.set(i, new Registration(r.field(), r.factory(), kept));
        }
        return out;
    }

    /** All registered ids in registration order (duplicates are an error). */
    public static List<String> ids(List<Registration> registrations) {
        Set<String> out = new LinkedHashSet<>();
        for (Registration r : registrations) {
            for (String id : r.ids()) {
                if (!out.add(id)) {
                    throw new DatagenException(ITEMS_SOURCE + ": " + id + " matched by two registrations (" + r.field() + ")");
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * The spawn eggs of one field: a literal {@code registerSpawnEgg(TFCEntities.X)} is one egg, a {@code mapOf(Fish.class, ...)}
     * one per fish. The models cannot say which egg belongs to which field, so the field takes the egg model named after the
     * entity constant ({@code COD_EGG} → {@code cod}) or, for the fish map, the fish constants.
     */
    private static Set<String> spawnEggs(String field, Set<String> eggModels, Map<String, String> vars, String body,
        List<String> fish) {
        Set<String> out = new TreeSet<>();
        if (vars.isEmpty()) {
            Matcher m = Pattern.compile("registerSpawnEgg\\(TFCEntities\\.([A-Z_]+)\\)").matcher(body);
            if (!m.find()) {
                throw new DatagenException(ITEMS_SOURCE + " " + field + ": unknown spawn egg shape");
            }
            String id = "spawn_egg/" + m.group(1).toLowerCase(Locale.ROOT);
            if (!eggModels.contains(id)) {
                throw new DatagenException(ITEMS_SOURCE + " " + field + ": no spawn egg model " + id);
            }
            out.add(id);
            return out;
        }
        if (!vars.containsValue("Fish") || vars.size() != 1) {
            throw new DatagenException(ITEMS_SOURCE + " " + field + ": unknown spawn egg map " + vars);
        }
        for (String f : fish) {
            String id = "spawn_egg/" + f;
            if (!eggModels.contains(id)) {
                throw new DatagenException(ITEMS_SOURCE + " " + field + ": no spawn egg model " + id);
            }
            out.add(id);
        }
        return out;
    }

    /** {@code var -> class} for the lambdas of the {@code mapOf} calls before the register call. */
    private static Map<String, String> bindings(String head) {
        Map<String, String> vars = new LinkedHashMap<>();
        Matcher m = MAP_OF.matcher(head);
        List<int[]> starts = new ArrayList<>();
        List<String> classes = new ArrayList<>();
        while (m.find()) {
            starts.add(new int[] {m.end()});
            classes.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i)[0];
            int to = i + 1 < starts.size() ? head.lastIndexOf("Helpers.mapOf", starts.get(i + 1)[0]) : head.length();
            Matcher l = LAMBDA.matcher(head.substring(from, to));
            String last = null;
            while (l.find()) {
                last = l.group(1);
            }
            if (last == null) {
                throw new DatagenException(ITEMS_SOURCE + ": mapOf(" + classes.get(i) + ") without a lambda");
            }
            vars.put(last, classes.get(i));
        }
        return vars;
    }

    /** The text inside {@code register(...)} starting at {@code from} (just after the open paren). */
    private static String arguments(String body, int from, String field) {
        int depth = 1;
        boolean str = false;
        for (int i = from; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                str = !str;
            } else if (!str && c == '(') {
                depth++;
            } else if (!str && c == ')' && --depth == 0) {
                return body.substring(from, i);
            }
        }
        throw new DatagenException(ITEMS_SOURCE + " " + field + ": unbalanced register(...)");
    }

    private static int topLevelComma(String args) {
        int depth = 0;
        boolean str = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                str = !str;
            } else if (!str && (c == '(' || c == '<')) {
                depth++;
            } else if (!str && (c == ')' || c == '>')) {
                depth--;
            } else if (!str && c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The lower-case constants of an enum ({@code Ore}, {@code Ore.Grade}, vanilla {@code DyeColor}). */
    static List<String> constants(TfcCheckout tfc, String cls, Map<String, List<String>> cache) {
        if (cls.equals("DyeColor")) {
            return DYE_COLORS;
        }
        List<String> cached = cache.get(cls);
        if (cached != null) {
            return cached;
        }
        String file = ENUM_SOURCES.get(cls);
        if (file == null) {
            throw new DatagenException(ITEMS_SOURCE + ": mapOf over " + cls + ", which TfcItemRegistry does not read");
        }
        String src = tfc.readString(JAVA + file).replace("\r\n", "\n");
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        Matcher m = Pattern.compile("\\benum " + simple + "\\b[^{]*\\{").matcher(src);
        if (!m.find()) {
            throw new DatagenException(JAVA + file + ": enum " + simple + " not found");
        }
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = m.end(); i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : 0;
            if (lineComment) {
                lineComment = c != '\n';
                continue;
            }
            if (blockComment) {
                if (c == '*' && n == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                lineComment = true;
                continue;
            }
            if (c == '/' && n == '*') {
                blockComment = true;
                continue;
            }
            if (c == '(' || c == '{') {
                depth++;
                continue;
            } else if (c == ')' || c == '}') {
                if (depth == 0) {
                    addConstant(out, cur);
                    break;
                }
                depth--;
                continue;
            } else if (depth == 0 && (c == ',' || c == ';')) {
                addConstant(out, cur);
                cur.setLength(0);
                if (c == ';') {
                    break;
                }
                continue;
            }
            if (depth == 0) {
                cur.append(c);
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException(JAVA + file + ": enum " + simple + " has no constants");
        }
        cache.put(cls, List.copyOf(out));
        return cache.get(cls);
    }

    private static void addConstant(List<String> out, StringBuilder cur) {
        String t = cur.toString().strip();
        if (t.startsWith("@")) {
            t = t.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
        }
        if (!t.isEmpty()) {
            if (!t.matches("[A-Z][A-Z0-9_]*")) {
                throw new DatagenException("unexpected enum constant " + t);
            }
            out.add(t.toLowerCase(Locale.ROOT));
        }
    }

    private static Set<String> expand(List<String> literalOrAny, Set<String> models) {
        StringBuilder re = new StringBuilder();
        for (String p : literalOrAny) {
            re.append(p.equals(ANY) ? "[a-z0-9_]+" : Pattern.quote(p));
        }
        Pattern rx = Pattern.compile(re.toString());
        Set<String> out = new TreeSet<>();
        for (String m : models) {
            if (rx.matcher(m).matches()) {
                out.add(m);
            }
        }
        return out;
    }

    private static Set<String> expandEnums(List<String> pattern, List<String> resolved, Set<String> models) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < pattern.size(); i++) {
            String p = pattern.get(i);
            if (p.equals(ANY)) {
                re.append("(?:metal/)?[a-z0-9_]+"); // FluidId names: salt_water, <simple>, <dye>_dye, metal/<metal>
            } else if (p.startsWith("#")) {
                re.append("(?:").append(resolved.get(i)).append(")");
            } else {
                re.append(Pattern.quote(p.toLowerCase(Locale.ROOT)));
            }
        }
        Pattern rx = Pattern.compile(re.toString());
        Set<String> out = new TreeSet<>();
        for (String m : models) {
            if (rx.matcher(m).matches()) {
                out.add(m);
            }
        }
        return out;
    }

    /** TFC's item model ids ({@code models/item/**.json}, path without extension). */
    static Set<String> models(TfcCheckout tfc) {
        Set<String> out = new TreeSet<>();
        for (String f : tfc.list(MODELS)) {
            if (f.endsWith(".json")) {
                out.add(f.substring(MODELS.length(), f.length() - ".json".length()));
            }
        }
        return out;
    }
}
