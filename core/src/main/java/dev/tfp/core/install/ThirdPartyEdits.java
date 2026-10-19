/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import dev.tfp.core.install.ConfigEdit.Effect;
import dev.tfp.core.install.ConfigEdit.Rule;
import dev.tfp.core.install.ConfigEdit.Target;
import java.util.List;
import java.util.Set;

/**
 * The config edits we make in CraftEngine, BetterHud and BetterModel, and only these
 * ({@code dependency-bootstrap} §6 table, {@code docs/spikes/M0-CE-01.md} for {@code cache-system}).
 */
public final class ThirdPartyEdits {
    /** CraftEngine's own default for {@code block.serverside-blocks}; we never go below it. */
    public static final int CE_DEFAULT_SERVERSIDE_BLOCKS = 2000;
    /** {@code dependency-bootstrap} §6: below 1.21.2 CraftEngine claims custom-model-data floats we use. */
    public static final String MIN_PACK_VERSION = "1.21.4";

    private ThirdPartyEdits() {
    }

    /**
     * {@code serverside-blocks} needed for {@code states} CraftEngine block states: +25 % margin, rounded up
     * to CraftEngine's recommended step of 1000, never below its default.
     */
    public static int serversideBlocks(int states) {
        if (states < 0) {
            throw new IllegalArgumentException("states < 0");
        }
        long withMargin = (long) Math.ceil(states * 1.25);
        long rounded = (withMargin + 999) / 1000 * 1000;
        return (int) Math.max(CE_DEFAULT_SERVERSIDE_BLOCKS, rounded);
    }

    /** @param ceStates how many CraftEngine block states our content defines (datagen report) */
    public static List<ConfigEdit> all(int ceStates) {
        return List.of(
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "misc.delay-configuration-load", new Rule.Equals("false"),
                        Effect.RESTART, true, "CraftEngine configs must be loaded before the TFC world's first chunks generate"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "chunk-system.cache-system", new Rule.Equals("true"),
                        Effect.RESTART, true, "without it CraftEngine does not track blocks written by our world generator (M0-CE-01)"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "chunk-system.storage-type", new Rule.NotEquals("none", "linear"),
                        Effect.RESTART, true, "'none' loses block-entity data on chunk unload"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "block.serverside-blocks", new Rule.AtLeast(serversideBlocks(ceStates)),
                        Effect.RESTART, true, "enough real server blocks for every TFC block state (" + ceStates + " + margin)"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "resource-pack.supported-version.min",
                        new Rule.MinVersion(MIN_PACK_VERSION, Set.of("server", "latest")), Effect.PACK_REBUILD, false,
                        "below 1.21.2 CraftEngine claims the custom-model-data float our item visuals use"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "resource-pack.exclude-core-shaders", new Rule.Equals("false"),
                        Effect.PACK_REBUILD, false, "'true' deletes BetterHud's text shader"),
                new ConfigEdit(Target.CRAFTENGINE_CONFIG, "resource-pack.merge-external-zip-files",
                        new Rule.ListWith("BetterModel/build.zip"), Effect.PACK_REBUILD, false,
                        "BetterModel's pack is merged into CraftEngine's"),
                new ConfigEdit(Target.CRAFTENGINE_SHULKER_PACK, "enable", new Rule.Equals("false"),
                        Effect.PACK_REBUILD, false, "its old-format core shader may break 1.21.6+/26.x clients"),
                new ConfigEdit(Target.BETTERHUD_CONFIG, "default-hud",
                        new Rule.All(List.of(new Rule.ListWithout("test_hud"), new Rule.ListWith("tfp_date_hud"),
                                new Rule.ListWith("tfp_bars_hud"))),
                        Effect.RELOAD, false, "BetterHud's demo HUD would be shown to every player; the TFC date and bars HUDs must be shown"),
                new ConfigEdit(Target.BETTERHUD_CONFIG, "merge-with-external-resources", new Rule.Equals("true"),
                        Effect.RELOAD, false, "BetterHud merges into CraftEngine's pack"),
                new ConfigEdit(Target.BETTERHUD_CONFIG, "pack-type", new Rule.NotEquals("none", "zip"),
                        Effect.RELOAD, false, "'none' produces nothing for CraftEngine to merge"),
                new ConfigEdit(Target.BETTERHUD_CONFIG, "enable-self-host", new Rule.Equals("false"),
                        Effect.RELOAD, false, "CraftEngine hosts the single merged pack"),
                new ConfigEdit(Target.BETTERMODEL_CONFIG, "pack-type", new Rule.Equals("zip"),
                        Effect.RELOAD, false, "CraftEngine merges BetterModel/build.zip"));
    }
}
