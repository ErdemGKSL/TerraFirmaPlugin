/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic run report: counts, skipped ids with reasons, unresolved refs, divergences. */
public final class Report {
    private final Map<String, Integer> counts = new TreeMap<>();
    private final Map<String, Integer> data = new TreeMap<>();
    private final Map<String, Integer> lang = new TreeMap<>();
    private final Set<String> missing = new TreeSet<>();
    private final Set<String> emitted = new TreeSet<>();
    private final Map<String, String> skipped = new TreeMap<>();
    private final Map<String, Set<String>> divergences = new TreeMap<>();
    private int ceStates = -1;

    void count(String key) {
        counts.merge(key, 1, Integer::sum);
    }

    void data(Map<String, Integer> perKind) {
        data.putAll(perKind);
    }

    void missing(String what) {
        missing.add(what);
    }

    void emitted(String id) {
        emitted.add(id);
    }

    Set<String> emittedIds() {
        return Set.copyOf(emitted);
    }

    void skipped(String id, String reason) {
        skipped.put(id, reason);
    }

    /** The CraftEngine block states of the emitted tree ({@link #countCeStates}); the installer sizes serverside-blocks. */
    void ceStates(int states) {
        ceStates = states;
    }

    /**
     * PAR-PROC-37: the server states every CraftEngine block entry under {@code craftengine/<pack>/configuration/} defines:
     * per block the product of its property sizes ({@code boolean} 2, {@code int} the range, {@code axis} 3 unless values
     * are listed, else the {@code values} list), 1 for a block without properties. Reads the text {@link Yaml} writes
     * (two-space indents, one list item per line). The installer used to size {@code block.serverside-blocks} from the
     * block count, which undercounts multi-state blocks.
     */
    public static int countCeStates(Map<String, byte[]> files) {
        int total = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("craftengine/") || !path.contains("/configuration/") || !path.endsWith(".yml")) {
                continue;
            }
            total += countCeStates(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return total;
    }

    public static int countCeStates(String text) {
        int total = 0;
        boolean inBlocks = false;
        long block = 0;
        int propsIndent = -1;
        String type = null;
        int values = 0;
        boolean inValues = false;
        for (String raw : text.split("\n")) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            String line = raw.strip();
            if (indent == 0) {
                if (propsIndent >= 0) {
                    block *= size(type, values);
                    propsIndent = -1;
                }
                total += (int) block;
                block = 0;
                inBlocks = line.matches("'?blocks(#[^:']*)?'?:");
                continue;
            }
            if (!inBlocks) {
                continue;
            }
            if (propsIndent >= 0 && indent <= propsIndent + 2 && type != null) {
                block *= size(type, values);
                type = null;
                values = 0;
                inValues = false;
            }
            if (propsIndent >= 0 && indent <= propsIndent) {
                propsIndent = -1;
            }
            if (indent == 2 && line.endsWith(":")) {
                total += (int) block;
                block = 1;
                continue;
            }
            if (propsIndent < 0 && line.equals("properties:")) {
                propsIndent = indent;
                continue;
            }
            if (propsIndent < 0) {
                continue;
            }
            if (indent == propsIndent + 2) {
                type = "";
                values = 0;
                inValues = false;
            } else if (indent == propsIndent + 4) {
                inValues = line.equals("values:");
                if (line.startsWith("type: ")) {
                    type = line.substring("type: ".length());
                } else if (line.startsWith("range: ")) {
                    String[] r = line.substring("range: ".length()).replace("'", "").split("~");
                    values = Integer.parseInt(r[1].strip()) - Integer.parseInt(r[0].strip()) + 1;
                }
            } else if (inValues && indent >= propsIndent + 4 && line.startsWith("- ")) {
                values++;
            }
        }
        if (propsIndent >= 0 && type != null) {
            block *= size(type, values);
        }
        return total + (int) block;
    }

    private static int size(String type, int values) {
        return switch (type) {
            case "boolean" -> 2;
            case "axis" -> values > 0 ? values : 3;
            default -> Math.max(1, values);
        };
    }

    void lang(String locale, int keys) {
        lang.put(locale, keys);
    }

    void divergence(String what, String id) {
        divergences.computeIfAbsent(what, k -> new TreeSet<>()).add(id);
    }

    public Set<String> missing() {
        return Set.copyOf(missing);
    }

    public int count(String key, int dflt) {
        return counts.getOrDefault(key, dflt);
    }

    public int emittedCount() {
        return emitted.size();
    }

    public int skippedCount() {
        return skipped.size();
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject c = new JsonObject();
        counts.forEach(c::addProperty);
        c.addProperty("ce_blocks", emitted.size());
        c.addProperty("ce_blocks_skipped", skipped.size());
        if (ceStates >= 0) {
            c.addProperty("ce_states", ceStates);
        }
        o.add("counts", c);
        JsonObject d = new JsonObject();
        data.forEach(d::addProperty);
        o.add("data_files", d);
        JsonObject l = new JsonObject();
        lang.forEach(l::addProperty);
        o.add("lang_keys", l);
        JsonArray m = new JsonArray();
        missing.forEach(m::add);
        o.add("unresolved_refs", m);
        JsonObject dv = new JsonObject();
        divergences.forEach((k, ids) -> dv.addProperty(k, ids.size() + " blocks"));
        o.add("divergences", dv);
        JsonObject s = new JsonObject();
        skipped.forEach(s::addProperty);
        o.add("ce_blocks_skipped", s);
        return o;
    }
}
