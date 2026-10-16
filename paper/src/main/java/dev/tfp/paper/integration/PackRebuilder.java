/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.concurrent.CompletableFuture;

/**
 * Rebuilds the CraftEngine resource pack: the equivalent of {@code /ce reload all} (configuration reload,
 * then the {@code reload_pack} workflows that build and upload the zip). CraftEngine never builds the pack
 * at startup ({@code dependency-bootstrap} §6), so the installer flow asks for one after our content or the
 * visual profile changed (M1-INST-03). Implemented by {@code integration/craftengine}.
 *
 * <p><b>Threads:</b> callable from any thread; the work is scheduled after the current CraftEngine reload
 * has finished. Never call it from a CraftEngine reload listener synchronously expecting it to be done.
 */
public interface PackRebuilder {
    /** True if a rebuild can be requested (CraftEngine is installed and its adapter is running). */
    boolean available();

    /**
     * Starts a full CraftEngine reload + pack build. Completes with {@code true} once the pack workflows
     * ran, {@code false} if CraftEngine reported a failed reload; exceptionally if it could not be started.
     */
    CompletableFuture<Boolean> rebuild();
}
