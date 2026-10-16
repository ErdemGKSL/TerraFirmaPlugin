/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.ready.ReadyGate;

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
}
