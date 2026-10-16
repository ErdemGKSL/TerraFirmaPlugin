/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.itest.LogFindings;
import dev.tfp.itest.SelfTestPack;
import dev.tfp.itest.ThreadAssertMatcher;
import dev.tfp.paper.integration.HudPlaceholders;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.packet.NoopPacketHandler;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDirection;
import dev.tfp.paper.packet.PacketHandlerRegistry;
import dev.tfp.paper.packet.PacketLayer;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The case bodies of M1-INT-02/03/04, M1-DGN-03 and M1-SVC-03 that do not need a server, with fakes. */
class M1CasesTest {
    // ---- ce.pack_issues / ce.block_roundtrip ---------------------------------------------------------

    static LogFindings findings() {
        return new LogFindings(new ThreadAssertMatcher(), new CraftEngineIssueMatcher("tfp"),
                new CraftEngineIssueMatcher(SelfTestPack.NAMESPACE));
    }

    @Test
    void packIssuesPassesWithNoIssuesAndEveryFamilyLoaded() {
        LogFindings f = findings();
        f.accept("INFO", "[CraftEngine] Loaded 1353 blocks", (Throwable) null);
        assertThat(CraftEnginePackIssuesCase.check(f, id -> true)).startsWith("0 issues for [tfp, tfpitest]");
    }

    @Test
    void packIssuesFailsOnAFoundNIssuesLineForEitherPack() {
        LogFindings f = findings();
        f.accept("WARN", "[CraftEngine] Found 3 issue(s) in file 'plugins/CraftEngine/resources/tfpitest/configuration/blocks.yml':",
                (Throwable) null);
        f.accept("WARN", "[CraftEngine] Found 2 issue(s) in file 'plugins/CraftEngine/resources/default/x.yml':",
                (Throwable) null);
        assertThatThrownBy(() -> CraftEnginePackIssuesCase.check(f, id -> true)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("3 CraftEngine issue(s)").hasMessageContaining("tfpitest/configuration/blocks.yml");
        assertThat(f.results().get(1).name()).contains("'tfp', 'tfpitest'");
    }

    @Test
    void packIssuesFailsWhenAFamilyDidNotLoad() {
        assertThatThrownBy(() -> CraftEnginePackIssuesCase.check(findings(), id -> !id.startsWith("tfp:ore/")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("tfp:ore/rich_hematite/granite");
    }

    @Test
    void roundTripPrefersTheGeneratedRockThenTheSelfTestMarker() {
        assertThat(CraftEngineBlockRoundTripCase.pick(id -> true)).contains("tfp:rock/raw/granite");
        assertThat(CraftEngineBlockRoundTripCase.pick(SelfTestPack.MARKER::equals)).contains(SelfTestPack.MARKER);
        assertThat(CraftEngineBlockRoundTripCase.pick(id -> false)).isEmpty();
        assertThat(new CraftEngineBlockRoundTripCase().timeout().toSeconds())
                .isGreaterThan(CraftEngineBlockRoundTripCase.cacheSeconds() + 60);
    }

    // ---- bh.placeholder -------------------------------------------------------------------------------

    /** Like the adapter: BetterHud's registry of our functions, read through the snapshot cache. */
    static HudPlaceholders fakeBetterHud(HudSnapshotCache cache, Map<String, ToDoubleFunction<HudSnapshot>> registered) {
        return new HudPlaceholders() {
            @Override
            public Set<String> registered() {
                return registered.keySet();
            }

            @Override
            public OptionalDouble evaluate(String name, UUID player) {
                ToDoubleFunction<HudSnapshot> f = registered.get(name);
                return f == null ? OptionalDouble.empty() : OptionalDouble.of(f.applyAsDouble(cache.snapshot(player)));
            }
        };
    }

    @Test
    void placeholderEvaluatesFromTheSnapshotAndCleansUp() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        HudPlaceholders bh = fakeBetterHud(cache, Map.of("tfp_health", HudSnapshot::health,
                "tfp_max_health", HudSnapshot::maxHealth));
        assertThat(BetterHudPlaceholderCase.check(bh, cache)).startsWith("tfp_health = 37.5 then 12");
        assertThat(cache.size()).isZero();
    }

    @Test
    void placeholderFailsWhenMissingOrNotReadingTheSnapshot() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        assertThatThrownBy(() -> BetterHudPlaceholderCase.check(fakeBetterHud(cache, Map.of()), cache))
                .isInstanceOf(AssertionError.class).hasMessageContaining("no 'tfp_health'");
        HudPlaceholders constant = fakeBetterHud(cache, Map.of("tfp_health", s -> 50, "tfp_max_health", s -> 50));
        assertThatThrownBy(() -> BetterHudPlaceholderCase.check(constant, cache)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("expected 37.5, got 50.0");
        assertThat(cache.size()).isZero();
    }

    // ---- packet.noop_measured -------------------------------------------------------------------------

    static PacketLayer layer(boolean installed) {
        return new PacketLayer() {
            @Override
            public void install(PacketHandlerRegistry registry) {
            }

            @Override
            public void uninstall() {
            }

            @Override
            public boolean installed() {
                return installed;
            }

            @Override
            public String describe() {
                return "PacketEvents 2.14.0";
            }
        };
    }

    static PacketContext keepAlive() {
        return new PacketContext() {
            @Override
            public PacketDirection direction() {
                return PacketDirection.SEND;
            }

            @Override
            public String packetType() {
                return "KEEP_ALIVE";
            }

            @Override
            public UUID playerId() {
                return new UUID(0, 1);
            }

            @Override
            public <T> T nativeEvent(Class<T> type) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void packetCaseReportsCountsAndNanos() {
        PacketHandlerRegistry r = new PacketHandlerRegistry(Logger.getAnonymousLogger());
        r.register(new NoopPacketHandler());
        assertThat(PacketNoopCase.check(layer(true), r, false, 20)).contains("listener installed",
                NoopPacketHandler.NAME + " calls=0", "no players online");
        assertThatThrownBy(() -> PacketNoopCase.check(layer(true), r, true, 20)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("no keep-alive");
        r.dispatch(keepAlive());
        r.dispatch(keepAlive());
        assertThat(PacketNoopCase.check(layer(true), r, true, 20)).contains(NoopPacketHandler.NAME + " calls=2 total=");
    }

    @Test
    void packetCaseFailsWithoutAListenerOrTheNoopHandler() {
        PacketHandlerRegistry empty = new PacketHandlerRegistry(Logger.getAnonymousLogger());
        assertThatThrownBy(() -> PacketNoopCase.check(layer(false), empty, false, 20)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("no listener installed");
        assertThatThrownBy(() -> PacketNoopCase.check(layer(true), empty, false, 20)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("not registered");
    }
}
