/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps {@link TfpConfig} to and from the plain tree a YAML reader produces (maps, lists, scalars).
 * Lenient on purpose: an owner's typo must not stop the server. Missing keys take their defaults,
 * bad values take their defaults with a warning, unknown keys are reported as warnings and ignored.
 */
public final class ConfigCodec {
    private ConfigCodec() {
    }

    /**
     * @param config   the typed config (always complete)
     * @param warnings human-readable problems (unknown keys, bad values), one per entry
     * @param defaulted dotted leaf keys that were absent and took their default
     */
    public record Parsed(TfpConfig config, List<String> warnings, List<String> defaulted) {
        public Parsed {
            warnings = List.copyOf(warnings);
            defaulted = List.copyOf(defaulted);
        }
    }

    public static Parsed parse(Map<?, ?> root) {
        Reader r = new Reader();
        Map<?, ?> top = root == null ? Map.of() : root;
        r.unknown(top, "", List.of("visuals", "auto-install"));

        Map<?, ?> v = r.section(top, "visuals");
        r.unknown(v, "visuals.", List.of("profile", "solid-reserve-warning", "use-nether-end-blocks"));
        Map<String, Profile> profiles = new LinkedHashMap<>(Visuals.DEFAULT_PROFILES);
        if (!v.containsKey("profile")) {
            r.defaulted.add("visuals.profile");
        } else if (v.get("profile") instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                String family = String.valueOf(e.getKey());
                String key = "visuals.profile." + family;
                if (!family.matches("\\d+(\\.\\d+)?")) {
                    r.warn(key + ": '" + family + "' is not a version family like \"1.21\" or \"26\"; ignored");
                    continue;
                }
                Optional<Profile> p = e.getValue() == null ? Optional.empty() : Profile.byId(e.getValue().toString());
                if (p.isPresent()) {
                    profiles.put(family, p.get());
                } else {
                    r.warn(key + ": unknown profile '" + e.getValue() + "' (expected level-0, level-1, level-2, level-3a or "
                            + "level-3b); using " + profiles.getOrDefault(family, family.startsWith("1.") ? Profile.LEVEL_3A
                            : Profile.LEVEL_2).id());
                }
            }
        } else {
            r.warn("visuals.profile: expected a mapping of version family to profile; using defaults");
        }
        int reserve = r.nonNegativeInt(v, "visuals.", "solid-reserve-warning", Visuals.DEFAULTS.solidReserveWarning());
        NetherEndBlocks netherEnd = r.netherEnd(v, Visuals.DEFAULTS.useNetherEndBlocks());

        Map<?, ?> a = r.section(top, "auto-install");
        r.unknown(a, "auto-install.", List.of("craftengine", "betterhud", "config-edits", "download-missing", "restore-deleted"));
        AutoInstall d = AutoInstall.DEFAULTS;
        AutoInstall auto = new AutoInstall(
                r.bool(a, "auto-install.", "craftengine", d.craftengine()),
                r.bool(a, "auto-install.", "betterhud", d.betterhud()),
                r.bool(a, "auto-install.", "config-edits", d.configEdits()),
                r.bool(a, "auto-install.", "download-missing", d.downloadMissing()),
                r.bool(a, "auto-install.", "restore-deleted", d.restoreDeleted()));
        return new Parsed(new TfpConfig(new Visuals(profiles, reserve, netherEnd), auto), r.warnings, r.defaulted);
    }

    /** The tree that {@link #parse} reads back to an equal config. Keys in config.yml order. */
    public static Map<String, Object> toMap(TfpConfig c) {
        Map<String, Object> profile = new LinkedHashMap<>();
        c.visuals().profile().forEach((k, p) -> profile.put(k, p.id()));
        Map<String, Object> visuals = new LinkedHashMap<>();
        visuals.put("profile", profile);
        visuals.put("solid-reserve-warning", c.visuals().solidReserveWarning());
        NetherEndBlocks ne = c.visuals().useNetherEndBlocks();
        visuals.put("use-nether-end-blocks", ne == NetherEndBlocks.AUTO ? ne.id() : (Object) (ne == NetherEndBlocks.TRUE));
        AutoInstall a = c.autoInstall();
        Map<String, Object> auto = new LinkedHashMap<>();
        auto.put("craftengine", a.craftengine());
        auto.put("betterhud", a.betterhud());
        auto.put("config-edits", a.configEdits());
        auto.put("download-missing", a.downloadMissing());
        auto.put("restore-deleted", a.restoreDeleted());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("visuals", visuals);
        root.put("auto-install", auto);
        return root;
    }

    private static final class Reader {
        final List<String> warnings = new ArrayList<>();
        final List<String> defaulted = new ArrayList<>();

        void warn(String s) {
            warnings.add(s);
        }

        void unknown(Map<?, ?> m, String prefix, List<String> known) {
            for (Object k : m.keySet()) {
                if (!known.contains(String.valueOf(k))) {
                    warn("unknown key '" + prefix + k + "' (ignored)");
                }
            }
        }

        Map<?, ?> section(Map<?, ?> m, String key) {
            Object o = m.get(key);
            if (o instanceof Map<?, ?> s) {
                return s;
            }
            if (o != null) {
                warn(key + ": expected a section, got '" + o + "'; using defaults");
            }
            return Map.of();
        }

        int nonNegativeInt(Map<?, ?> m, String prefix, String key, int def) {
            if (!m.containsKey(key)) {
                defaulted.add(prefix + key);
                return def;
            }
            Object o = m.get(key);
            if (o instanceof Number n && n.doubleValue() == n.longValue() && n.longValue() >= 0
                    && n.longValue() <= Integer.MAX_VALUE) {
                return n.intValue();
            }
            warn(prefix + key + ": expected a whole number >= 0, got '" + o + "'; using " + def);
            return def;
        }

        boolean bool(Map<?, ?> m, String prefix, String key, boolean def) {
            if (!m.containsKey(key)) {
                defaulted.add(prefix + key);
                return def;
            }
            Object o = m.get(key);
            if (o instanceof Boolean b) {
                return b;
            }
            warn(prefix + key + ": expected true or false, got '" + o + "'; using " + def);
            return def;
        }

        NetherEndBlocks netherEnd(Map<?, ?> m, NetherEndBlocks def) {
            String key = "use-nether-end-blocks";
            if (!m.containsKey(key)) {
                defaulted.add("visuals." + key);
                return def;
            }
            Object o = m.get(key);
            if (o instanceof Boolean b) {
                return b ? NetherEndBlocks.TRUE : NetherEndBlocks.FALSE;
            }
            if (o != null) {
                switch (o.toString().strip().toLowerCase(Locale.ROOT)) {
                    case "auto": return NetherEndBlocks.AUTO;
                    case "true": return NetherEndBlocks.TRUE;
                    case "false": return NetherEndBlocks.FALSE;
                    default: break;
                }
            }
            warn("visuals." + key + ": expected auto, true or false, got '" + o + "'; using " + def.id());
            return def;
        }
    }
}
