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
 * {@code tfp} are read; other packs' blocks are never rewritten. Blocks whose sounds come from a template alone (rock,
 * soil, sand via {@code tfp:settings/sound}) are not in the table (follow-up PAR-INFRA-35).
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
     * Reads every {@code .yml} under {@code resourcesDir} (CraftEngine's {@code resources} folder). Files that never
     * mention a sound are skipped without parsing; a file that does not parse is reported to {@code problems} and skipped.
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
            if (!text.contains("sound")) {
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

    /** Collects entries from several documents; a later document's entry for the same block wins. */
    public static final class Builder {
        private final Map<String, Entry> blocks = new HashMap<>();

        public Builder add(String yaml) {
            Map<?, ?> root = Yamls.load(yaml);
            for (Map.Entry<?, ?> section : root.entrySet()) {
                // "blocks", or "blocks#suffix" (CraftEngine's duplicate-key extension).
                if (!(section.getKey() instanceof String key) || !(key.equals("blocks") || key.startsWith("blocks#"))
                        || !(section.getValue() instanceof Map<?, ?> entries)) {
                    continue;
                }
                for (Map.Entry<?, ?> e : entries.entrySet()) {
                    if (e.getKey() instanceof String id && ours(id) && e.getValue() instanceof Map<?, ?> block) {
                        Entry entry = entry(block);
                        if (entry != null) {
                            blocks.put(id, entry);
                        }
                    }
                }
            }
            return this;
        }

        public CeBlockSoundTable build() {
            return new CeBlockSoundTable(blocks);
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
