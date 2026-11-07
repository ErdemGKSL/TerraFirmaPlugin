/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.config.TfpConfig.CatchUp;
import dev.tfp.core.config.TfpConfig.Hud;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.OreOverlay;
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
    /** The owner's file, as named in warnings. */
    public static final String FILE = "plugins/TerraFirmaPlugin/config.yml";

    private ConfigCodec() {
    }

    /** {@code visuals.profile."1.21"}: the key as the owner writes it. */
    static String quotedKey(String family) {
        return "visuals.profile.\"" + family + "\"";
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
        r.unknown(top, "", List.of("visuals", "ore-overlay", "auto-install", "catchup", "hud"));

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
                Optional<Profile> removed = e.getValue() == null ? Optional.empty()
                        : Profile.removedAlias(e.getValue().toString());
                if (p.isPresent()) {
                    profiles.put(family, p.get());
                } else if (removed.isPresent()) {
                    // PAR-INFRA-15: logged at every start until the owner edits the key; the owner's file is never rewritten.
                    profiles.put(family, removed.get());
                    r.warn(FILE + " " + quotedKey(family) + ": " + e.getValue().toString().strip()
                            + " was removed: ores are drawn as overlays and grades no longer cost visual states; using "
                            + removed.get().id());
                } else {
                    r.warn(key + ": unknown profile '" + e.getValue() + "' (expected " + Profile.accepted() + "); using "
                            + profiles.getOrDefault(family, Profile.LEVEL_2).id());
                }
            }
        } else {
            r.warn("visuals.profile: expected a mapping of version family to profile; using defaults");
        }
        int reserve = r.nonNegativeInt(v, "visuals.", "solid-reserve-warning", Visuals.DEFAULTS.solidReserveWarning());
        NetherEndBlocks netherEnd = r.netherEnd(v, Visuals.DEFAULTS.useNetherEndBlocks());

        Map<?, ?> o = r.section(top, "ore-overlay");
        r.unknown(o, "ore-overlay.", List.of("cutoff-blocks", "max-per-player", "surface-outcrops-full-range"));
        OreOverlay od = OreOverlay.DEFAULTS;
        OreOverlay overlay = new OreOverlay(
                r.nonNegativeInt(o, "ore-overlay.", "cutoff-blocks", od.cutoffBlocks()),
                r.nonNegativeInt(o, "ore-overlay.", "max-per-player", od.maxPerPlayer()),
                r.bool(o, "ore-overlay.", "surface-outcrops-full-range", od.surfaceOutcropsFullRange()));

        Map<?, ?> a = r.section(top, "auto-install");
        r.unknown(a, "auto-install.", List.of("craftengine", "betterhud", "config-edits", "download-missing", "restore-deleted"));
        AutoInstall d = AutoInstall.DEFAULTS;
        AutoInstall auto = new AutoInstall(
                r.bool(a, "auto-install.", "craftengine", d.craftengine()),
                r.bool(a, "auto-install.", "betterhud", d.betterhud()),
                r.bool(a, "auto-install.", "config-edits", d.configEdits()),
                r.bool(a, "auto-install.", "download-missing", d.downloadMissing()),
                r.bool(a, "auto-install.", "restore-deleted", d.restoreDeleted()));

        Map<?, ?> cu = r.section(top, "catchup");
        r.unknown(cu, "catchup.", List.of("sweep-period-ticks", "sweep-radius-chunks", "feed-budget-micros",
                "commit-budget-micros"));
        CatchUp cd = CatchUp.DEFAULTS;
        CatchUp catchUp = new CatchUp(
                r.intIn(cu, "catchup.", "sweep-period-ticks", cd.sweepPeriodTicks(), 0, Integer.MAX_VALUE),
                r.intIn(cu, "catchup.", "sweep-radius-chunks", cd.sweepRadiusChunks(), 0, CatchUp.MAX_RADIUS),
                r.intIn(cu, "catchup.", "feed-budget-micros", cd.feedBudgetMicros(), 1, CatchUp.MAX_BUDGET_MICROS),
                r.intIn(cu, "catchup.", "commit-budget-micros", cd.commitBudgetMicros(), 1, CatchUp.MAX_BUDGET_MICROS));
        Hud hud = r.hud(top);
        return new Parsed(new TfpConfig(new Visuals(profiles, reserve, netherEnd), overlay, auto, catchUp, hud),
                r.warnings, r.defaulted);
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
        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("cutoff-blocks", c.oreOverlay().cutoffBlocks());
        overlay.put("max-per-player", c.oreOverlay().maxPerPlayer());
        overlay.put("surface-outcrops-full-range", c.oreOverlay().surfaceOutcropsFullRange());
        AutoInstall a = c.autoInstall();
        Map<String, Object> auto = new LinkedHashMap<>();
        auto.put("craftengine", a.craftengine());
        auto.put("betterhud", a.betterhud());
        auto.put("config-edits", a.configEdits());
        auto.put("download-missing", a.downloadMissing());
        auto.put("restore-deleted", a.restoreDeleted());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("visuals", visuals);
        root.put("ore-overlay", overlay);
        root.put("auto-install", auto);
        CatchUp cu = c.catchUp();
        Map<String, Object> catchUp = new LinkedHashMap<>();
        catchUp.put("sweep-period-ticks", cu.sweepPeriodTicks());
        catchUp.put("sweep-radius-chunks", cu.sweepRadiusChunks());
        catchUp.put("feed-budget-micros", cu.feedBudgetMicros());
        catchUp.put("commit-budget-micros", cu.commitBudgetMicros());
        root.put("catchup", catchUp);
        root.put("hud", new LinkedHashMap<>(c.hud().section()));
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
            return intIn(m, prefix, key, def, 0, Integer.MAX_VALUE);
        }

        int intIn(Map<?, ?> m, String prefix, String key, int def, int min, int max) {
            if (!m.containsKey(key)) {
                defaulted.add(prefix + key);
                return def;
            }
            Object o = m.get(key);
            if (o instanceof Number n && n.doubleValue() == n.longValue() && n.longValue() >= min
                    && n.longValue() <= max) {
                return n.intValue();
            }
            String range = max == Integer.MAX_VALUE ? ">= " + min : "in " + min + ".." + max;
            warn(prefix + key + ": expected a whole number " + range + ", got '" + o + "'; using " + def);
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

        /**
         * The {@code hud} section as written (PAR-HUD-17). Only its shape is checked here: a non-section warns and takes
         * TFC's defaults, an absent key is reported as defaulted (and takes its default), every present key is kept as
         * written. The values, unknown keys and their warnings are the paper layer's {@code HudStyles.parse}.
         */
        Hud hud(Map<?, ?> top) {
            Map<?, ?> h = section(top, "hud");
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : Hud.KEYS) {
                if (h.containsKey(key)) {
                    out.put(key, h.get(key));
                } else {
                    defaulted.add("hud." + key);
                    out.put(key, Hud.DEFAULTS.section().get(key));
                }
            }
            for (Map.Entry<?, ?> e : h.entrySet()) {
                out.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
            }
            return new Hud(out);
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
