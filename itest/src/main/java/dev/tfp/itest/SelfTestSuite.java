/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.itest.cases.BetterHudPlaceholderCase;
import dev.tfp.itest.cases.AnimalSpawnPersistCase;
import dev.tfp.itest.cases.BetterModelSpawnCase;
import dev.tfp.itest.cases.CalendarPersistCase;
import dev.tfp.itest.cases.ConfigCase;
import dev.tfp.itest.cases.CraftEngineBlockRoundTripCase;
import dev.tfp.itest.cases.CraftEnginePackIssuesCase;
import dev.tfp.itest.cases.DeviceRoundTripCase;
import dev.tfp.itest.cases.ItemDataRoundTripCase;
import dev.tfp.itest.cases.MetalRunCase;
import dev.tfp.itest.cases.PacketNoopCase;
import dev.tfp.itest.cases.PlayerDataCase;
import dev.tfp.itest.cases.ProfileRestartCase;
import dev.tfp.itest.cases.ReadyCase;
import dev.tfp.itest.cases.RegionSchedulerCase;
import dev.tfp.itest.cases.SuppressionCase;
import dev.tfp.itest.cases.VersionCommandCase;
import dev.tfp.itest.cases.WorldgenTerrainCase;
import java.time.Duration;
import java.util.List;

/**
 * The registered cases, in run order. Add new cases here. A case whose plugin or content is missing in this
 * run throws {@link SelfTestCase.Skip} and is reported as skipped with the reason.
 */
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
                new CalendarPersistCase(),
                new ItemDataRoundTripCase(),
                new CraftEnginePackIssuesCase(),
                new CraftEngineBlockRoundTripCase(),
                new DeviceRoundTripCase(),
                new WorldgenTerrainCase(),
                new MetalRunCase(),
                new ProfileRestartCase(),
                new SuppressionCase(),
                new BetterModelSpawnCase(),
                new AnimalSpawnPersistCase(),
                new BetterHudPlaceholderCase(),
                new PacketNoopCase());
    }
}
