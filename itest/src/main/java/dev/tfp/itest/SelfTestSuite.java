/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.itest.cases.ConfigCase;
import dev.tfp.itest.cases.PlayerDataCase;
import dev.tfp.itest.cases.ReadyCase;
import dev.tfp.itest.cases.RegionSchedulerCase;
import dev.tfp.itest.cases.VersionCommandCase;
import java.time.Duration;
import java.util.List;

/** The registered cases, in run order. Add new cases here. */
public final class SelfTestSuite {
    /** JUnit suite and class name in the report. */
    public static final String NAME = "tfp.selftest";

    private SelfTestSuite() {
    }

    /** @param readyLimit how long after JVM start the ready gate may take ({@code -Dtfp.selftest.readySeconds}) */
    public static List<SelfTestCase> cases(Duration readyLimit) {
        return List.of(
                new ReadyCase(readyLimit),
                new ConfigCase(),
                new VersionCommandCase(),
                new PlayerDataCase(),
                new RegionSchedulerCase(),
                SelfTestCase.skipped("CraftEngine test block places, breaks and persists (M1-INT-02)",
                        "pending: the tfp CraftEngine pack has no test block yet and the CraftEngine adapter "
                                + "(CustomBlocks place/stateAt/remove) has not been verified in a server"),
                SelfTestCase.skipped("BetterModel example bovine spawns (M1-INT-03)",
                        "pending: the BetterModel adapter (jar model registration, EntityModels.attach to a "
                                + "carrier) has not been verified in a server"));
    }
}
