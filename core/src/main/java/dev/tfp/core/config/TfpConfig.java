/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import dev.tfp.core.compat.Version;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Our {@code config.yml}, typed (M1-BOOT-03). Immutable; a reload builds a new instance and swaps it.
 * {@link ConfigCodec} maps it to and from the YAML tree. TFC's ServerConfig/CommonConfig keys are added
 * here as their features land.
 */
public record TfpConfig(Visuals visuals, AutoInstall autoInstall) {
    public static final TfpConfig DEFAULTS = new TfpConfig(Visuals.DEFAULTS, AutoInstall.DEFAULTS);

    /** Visual allocation profile ({@code ARCHITECTURE.md} §9.1). Higher levels free more CE states at some fidelity cost. */
    public enum Profile {
        LEVEL_0, LEVEL_1, LEVEL_2, LEVEL_3A, LEVEL_3B;

        /** The config spelling: {@code level-3a}. */
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
            m.put("1.21", Profile.LEVEL_3A);
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
            return minecraft.major() == 1 ? Profile.LEVEL_3A : Profile.LEVEL_2;
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
}
