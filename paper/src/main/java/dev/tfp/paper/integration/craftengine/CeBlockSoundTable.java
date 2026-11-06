/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration.craftengine;

import dev.tfp.paper.config.Yamls;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The hit and break sound of every CraftEngine block of ours, with the volume and pitch TFC's {@code SoundType} plays them
 * at (PAR-INFRA-33), read from the CraftEngine configuration files our installer (and a test pack such as spike S2)
 * put in {@code plugins/CraftEngine/resources/<pack>/}. The packet rewrite of {@code dev.tfp.paper.blocksound} needs
 * them because CraftEngine 26.9.2 sends two of a block's sounds at fixed values that ignore the configuration: the
 * mining-loop hit (0.5/0.5) and the break sound it makes of the vanilla level event 2001 (1.0/0.8).
 *
 * <p>Where the values come from, per block entry (the shapes {@code tools/datagen} writes):
 * <ul>
 *   <li>{@code settings.sounds.<event>}: a plain id or {@code {id, volume, pitch}} (literal devices, {@code DeviceSound});</li>
 *   <li>{@code arguments.<prefix>_sound_<event>}: a plain id or {@code {id, volume, pitch}} (templated devices,
 *       {@code tfp:block/device}), with optional {@code <prefix>_sound_<event>_volume} / {@code _pitch} beside it
 *       (renderer cubes, {@code tfp:block/rendered_cube}).</li>
 * </ul>
 * A plain id plays at a {@code SoundType} of (1.0, 1.0): datagen writes plain ids only for those types (vanilla STONE,
 * WOOD, NETHER_WART, every TFC type), so hit 0.25/0.5 and break 1.0/0.8. Only block ids whose namespace starts with
 * {@code tfp} are read; other packs' blocks are never rewritten.
 *
 * <p>Blocks whose sounds come from a template alone (rock, soil, sand, ores: {@code settings: {template:
 * tfp:settings/rock, arguments: ...}}, which pulls in {@code tfp:settings/sound} with {@code sound_block_type}) are
 * resolved through the {@code templates} sections of all files (PAR-INFRA-35): a small subset of CraftEngine's template
 * expansion that follows {@code template} (one id or a list), {@code arguments} (the caller's argument wins over the
 * template's own), {@code overrides} and {@code merges}, and substitutes {@code ${name}} / {@code ${name:-default}}.
 * A sound whose id still holds a placeholder after that is ignored. Every TFC terrain {@code SoundType} (STONE, GRAVEL,
 * SAND, GRASS) is (1.0, 1.0), so the resolved plain ids play at hit 0.25/0.5 and break 1.0/0.8.
 *
 * <p>Immutable and thread-safe once built; loading reads files, so call {@link #load} off the server threads.
 */
public final class CeBlockSoundTable {
    /** One event: the sound id without CraftEngine's {@code replaced.} prefix, and the volume and pitch to play it at. */
    public record Sound(String id, float volume, float pitch) {
    }

    /** The two events CraftEngine sends at fixed values. */
    public record Entry(Sound hit, Sound breakSound) {
    }

    /** The empty table (nothing is rewritten). */
    public static final CeBlockSoundTable EMPTY = new CeBlockSoundTable(Map.of());

    /** Plain-id defaults: the client multipliers on a (1.0, 1.0) {@code SoundType}. */
    static final float HIT_VOLUME = 0.25f;
    static final float HIT_PITCH = 0.5f;
    static final float BREAK_VOLUME = 1.0f;
    static final float BREAK_PITCH = 0.8f;
    private static final String REPLACED = "replaced.";

    private final Map<String, Entry> blocks;

    private CeBlockSoundTable(Map<String, Entry> blocks) {
        this.blocks = Map.copyOf(blocks);
    }

    /** The entry of CraftEngine block {@code blockId}, if it is ours and has sounds. */
    public Optional<Entry> entry(String blockId) {
        return Optional.ofNullable(blocks.get(blockId));
    }

    public boolean contains(String blockId) {
        return blocks.containsKey(blockId);
    }

    public int size() {
        return blocks.size();
    }

    /** {@code ns:replaced.path} to {@code ns:path}, the id the configuration names. */
    public static String normalise(String soundId) {
        int colon = soundId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : soundId.substring(0, colon);
        String path = colon < 0 ? soundId : soundId.substring(colon + 1);
        if (path.startsWith(REPLACED)) {
            path = path.substring(REPLACED.length());
        }
        return ns + ":" + path;
    }

    /**
     * Reads every {@code .yml} under {@code resourcesDir} (CraftEngine's {@code resources} folder). Files that mention
     * neither a sound nor a template are skipped without parsing; a file that does not parse is reported to {@code problems} and skipped.
     */
    public static CeBlockSoundTable load(Path resourcesDir, List<String> problems) throws IOException {
        if (!Files.isDirectory(resourcesDir)) {
            return EMPTY;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(resourcesDir)) {
            files = walk.filter(p -> p.toString().endsWith(".yml") && Files.isRegularFile(p)).sorted().toList();
        }
        Builder b = new Builder();
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            if (!text.contains("sound") && !text.contains("template")) {
                continue;
            }
            try {
                b.add(text);
            } catch (IllegalArgumentException e) {
                problems.add(resourcesDir.relativize(f) + ": " + e.getMessage());
            }
        }
        return b.build();
    }

    /** The table of one YAML document (tests). */
    public static CeBlockSoundTable parse(String yaml) {
        Builder b = new Builder();
        b.add(yaml);
        return b.build();
    }

    /**
     * Collects entries from several documents; a later document's entry for the same block wins. Templates may live in
     * any document, so blocks whose sounds need them are resolved in {@link #build()}.
     */
    public static final class Builder {
        private final Map<String, Entry> blocks = new HashMap<>();
        private final Map<String, Object> templates = new HashMap<>();
        /** Block id to the block's YAML, for entries without literal or argument sounds that call a template. */
        private final Map<String, Map<?, ?>> pending = new LinkedHashMap<>();

        public Builder add(String yaml) {
            Map<?, ?> root = Yamls.load(yaml);
            for (Map.Entry<?, ?> section : root.entrySet()) {
                if (!(section.getKey() instanceof String key) || !(section.getValue() instanceof Map<?, ?> entries)) {
                    continue;
                }
                // "templates" / "blocks", or with "#suffix" (CraftEngine's duplicate-key extension).
                if (key.equals("templates") || key.startsWith("templates#")) {
                    for (Map.Entry<?, ?> t : entries.entrySet()) {
                        if (t.getKey() instanceof String id) {
                            templates.put(id, t.getValue());
                        }
                    }
                    continue;
                }
                if (!(key.equals("blocks") || key.startsWith("blocks#"))) {
                    continue;
                }
                for (Map.Entry<?, ?> e : entries.entrySet()) {
                    if (e.getKey() instanceof String id && ours(id) && e.getValue() instanceof Map<?, ?> block) {
                        Entry entry = entry(block);
                        if (entry != null) {
                            blocks.put(id, entry);
                            pending.remove(id);
                        } else if (callsTemplate(block)) {
                            pending.put(id, block);
                            blocks.remove(id);
                        }
                    }
                }
            }
            return this;
        }

        public CeBlockSoundTable build() {
            Map<String, Entry> all = new HashMap<>(blocks);
            Templates t = new Templates(templates);
            for (Map.Entry<String, Map<?, ?>> e : pending.entrySet()) {
                Entry entry = t.resolve(e.getValue());
                if (entry != null) {
                    all.put(e.getKey(), entry);
                }
            }
            return new CeBlockSoundTable(all);
        }
    }

    private static boolean callsTemplate(Map<?, ?> block) {
        return block.containsKey("template")
            || block.get("settings") instanceof Map<?, ?> settings && settings.containsKey("template");
    }

    /** The part of CraftEngine's template expansion the sound table needs (PAR-INFRA-35). */
    static final class Templates {
        private static final int MAX_DEPTH = 32;
        private final Map<String, Object> templates;

        Templates(Map<String, Object> templates) {
            this.templates = templates;
        }

        Entry resolve(Map<?, ?> block) {
            Object expanded;
            if (block.containsKey("template")) {
                expanded = expand(block, Map.of(), 0);
            } else {
                Map<Object, Object> copy = new LinkedHashMap<>();
                copy.put("settings", expand(block.get("settings"), Map.of(), 0));
                expanded = copy;
            }
            if (!(expanded instanceof Map<?, ?> m)) {
                return null;
            }
            Sound hit = settingsSound(m, "hit", HIT_VOLUME, HIT_PITCH);
            Sound brk = settingsSound(m, "break", BREAK_VOLUME, BREAK_PITCH);
            return hit == null && brk == null ? null : new Entry(hit, brk);
        }

        private static Sound settingsSound(Map<?, ?> block, String event, float defVolume, float defPitch) {
            if (block.get("settings") instanceof Map<?, ?> settings && settings.get("sounds") instanceof Map<?, ?> sounds) {
                Sound s = of(sounds.get(event), null, null, defVolume, defPitch);
                if (s != null && !s.id().contains("${")) {
                    return s;
                }
            }
            return null;
        }

        /** Expands {@code node} with {@code args} in scope. */
        Object expand(Object node, Map<String, Object> args, int depth) {
            if (depth > MAX_DEPTH) {
                return node;
            }
            if (node instanceof Map<?, ?> map) {
                if (map.containsKey("template")) {
                    return call(map, args, depth);
                }
                Map<Object, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(e.getKey(), expand(e.getValue(), args, depth + 1));
                }
                return out;
            }
            if (node instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object o : list) {
                    out.add(expand(o, args, depth + 1));
                }
                return out;
            }
            if (node instanceof String s) {
                return substitute(s, args);
            }
            return node;
        }

        private Object call(Map<?, ?> node, Map<String, Object> callerArgs, int depth) {
            // The template's own arguments are defaults; the caller's argument always wins.
            Map<String, Object> scope = new HashMap<>();
            if (node.get("arguments") instanceof Map<?, ?> own) {
                for (Map.Entry<?, ?> a : own.entrySet()) {
                    if (a.getKey() instanceof String k) {
                        scope.put(k, expand(a.getValue(), callerArgs, depth + 1));
                    }
                }
            }
            scope.putAll(callerArgs);
            Object ref = node.get("template");
            List<?> ids = ref instanceof List<?> l ? l : java.util.Collections.singletonList(ref);
            Map<Object, Object> result = new LinkedHashMap<>();
            for (Object id : ids) {
                Object body = id instanceof String s ? templates.get(String.valueOf(substitute(s, scope))) : null;
                if (body != null && expand(body, scope, depth + 1) instanceof Map<?, ?> m) {
                    deepMerge(result, m);
                }
            }
            if (node.get("merges") instanceof Map<?, ?> merges && expand(merges, scope, depth + 1) instanceof Map<?, ?> m) {
                deepMerge(result, m);
            }
            if (node.get("overrides") instanceof Map<?, ?> overrides) {
                for (Map.Entry<?, ?> o : overrides.entrySet()) {
                    result.put(o.getKey(), expand(o.getValue(), scope, depth + 1));
                }
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private static void deepMerge(Map<Object, Object> into, Map<?, ?> from) {
            for (Map.Entry<?, ?> e : from.entrySet()) {
                Object cur = into.get(e.getKey());
                if (cur instanceof Map<?, ?> a && e.getValue() instanceof Map<?, ?> b) {
                    Map<Object, Object> merged = new LinkedHashMap<>((Map<Object, Object>) a);
                    deepMerge(merged, b);
                    into.put(e.getKey(), merged);
                } else {
                    into.put(e.getKey(), e.getValue());
                }
            }
        }

        /**
         * {@code ${name}} and {@code ${name:-default}}; a string that is one placeholder becomes the argument's value
         * itself (a number stays a number). Unknown names without a default stay as written.
         */
        static Object substitute(String s, Map<String, Object> args) {
            int start = s.indexOf("${");
            if (start < 0) {
                return s;
            }
            int end = s.indexOf('}', start);
            if (start == 0 && end == s.length() - 1) {
                Object v = lookup(s.substring(2, end), args);
                return v != null ? v : s;
            }
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (start >= 0 && end > start) {
                out.append(s, i, start);
                Object v = lookup(s.substring(start + 2, end), args);
                out.append(v != null ? String.valueOf(v) : s.substring(start, end + 1));
                i = end + 1;
                start = s.indexOf("${", i);
                end = start < 0 ? -1 : s.indexOf('}', start);
            }
            out.append(s.substring(i));
            return out.toString();
        }

        private static Object lookup(String expr, Map<String, Object> args) {
            int dflt = expr.indexOf(":-");
            String name = dflt < 0 ? expr : expr.substring(0, dflt);
            Object v = args.get(name);
            if (v == null && dflt >= 0) {
                return expr.substring(dflt + 2);
            }
            return v;
        }
    }

    private static boolean ours(String id) {
        return id.startsWith("tfp") && id.indexOf(':') > 0;
    }

    private static Entry entry(Map<?, ?> block) {
        Sound hit = sound(block, "hit", HIT_VOLUME, HIT_PITCH);
        Sound brk = sound(block, "break", BREAK_VOLUME, BREAK_PITCH);
        if (hit == null && brk == null) {
            return null;
        }
        return new Entry(hit, brk);
    }

    private static Sound sound(Map<?, ?> block, String event, float defVolume, float defPitch) {
        Sound fromSettings = null;
        if (block.get("settings") instanceof Map<?, ?> settings && settings.get("sounds") instanceof Map<?, ?> sounds) {
            fromSettings = of(sounds.get(event), null, null, defVolume, defPitch);
        }
        Sound fromArguments = null;
        if (block.get("arguments") instanceof Map<?, ?> args) {
            String suffix = "_sound_" + event;
            List<String> keys = new ArrayList<>();
            for (Object k : args.keySet()) {
                if (k instanceof String s && s.endsWith(suffix)) {
                    keys.add(s);
                }
            }
            if (keys.size() == 1) {
                String k = keys.get(0);
                fromArguments = of(args.get(k), args.get(k + "_volume"), args.get(k + "_pitch"), defVolume, defPitch);
            }
        }
        // The entry's own settings override what its template builds from the arguments.
        return fromSettings != null ? fromSettings : fromArguments;
    }

    private static Sound of(Object value, Object volume, Object pitch, float defVolume, float defPitch) {
        if (value instanceof String id && !id.isBlank()) {
            return new Sound(normalise(id.trim()), number(volume, defVolume), number(pitch, defPitch));
        }
        if (value instanceof Map<?, ?> m && m.get("id") instanceof String id && !id.isBlank()) {
            return new Sound(normalise(id.trim()), number(m.get("volume"), defVolume), number(m.get("pitch"), defPitch));
        }
        return null;
    }

    private static float number(Object o, float fallback) {
        if (o instanceof Number n) {
            return n.floatValue();
        }
        if (o instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
