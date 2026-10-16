/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import dev.tfp.core.compat.Version;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.util.Objects;

/**
 * Which precomputed visual allocation this server uses (ARCHITECTURE.md §9.1, M1-INST-03): MC version
 * family × {@code visuals.profile} × Nether/End blocks on/off. Pure.
 *
 * @param family    {@code 1.21} or {@code 26}
 * @param profile   the configured profile for that family
 * @param netherEnd Nether/End blocks are used as visuals
 */
public record VisualSelection(String family, Profile profile, boolean netherEnd) {
    public VisualSelection {
        Objects.requireNonNull(family);
        Objects.requireNonNull(profile);
    }

    /**
     * @param netherAllowed the server has a vanilla Nether ({@code allow-nether})
     * @param endAllowed    the server has a vanilla End ({@code bukkit.yml settings.allow-end})
     */
    public static VisualSelection of(Visuals config, Version minecraft, boolean netherAllowed, boolean endAllowed) {
        return new VisualSelection(Visuals.family(minecraft), config.profileFor(minecraft),
                netherEnd(config.useNetherEndBlocks(), netherAllowed, endAllowed));
    }

    /** {@code auto}: only if neither vanilla dimension exists; {@code true}/{@code false}: as the owner says. */
    public static boolean netherEnd(NetherEndBlocks setting, boolean netherAllowed, boolean endAllowed) {
        return switch (setting) {
            case AUTO -> !netherAllowed && !endAllowed;
            case TRUE -> true;
            case FALSE -> false;
        };
    }

    /** The allocation folder below the content root: {@code visuals/1.21/level-3a/nether-end-off}. */
    public String dir() {
        return "visuals/" + family + "/" + profile.id() + "/nether-end-" + (netherEnd ? "on" : "off");
    }

    /** One line for logs and the build record: {@code 1.21/level-3a/nether-end-off}. */
    public String id() {
        return dir().substring("visuals/".length());
    }
}
