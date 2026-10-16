/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import dev.tfp.datagen.DatagenException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The audited supply of opaque full-cube vanilla states that CraftEngine's default {@code mappings.yml} leaves
 * alone, per {@link VersionFamily}: resource {@code visuals/fullcube-states.csv}, exported per state by
 * {@code tools/datagen/scripts/export_fullcube_states.py} with the same classification as
 * {@code docs/research/vanilla-fullcube-supply.csv} (the per-block allowlist; a test checks both agree).
 */
public final class VanillaSupply {
    public static final String RESOURCE = "fullcube-states.csv";

    /** Where a state comes from (research §2). */
    public enum Source {
        IDENTITY_DUP("identity_dup"),
        IDENTITY_DUP_CROSS("identity_dup_cross"),
        NETHER_END("nether_end"),
        ABSENT("absent"),
        TFC_PRESENT("tfc_present");

        final String csv;

        Source(String csv) {
            this.csv = csv;
        }

        public boolean identity() {
            return this == IDENTITY_DUP || this == IDENTITY_DUP_CROSS;
        }

        static Source of(String s) {
            for (Source v : values()) {
                if (v.csv.equals(s)) {
                    return v;
                }
            }
            throw new DatagenException("unknown supply source " + s);
        }
    }

    /** Client-side behaviour class (research §2.5). Only CLEAN and CAVEAT_MINOR are terrain-grade. */
    public enum Behaviour {
        CLEAN, CAVEAT_MINOR, CAVEAT_MAJOR, AVOID;

        static Behaviour of(String s) {
            return valueOf(s.toUpperCase(java.util.Locale.ROOT));
        }
    }

    /** One vanilla state, e.g. {@code minecraft:oak_slab[type=double,waterlogged=false]}. */
    public record State(String state, Source source, Behaviour behaviour, String twin) {
        public String block() {
            int i = state.indexOf('[');
            return i < 0 ? state : state.substring(0, i);
        }

        public boolean terrainGrade() {
            return source != Source.TFC_PRESENT
                && (behaviour == Behaviour.CLEAN || behaviour == Behaviour.CAVEAT_MINOR);
        }

        /** Allocation pool label used in reports. */
        public String pool() {
            if (source.identity()) {
                return "identity";
            }
            if (source == Source.NETHER_END) {
                return "nether_end";
            }
            return behaviour == Behaviour.CLEAN ? "absent_clean" : "absent_minor";
        }

        int rank() {
            int cat = source.identity() ? 0 : source == Source.NETHER_END ? 1 : 2;
            return cat * 2 + (behaviour == Behaviour.CLEAN ? 0 : 1);
        }
    }

    /** Allocation order: identity duplicates → Nether/End → absent-in-TFC; clean before minor-caveat in each. */
    static final Comparator<State> ORDER = Comparator.comparingInt(State::rank).thenComparing(State::state);

    private final Map<VersionFamily, Map<String, State>> states;

    private VanillaSupply(Map<VersionFamily, Map<String, State>> states) {
        this.states = states;
    }

    public static VanillaSupply load() {
        try (InputStream in = VanillaSupply.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new DatagenException("missing datagen resource visuals/" + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static VanillaSupply parse(String csv) {
        Map<VersionFamily, Map<String, State>> m = new EnumMap<>(VersionFamily.class);
        for (VersionFamily f : VersionFamily.values()) {
            m.put(f, new LinkedHashMap<>());
        }
        boolean header = true;
        try (BufferedReader r = new BufferedReader(new java.io.StringReader(csv))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> f = fields(line);
                if (header) {
                    if (!f.equals(List.of("family", "state", "source", "behaviour", "twin"))) {
                        throw new DatagenException(RESOURCE + ": unexpected header " + f);
                    }
                    header = false;
                    continue;
                }
                State s = new State(f.get(1), Source.of(f.get(2)), Behaviour.of(f.get(3)), f.get(4));
                if (s.source.identity() == s.twin.isEmpty()) {
                    throw new DatagenException(RESOURCE + ": identity duplicates (and only they) need a twin: " + line);
                }
                if (m.get(VersionFamily.byId(f.get(0))).put(s.state, s) != null) {
                    throw new DatagenException(RESOURCE + ": duplicate " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new VanillaSupply(m);
    }

    /** Minimal CSV: comma separated, fields with commas are double-quoted (no embedded quotes). */
    private static List<String> fields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** Every listed state of the family (all behaviours), keyed by state string. */
    public Map<String, State> all(VersionFamily family) {
        return java.util.Collections.unmodifiableMap(states.get(family));
    }

    public State get(VersionFamily family, String state) {
        return states.get(family).get(state);
    }

    /**
     * Terrain-grade candidates in allocation order. Nether/End states only with {@code netherEnd}; the
     * {@code reserved} states (kept vanilla as mapping targets) are left out.
     */
    public List<State> candidates(VersionFamily family, boolean netherEnd, java.util.Set<String> reserved) {
        return states.get(family).values().stream()
            .filter(State::terrainGrade)
            .filter(s -> netherEnd || s.source != Source.NETHER_END)
            .filter(s -> !reserved.contains(s.state))
            .sorted(ORDER)
            .toList();
    }
}
