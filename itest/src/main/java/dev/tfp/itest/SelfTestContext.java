/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.ready.ReadyGate;
import java.nio.file.Path;

/** What cases may use. The in-server implementation is {@link SelfTestPlugin}'s; unit tests pass their own. */
public interface SelfTestContext {
    /**
     * The main plugin, enabled.
     *
     * @throws AssertionError if TerraFirmaPlugin is missing or not enabled (the case fails)
     */
    TfpPlugin tfp();

    /** The main plugin's ready gate. */
    ReadyGate readyGate();

    /** Wall-clock start of the server JVM (ms since the epoch): "startup" for the ready-time check. */
    long jvmStartMillis();

    /** The server-log findings collected so far (M1-INT-02, M1-DGN-03), or null if nothing watches the log. */
    default LogFindings logFindings() {
        return null;
    }

    /** Whether the live Log4j tap is attached (findings are current, not only scanned at the end). */
    default boolean liveLog() {
        return false;
    }

    /** The self-test plugin's data folder (markers kept across the matrix's two starts), or null if none. */
    default Path dataDir() {
        return null;
    }
}
