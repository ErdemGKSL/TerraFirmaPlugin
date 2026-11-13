/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.compat.Version;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Our {@code config.yml}, typed (M1-BOOT-03). Immutable; a reload builds a new instance and swaps it.
 * {@link ConfigCodec} maps it to and from the YAML tree. TFC's ServerConfig/CommonConfig keys are added
 * here as their features land.
 */
public record TfpConfig(Visuals visuals, OreOverlay oreOverlay, AutoInstall autoInstall, CatchUp catchUp, Hud hud,
        BucketConfig buckets, Survival survival) {
    public static final TfpConfig DEFAULTS = new TfpConfig(Visuals.DEFAULTS, OreOverlay.DEFAULTS, AutoInstall.DEFAULTS,
            CatchUp.DEFAULTS, Hud.DEFAULTS, BucketConfig.DEFAULTS, Survival.DEFAULTS);

    public TfpConfig {
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(buckets, "buckets");
        Objects.requireNonNull(survival, "survival");
    }

    /** A config with TFC's survival defaults ({@link Survival#DEFAULTS}). */
    public TfpConfig(Visuals visuals, OreOverlay oreOverlay, AutoInstall autoInstall, CatchUp catchUp, Hud hud,
            BucketConfig buckets) {
        this(visuals, oreOverlay, autoInstall, catchUp, hud, buckets, Survival.DEFAULTS);
    }

    /** A config with TFC's bucket defaults ({@link BucketConfig#DEFAULTS}). */
    public TfpConfig(Visuals visuals, OreOverlay oreOverlay, AutoInstall autoInstall, CatchUp catchUp, Hud hud) {
        this(visuals, oreOverlay, autoInstall, catchUp, hud, BucketConfig.DEFAULTS);
    }

    /** A config with TFC's HUD display and bucket defaults ({@link Hud#DEFAULTS}, {@link BucketConfig#DEFAULTS}). */
    public TfpConfig(Visuals visuals, OreOverlay oreOverlay, AutoInstall autoInstall, CatchUp catchUp) {
        this(visuals, oreOverlay, autoInstall, catchUp, Hud.DEFAULTS, BucketConfig.DEFAULTS);
    }

    /**
     * Visual allocation profile ({@code ARCHITECTURE.md} §9.1). Higher levels free more CE states at some fidelity cost.
     * {@code level-2} is the highest: {@code level-3a}/{@code level-3b} only merged ore grades and were removed when ores
     * became host-rock visuals with face overlays (PAR-INFRA-15); {@link #removedAlias} maps them for old configs.
     */
    public enum Profile {
        LEVEL_0, LEVEL_1, LEVEL_2;

        /** The config spelling: {@code level-2}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Profile> byId(String id) {
            for (Profile p : values()) {
                if (p.id().equalsIgnoreCase(id.strip())) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }

        /**
         * A removed profile name ({@code level-3a}, {@code level-3b}) and the profile it is read as ({@code level-2}), so
         * an old config keeps starting; empty for every other text.
         */
        public static Optional<Profile> removedAlias(String id) {
            String s = id.strip().toLowerCase(Locale.ROOT);
            return s.equals("level-3a") || s.equals("level-3b") ? Optional.of(LEVEL_2) : Optional.empty();
        }

        /** The accepted spellings for messages: {@code level-0, level-1 or level-2}. */
        public static String accepted() {
            Profile[] all = values();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < all.length; i++) {
                b.append(i == 0 ? "" : i == all.length - 1 ? " or " : ", ").append(all[i].id());
            }
            return b.toString();
        }
    }

    /** {@code visuals.use-nether-end-blocks}: AUTO uses them only if the server has no vanilla Nether/End. */
    public enum NetherEndBlocks {
        AUTO, TRUE, FALSE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param profile             per MC version family ({@code "1.21"}, {@code "26"})
     * @param solidReserveWarning warn at startup if fewer free solid-pool slots remain for other CE packs
     */
    public record Visuals(Map<String, Profile> profile, int solidReserveWarning, NetherEndBlocks useNetherEndBlocks) {
        public static final Map<String, Profile> DEFAULT_PROFILES = defaultProfiles();
        public static final Visuals DEFAULTS = new Visuals(DEFAULT_PROFILES, 500, NetherEndBlocks.AUTO);

        public Visuals {
            profile = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(profile));
            if (solidReserveWarning < 0) {
                throw new IllegalArgumentException("solidReserveWarning must be >= 0");
            }
        }

        private static Map<String, Profile> defaultProfiles() {
            Map<String, Profile> m = new LinkedHashMap<>();
            m.put("1.21", Profile.LEVEL_2);
            m.put("26", Profile.LEVEL_2);
            return java.util.Collections.unmodifiableMap(m);
        }

        /** {@code 1.21} for 1.21.x, {@code 26} for 26.x. */
        public static String family(Version minecraft) {
            return minecraft.major() == 1 && minecraft.parts().size() > 1
                    ? "1." + minecraft.parts().get(1) : Integer.toString(minecraft.major());
        }

        /** The profile for the running version: the configured one, else the built-in default for its family. */
        public Profile profileFor(Version minecraft) {
            String family = family(minecraft);
            Profile p = profile.get(family);
            if (p != null) {
                return p;
            }
            return Profile.LEVEL_2;
        }
    }

    /**
     * {@code ore-overlay.*}: the exposed-face ore overlays (ARCHITECTURE.md §9.1, design
     * {@code docs/research/ore-entity-renderer-design.md} §3.4; read by the overlay runtime, PAR-INFRA-16). Defaults from the
     * S1 verdict of PAR-WORLD-17 (unchanged: the owner question there is open).
     *
     * @param cutoffBlocks             client draw distance of a face overlay in blocks (sets {@code view_range} and the
     *                                 server send radius {@code ceil(cutoff / 16) + 1} chunks)
     * @param maxPerPlayer             face overlays sent to one player at most, nearest first
     * @param surfaceOutcropsFullRange faces at the surface (cliffs, outcrops) are sent within the full view distance
     */
    public record OreOverlay(int cutoffBlocks, int maxPerPlayer, boolean surfaceOutcropsFullRange) {
        public static final OreOverlay DEFAULTS = new OreOverlay(48, 8000, true);

        public OreOverlay {
            if (cutoffBlocks < 0 || maxPerPlayer < 0) {
                throw new IllegalArgumentException("ore-overlay values must be >= 0");
            }
        }
    }

    /**
     * {@code auto-install.*} ({@code dependency-bootstrap} §4-5).
     *
     * @param craftengine     write our pack into {@code plugins/CraftEngine/resources/tfp/}
     * @param betterhud       write our pack into {@code plugins/BetterHud/packs/tfp/}
     * @param configEdits     apply the targeted CE/BetterHud/BetterModel config edits
     * @param downloadMissing download missing client plugins (hash-pinned). Opt-in: off by default
     * @param restoreDeleted  recreate files of ours that the owner deleted
     */
    public record AutoInstall(boolean craftengine, boolean betterhud, boolean configEdits, boolean downloadMissing,
            boolean restoreDeleted) {
        public static final AutoInstall DEFAULTS = new AutoInstall(true, true, true, false, false);
    }

    /**
     * {@code catchup.*}: the lazy catch-up service (PAR-INFRA-11/25/31; substitute for TFC's per-tick
     * {@code ICalendarTickable.checkForCalendarUpdate}, NOT-PARITY NP-SIM-01). Read once when the catch-up module enables;
     * the JVM properties {@code tfp.catchup.*} override these values.
     *
     * @param sweepPeriodTicks   server ticks between visible-area sweeps; {@code 0} turns the sweep off
     * @param sweepRadiusChunks  chunks around each player the sweep catches up, {@value #MAX_RADIUS} at most
     * @param feedBudgetMicros   region-thread time per tick window for capturing snapshots, 1..{@value #MAX_BUDGET_MICROS}
     * @param commitBudgetMicros region-thread time per tick window for writing caught-up states,
     *                           1..{@value #MAX_BUDGET_MICROS}
     */
    public record CatchUp(int sweepPeriodTicks, int sweepRadiusChunks, int feedBudgetMicros, int commitBudgetMicros) {
        public static final int MAX_RADIUS = 32;
        public static final int MAX_BUDGET_MICROS = 50_000;
        public static final CatchUp DEFAULTS = new CatchUp(600, 4, 1000, 1000);

        public CatchUp {
            if (sweepPeriodTicks < 0) {
                throw new IllegalArgumentException("sweepPeriodTicks must be >= 0");
            }
            if (sweepRadiusChunks < 0 || sweepRadiusChunks > MAX_RADIUS) {
                throw new IllegalArgumentException("sweepRadiusChunks must be in 0.." + MAX_RADIUS);
            }
            if (feedBudgetMicros < 1 || feedBudgetMicros > MAX_BUDGET_MICROS
                    || commitBudgetMicros < 1 || commitBudgetMicros > MAX_BUDGET_MICROS) {
                throw new IllegalArgumentException("budgets must be in 1.." + MAX_BUDGET_MICROS);
            }
        }
    }

    /**
     * {@code hud.*}: TFC's HUD display options (PAR-HUD-14/17). TFC keeps them per player in {@code ClientConfig}; a
     * vanilla client has none, so here they are one server-wide value (NOT-PARITY NP-CLIENT-04). Core holds the section
     * as the plain tree the YAML reader produced (key to scalar, as written); the paper layer types it
     * ({@code dev.tfp.paper.hud.HudStyles.parse}), which owns the lenient enum parsing, the unknown-key check and their
     * warnings, so the values are parsed in exactly one place. Read again on every {@code /tfp reload}.
     *
     * @param section the {@code hud} keys and values as written, in config.yml order
     */
    public record Hud(Map<String, Object> section) {
        public static final String HEALTH_KEY = "health-display-style";
        public static final String CLIMATE_KEY = "climate-temperature-style";
        public static final String XP_BAR_KEY = "enable-experience-bar";
        public static final String XP_STYLE_KEY = "disabled-experience-bar-style";
        /** The keys of the section, in config.yml order. */
        public static final List<String> KEYS = List.of(HEALTH_KEY, CLIMATE_KEY, XP_BAR_KEY, XP_STYLE_KEY);
        /** TFC's {@code ClientConfig} defaults: {@code TFC}, {@code CELSIUS}, XP bar on, {@code HOVER}. */
        public static final Hud DEFAULTS = new Hud(defaults());

        public Hud {
            section = Collections.unmodifiableMap(new LinkedHashMap<>(section));
        }

        private static Map<String, Object> defaults() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(HEALTH_KEY, "TFC");
            m.put(CLIMATE_KEY, "CELSIUS");
            m.put(XP_BAR_KEY, true);
            m.put(XP_STYLE_KEY, "HOVER");
            return m;
        }
    }

    /**
     * {@code survival.*}: TFC's {@code ServerConfig} player survival options (PAR-SURV-14). Read again on every
     * {@code /tfp reload config}.
     *
     * @param keepNutritionAfterDeath     TFC {@code keepNutritionAfterDeath} (true): a death keeps the nutrition records
     * @param peacefulPassiveRegeneration TFC {@code enablePeacefulDifficultyPassiveRegeneration} (false): TFC's
     *                                    extra-peaceful heal, hunger and thirst refill
     * @param naturalRegenerationModifier TFC {@code naturalRegenerationModifier} (1, range 0..100): the factor on TFC's
     *                                    passive health regeneration
     */
    public record Survival(boolean keepNutritionAfterDeath, boolean peacefulPassiveRegeneration,
            double naturalRegenerationModifier) {
        public static final String SECTION = "survival";
        public static final String KEEP_NUTRITION_KEY = "keep-nutrition-after-death";
        public static final String PEACEFUL_REGEN_KEY = "peaceful-passive-regeneration";
        public static final String REGEN_MODIFIER_KEY = "natural-regeneration-modifier";
        /** The keys of the section, in config.yml order. */
        public static final List<String> KEYS = List.of(KEEP_NUTRITION_KEY, PEACEFUL_REGEN_KEY, REGEN_MODIFIER_KEY);
        /** TFC's {@code define(..., 1d, 0d, 100d)} bounds of {@code naturalRegenerationModifier}. */
        public static final double MIN_MODIFIER = 0d;
        public static final double MAX_MODIFIER = 100d;
        /** TFC's {@code ServerConfig} defaults. */
        public static final Survival DEFAULTS = new Survival(true, false, 1d);

        public Survival {
            if (!(naturalRegenerationModifier >= MIN_MODIFIER && naturalRegenerationModifier <= MAX_MODIFIER)) {
                throw new IllegalArgumentException("naturalRegenerationModifier must be in 0..100: "
                        + naturalRegenerationModifier);
            }
        }
    }
}
