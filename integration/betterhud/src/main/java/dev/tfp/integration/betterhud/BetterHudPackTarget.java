/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where our BetterHud content goes ({@code betterhud} skill, "Where our content goes"): the external pack
 * folder {@code plugins/BetterHud/packs/tfp/}. BetterHud skips pack folders starting with {@code -}; if
 * the owner renamed ours to {@code packs/-tfp}, we must not recreate {@code packs/tfp}. Path computation
 * only: the installer (M1-INST-01) does the writing.
 */
public final class BetterHudPackTarget {
    public static final String PACK = "tfp";

    private BetterHudPackTarget() {
    }

    /** {@code <plugins>/BetterHud/packs/tfp}. */
    public static Path packFolder(Path pluginsDirectory) {
        return packsRoot(pluginsDirectory).resolve(PACK);
    }

    /** {@code <plugins>/BetterHud/packs/-tfp}: the owner's opt-out marker. */
    public static Path disabledFolder(Path pluginsDirectory) {
        return packsRoot(pluginsDirectory).resolve("-" + PACK);
    }

    /** True if the owner disabled our pack by renaming it. */
    public static boolean optedOut(Path pluginsDirectory) {
        return Files.isDirectory(disabledFolder(pluginsDirectory));
    }

    private static Path packsRoot(Path pluginsDirectory) {
        return pluginsDirectory.resolve("BetterHud").resolve("packs");
    }
}
