/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick, useItemOn),
 *       src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (onEntityItemUpdate),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, onFireStop)
 */
package dev.tfp.core.light;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.light.TorchRules.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-10: TFC's torch state machine at c3b4d9536. The burnout rows were produced by a transcription of
 * {@code onRandomTick} ({@code torchTicks > 0 && getTicksSinceUpdate() > torchTicks} swaps to the dead kind with the
 * facing kept; scratch script, not committed) and pasted here; lazy-vs-stepped replays TFC's random ticks.
 */
class TorchRulesTest {
    /** kind, litTick, now, torchTicks, kind after. */
    private static final Object[][] BURNOUT = {
        {Kind.TORCH, 0L, 0L, 86_400, Kind.TORCH},
        {Kind.TORCH, 0L, 86_400L, 86_400, Kind.TORCH},
        {Kind.TORCH, 0L, 86_401L, 86_400, Kind.DEAD_TORCH},
        {Kind.WALL_TORCH, 1_000L, 87_400L, 86_400, Kind.WALL_TORCH},
        {Kind.WALL_TORCH, 1_000L, 87_401L, 86_400, Kind.DEAD_WALL_TORCH},
        {Kind.TORCH, 5_000L, 77_000L, 86_400, Kind.TORCH}, // three in-game days (72,000 ticks) after placing
        {Kind.TORCH, 5_000L, 101_000L, 86_400, Kind.DEAD_TORCH}, // four days
        {Kind.TORCH, 0L, 10_000_000L, -1, Kind.TORCH},
        {Kind.WALL_TORCH, 0L, 10_000_000L, 0, Kind.WALL_TORCH},
        {Kind.TORCH, 0L, 2L, 1, Kind.DEAD_TORCH},
        {Kind.DEAD_TORCH, 0L, 10_000_000L, 86_400, Kind.DEAD_TORCH},
        {Kind.DEAD_WALL_TORCH, 0L, 0L, 86_400, Kind.DEAD_WALL_TORCH},
    };

    @Test
    void burnoutMatchesTfcGolden() {
        for (Object[] r : BURNOUT) {
            assertThat(TorchRules.afterCatchUp((Kind) r[0], (long) r[1], (long) r[2], (int) r[3]))
                .as("%s lit %s at %s limit %s", r[0], r[1], r[2], r[3]).isEqualTo(r[4]);
        }
        assertThat(TorchRules.burnoutTick(1_000L, 86_400)).isEqualTo(87_401L);
        assertThat(TorchRules.burnoutTick(1_000L, -1)).isEqualTo(LightSourceRules.NEVER);
    }

    @Test
    void kindsKeepTheirShapeThroughEverySwap() {
        assertThat(Kind.TORCH.dead()).isEqualTo(Kind.DEAD_TORCH);
        assertThat(Kind.WALL_TORCH.dead()).isEqualTo(Kind.DEAD_WALL_TORCH);
        assertThat(Kind.DEAD_TORCH.relit()).isEqualTo(Kind.TORCH);
        assertThat(Kind.DEAD_WALL_TORCH.relit()).isEqualTo(Kind.WALL_TORCH);
        assertThat(Kind.TORCH.light()).isEqualTo(14);
        assertThat(Kind.WALL_TORCH.light()).isEqualTo(14);
        assertThat(Kind.DEAD_TORCH.light()).isZero();
        assertThat(Kind.DEAD_WALL_TORCH.light()).isZero();
        for (Kind k : Kind.values()) {
            assertThat(Kind.byId(k.id())).contains(k);
        }
        assertThat(Kind.byId("tfp:candle")).isEmpty();
        assertThat(Kind.byId("minecraft:torch")).isEmpty();
    }

    @Test
    void fireStartResetsLitAndRelightsDeadTorches() {
        assertThat(TorchRules.fireStart(Kind.TORCH)).isEqualTo(TorchRules.FireStart.RESET);
        assertThat(TorchRules.fireStart(Kind.WALL_TORCH)).isEqualTo(TorchRules.FireStart.RESET);
        assertThat(TorchRules.fireStart(Kind.DEAD_TORCH)).isEqualTo(TorchRules.FireStart.RELIT);
        assertThat(TorchRules.fireStart(Kind.DEAD_WALL_TORCH)).isEqualTo(TorchRules.FireStart.RELIT);
        for (Kind k : Kind.values()) {
            assertThat(TorchRules.fireStart(k).handled()).isTrue();
        }
        assertThat(TorchRules.FireStart.NONE.handled()).isFalse();
    }

    @Test
    void douseKillsOnlyLitTorches() {
        assertThat(TorchRules.douse(Kind.TORCH)).contains(Kind.DEAD_TORCH);
        assertThat(TorchRules.douse(Kind.WALL_TORCH)).contains(Kind.DEAD_WALL_TORCH);
        assertThat(TorchRules.douse(Kind.DEAD_TORCH)).isEmpty();
        assertThat(TorchRules.douse(Kind.DEAD_WALL_TORCH)).isEmpty();
    }

    @Test
    void useOfALitTorchAlwaysConsumesAndLightsSticks() {
        assertThat(TorchRules.useConsumes(Kind.TORCH)).isTrue();
        assertThat(TorchRules.useConsumes(Kind.WALL_TORCH)).isTrue();
        assertThat(TorchRules.useConsumes(Kind.DEAD_TORCH)).isFalse();
        assertThat(TorchRules.lightsHeldItem(Kind.TORCH, true)).isTrue();
        assertThat(TorchRules.lightsHeldItem(Kind.WALL_TORCH, true)).isTrue();
        assertThat(TorchRules.lightsHeldItem(Kind.TORCH, false)).isFalse();
        assertThat(TorchRules.lightsHeldItem(Kind.DEAD_TORCH, true)).isFalse();
    }

    @Test
    void droppedTorchesGoOutInBatchesAsTfc() {
        // TFC: count > 5 ? 1 + nextInt(5) : count
        for (int count = 1; count <= 5; count++) {
            assertThat(TorchRules.extinguished(count, b -> {
                throw new AssertionError("no draw for a small stack");
            })).isEqualTo(count);
        }
        List<Integer> bounds = new ArrayList<>();
        assertThat(TorchRules.extinguished(6, b -> {
            bounds.add(b);
            return 0;
        })).isEqualTo(1);
        assertThat(TorchRules.extinguished(64, b -> 4)).isEqualTo(5);
        assertThat(bounds).containsExactly(5);
        assertThatThrownBy(() -> TorchRules.extinguished(0, b -> 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TorchRules.droppedAgeRequirement(true)).isEqualTo(20);
        assertThat(TorchRules.droppedAgeRequirement(false)).isEqualTo(160);
        assertThat(TorchRules.DROPPED_LIGHT_CHANCE).isEqualTo(0.01f);
    }

    @Test
    void lazyCatchUpEqualsTfcRandomTicks() {
        Random random = new Random(0x70c4L);
        int[] limits = {-1, 1, 50, 1200, 86_400};
        for (int trial = 0; trial < 300; trial++) {
            int limit = limits[trial % limits.length];
            Kind start = random.nextBoolean() ? Kind.TORCH : Kind.WALL_TORCH;
            long lit = random.nextInt(1_000_000);
            long end = lit + random.nextInt(Math.max(limit, 100) * 2 + 1);
            // stepped: TFC random ticks at random gaps (vanilla: about every 68 s per block at randomTickSpeed 3)
            Kind stepped = start;
            long t = lit;
            while (t < end) {
                t = Math.min(end, t + 1 + random.nextInt(4096));
                if (stepped.lit() && limit > 0 && t - lit > limit) {
                    stepped = stepped.dead();
                }
            }
            // a random tick may land exactly at end or not at all; the lazy answer is the one of a check at end
            Kind lazy = TorchRules.afterCatchUp(start, lit, end, limit);
            assertThat(lazy).as("trial %s", trial).isEqualTo(stepped);
            // evaluating twice (catch-up on every touch) changes nothing
            assertThat(TorchRules.afterCatchUp(lazy, lit, end, limit)).isEqualTo(lazy);
        }
    }

    @Test
    void relightRestartsTheBurnTime() {
        int limit = LightSourceRules.DEFAULT_TORCH_TICKS;
        long placed = 1_000;
        long relit = placed + 50_000;
        // a reset at relit: the torch lives until relit + limit, not placed + limit
        assertThat(TorchRules.afterCatchUp(Kind.TORCH, relit, placed + limit + 10, limit)).isEqualTo(Kind.TORCH);
        assertThat(TorchRules.afterCatchUp(Kind.TORCH, relit, relit + limit + 1, limit)).isEqualTo(Kind.DEAD_TORCH);
    }
}
