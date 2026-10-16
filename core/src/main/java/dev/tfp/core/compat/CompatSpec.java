/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What this build supports, read from {@code content/compat.yml} (shipped in the jar as
 * {@code compat.yml}). Parsed from the generic map a YAML reader produces, so it stays platform-free.
 *
 * @param java       the lowest Java feature release we run on
 * @param minecraft  supported Minecraft ranges (inclusive)
 * @param tested     Minecraft versions CI actually runs
 * @param plugins    client plugins by their exact plugin name ({@code CraftEngine}, ...), in file order
 */
public record CompatSpec(int java, List<Range> minecraft, List<Version> tested, Map<String, PluginRequirement> plugins) {
    public CompatSpec {
        if (minecraft.isEmpty()) {
            throw new IllegalArgumentException("compat: minecraft.supported must list at least one range");
        }
        minecraft = List.copyOf(minecraft);
        tested = List.copyOf(tested);
        plugins = Collections.unmodifiableMap(new LinkedHashMap<>(plugins));
    }

    /** An inclusive version range. */
    public record Range(Version min, Version max) {
        public Range {
            if (max.isOlderThan(min)) {
                throw new IllegalArgumentException("range max " + max + " is below min " + min);
            }
        }

        public boolean contains(Version v) {
            return !v.isOlderThan(min) && !v.isNewerThan(max);
        }

        @Override
        public String toString() {
            return min + "-" + max;
        }
    }

    /**
     * @param min      versions below this refuse to enable
     * @param tested   versions we ran against; newer than all of them warns
     * @param provides what is missing without the plugin (used in the degraded-mode message)
     */
    public record PluginRequirement(Version min, List<Version> tested, String provides) {
        public PluginRequirement {
            tested = List.copyOf(tested);
        }

        /** The newest tested version, or {@code min} if none is listed. */
        public Version newestTested() {
            return tested.stream().max(Version::compareTo).orElse(min);
        }
    }

    /** The lowest supported Minecraft version. */
    public Version lowestMinecraft() {
        return minecraft.stream().map(Range::min).min(Version::compareTo).orElseThrow();
    }

    /** @throws IllegalArgumentException with the offending key if the map is malformed */
    public static CompatSpec fromMap(Map<String, ?> root) {
        int java = intValue(root.get("java"), "java");
        Map<String, ?> mc = map(root.get("minecraft"), "minecraft");
        List<Range> ranges = new ArrayList<>();
        for (Object o : list(mc.get("supported"), "minecraft.supported")) {
            Map<String, ?> r = map(o, "minecraft.supported[]");
            ranges.add(new Range(version(r.get("min"), "minecraft.supported[].min"),
                    version(r.get("max"), "minecraft.supported[].max")));
        }
        List<Version> tested = versions(mc.get("tested"), "minecraft.tested");
        Map<String, PluginRequirement> plugins = new LinkedHashMap<>();
        Map<String, ?> ps = map(root.get("plugins"), "plugins");
        for (Map.Entry<String, ?> e : ps.entrySet()) {
            String k = "plugins." + e.getKey();
            Map<String, ?> p = map(e.getValue(), k);
            Object provides = p.get("provides");
            plugins.put(e.getKey(), new PluginRequirement(version(p.get("min"), k + ".min"),
                    p.containsKey("tested") ? versions(p.get("tested"), k + ".tested") : List.of(),
                    provides == null ? e.getKey() + " features" : provides.toString()));
        }
        return new CompatSpec(java, ranges, tested, plugins);
    }

    private static int intValue(Object o, String key) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.strip());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("compat: '" + key + "' must be an integer, got " + o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> map(Object o, String key) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, ?>) m;
        }
        throw new IllegalArgumentException("compat: '" + key + "' must be a mapping, got " + o);
    }

    private static List<?> list(Object o, String key) {
        if (o instanceof List<?> l) {
            return l;
        }
        throw new IllegalArgumentException("compat: '" + key + "' must be a list, got " + o);
    }

    private static Version version(Object o, String key) {
        if (o == null) {
            throw new IllegalArgumentException("compat: '" + key + "' is missing");
        }
        return Version.tryParse(o.toString())
                .orElseThrow(() -> new IllegalArgumentException("compat: '" + key + "' is not a version: " + o));
    }

    private static List<Version> versions(Object o, String key) {
        List<Version> out = new ArrayList<>();
        for (Object v : list(o, key)) {
            out.add(version(v, key + "[]"));
        }
        return out;
    }
}
