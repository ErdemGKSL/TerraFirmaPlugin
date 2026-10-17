/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.weather.SnowRule.Block;
import dev.tfp.core.weather.SnowRule.Cell;
import dev.tfp.core.weather.SnowRule.Change;
import dev.tfp.core.weather.SnowRule.Event;
import dev.tfp.core.weather.SnowRule.Kind;
import dev.tfp.core.world.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Hand-checked cases of {@link SnowRule}, read off TFC's {@code WeatherHelpers} at c3b4d9536. The event thresholds are
 * the strict comparisons of {@code onTickChunk} lines 284 and 293; the block rules are covered in bulk by
 * {@link SnowRuleGoldenTest}.
 */
class SnowRuleTest {
    private static final BlockPos SURFACE = new BlockPos(5, 70, 5);

    @Test
    void accumulationIsStrictlyBelowMinusTwoAndNeedsPrecipitation() {
        assertThat(SnowRule.event(-2f, PrecipitationType.SNOW)).isEqualTo(Event.NONE);
        assertThat(SnowRule.event(Math.nextDown(-2f), PrecipitationType.SNOW)).isEqualTo(Event.ACCUMULATE);
        assertThat(SnowRule.event(Math.nextDown(-2f), PrecipitationType.RAIN)).isEqualTo(Event.ACCUMULATE);
        assertThat(SnowRule.event(-30f, PrecipitationType.NONE)).isEqualTo(Event.NONE);
    }

    @Test
    void meltingIsStrictlyAboveTwoWhateverFalls() {
        assertThat(SnowRule.event(2f, PrecipitationType.NONE)).isEqualTo(Event.NONE);
        assertThat(SnowRule.event(Math.nextUp(2f), PrecipitationType.NONE)).isEqualTo(Event.MELT);
        assertThat(SnowRule.event(Math.nextUp(2f), PrecipitationType.RAIN)).isEqualTo(Event.MELT);
        for (float t : new float[] {Math.nextUp(-2f), -1f, 0f, -0f, 1f, 2f}) {
            for (PrecipitationType p : PrecipitationType.values()) {
                assertThat(SnowRule.event(t, p)).as("%s %s", t, p).isEqualTo(Event.NONE);
            }
        }
    }

    @Test
    void snowGoesOnTheAirAboveGroundUnderTheSky() {
        Grid g = new Grid().ground(SURFACE.offset(0, -1, 0));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED))
                .startsWith(change(SURFACE, Kind.PLACE_SNOW));
    }

    @Test
    void noSkyNoSnowButStillTheIceStep() {
        Grid g = new Grid().ground(SURFACE.offset(0, -1, 0));
        g.put(SURFACE, cell(Block.AIR, true, false));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED))
                .noneMatch(c -> c.kind() == Kind.PLACE_SNOW);
    }

    @Test
    void snowOnSnowMovesSidewaysAndNeverStacks() {
        Grid g = new Grid();
        g.put(SURFACE, new Cell(Block.SNOW, 8, false, true, true, false, true));
        BlockPos east = SURFACE.offset(1, 0, 0);
        g.put(east, cell(Block.AIR, true, true));
        List<Change> out = SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED);
        assertThat(out).startsWith(change(east, Kind.PLACE_SNOW));
        // Nowhere to go: TFC does not add a layer to the snow (placeSnowOrSnowPileAt only fills air or a pileable block)
        g.put(east, cell(Block.OTHER, false, true));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED))
                .noneMatch(c -> c.kind() == Kind.PLACE_SNOW || c.kind() == Kind.SET_SNOW_LAYERS);
    }

    @Test
    void aPlantIsPiledWithTheTopHalfAbsorbed() {
        Grid g = new Grid();
        g.put(SURFACE, cell(Block.SNOW_PILEABLE, true, true));
        g.put(SURFACE.offset(0, 1, 0), cell(Block.SNOW_PILEABLE, false, true));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED))
                .startsWith(new Change(SURFACE, Kind.PLACE_SNOW_PILE, 0, true));
    }

    @Test
    void stillWaterAtAShoreFreezesButOpenWaterDoesNot() {
        Grid g = new Grid();
        BlockPos water = SURFACE.offset(0, -1, 0);
        g.put(water, water());
        g.put(water.offset(1, 0, 0), water());
        g.put(water.offset(-1, 0, 0), water());
        g.put(water.offset(0, 0, 1), water());
        g.put(water.offset(0, 0, -1), water());
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED)).noneMatch(c -> c.kind() == Kind.FREEZE);
        g.put(water.offset(0, 0, -1), cell(Block.OTHER, false, true));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED)).startsWith(change(water, Kind.FREEZE));
    }

    @Test
    void saltWaterAndFlowingWaterNeverFreeze() {
        Grid g = new Grid();
        BlockPos sea = SURFACE.offset(0, -1, 0);
        g.put(sea, new Cell(Block.LIQUID, 0, true, false, false, true, true));
        assertThat(SnowRule.accumulate(g, SURFACE, new Random(1), SnowRule.UNLIMITED)).noneMatch(c -> c.kind() == Kind.FREEZE);
    }

    @Test
    void meltRemovesOneLayerThenTheSnowAndGivesPilesBack() {
        Grid g = new Grid();
        g.put(SURFACE, new Cell(Block.SNOW, 3, false, true, false, false, true));
        assertThat(SnowRule.melt(g, SURFACE, 8)).containsExactly(new Change(SURFACE, Kind.SET_SNOW_LAYERS, 2, false));
        g.put(SURFACE, new Cell(Block.SNOW, 1, false, true, false, false, true));
        assertThat(SnowRule.melt(g, SURFACE, 8)).containsExactly(change(SURFACE, Kind.REMOVE));
        g.put(SURFACE, new Cell(Block.SNOW_PILE, 1, false, true, false, false, true));
        assertThat(SnowRule.melt(g, SURFACE, 8)).containsExactly(new Change(SURFACE, Kind.RESTORE_PILE, 0, true));
    }

    @Test
    void iceMeltsToWaterWhenHeldAndSeaIceNeverMelts() {
        Grid g = new Grid();
        BlockPos top = SURFACE.offset(0, -1, 0);
        g.put(top, new Cell(Block.ICE, 0, false, false, true, true, true));
        g.put(top.offset(0, -1, 0), new Cell(Block.WATER, 0, true, false, false, true, false));
        assertThat(SnowRule.melt(g, SURFACE, 8)).containsExactly(change(top, Kind.MELT_TO_WATER));
        g.put(top.offset(0, -1, 0), cell(Block.AIR, false, false));
        assertThat(SnowRule.melt(g, SURFACE, 8)).containsExactly(change(top, Kind.REMOVE));
        g.put(top, new Cell(Block.SEA_ICE, 0, false, false, true, true, true));
        assertThat(SnowRule.melt(g, SURFACE, 8)).isEmpty();
    }

    @Test
    void meltTakesTheLowestIcicleOfAHangingColumn() {
        Grid g = new Grid().ground(SURFACE.offset(0, -1, 0));
        BlockPos a = SURFACE.offset(0, -2, 0);
        g.put(a, cell(Block.ICICLE, false, false));
        g.put(a.offset(0, -1, 0), cell(Block.ICICLE, false, false));
        assertThat(SnowRule.melt(g, SURFACE, 8))
                .containsExactly(change(a.offset(0, -1, 0), Kind.REMOVE), change(a, Kind.ICICLE_TO_TIP));
    }

    @Test
    void unknownBlocksDecideNothing() {
        SnowRule.View unknown = new SnowRule.View() {
            @Override
            public Cell at(BlockPos pos) {
                return null;
            }

            @Override
            public int height(int x, int z) {
                return Integer.MIN_VALUE;
            }
        };
        assertThat(SnowRule.accumulate(unknown, SURFACE, new Random(1), 16)).isEmpty();
        assertThat(SnowRule.melt(unknown, SURFACE, 16)).isEmpty();
    }

    @Test
    void theIcicleLengthHashKeepsJavasNegativeRemainder() {
        // TFC: maxLength = 1 + (Helpers.hash(7189237951231L, x, 0, z) % 3); a negative hash gives 0 or -1
        int negatives = 0;
        for (int x = -50; x < 50; x++) {
            int h = SnowRule.hash(SnowRule.ICICLE_LENGTH_SALT, x, 0, 7);
            negatives += h < 0 ? 1 : 0;
            assertThat(1 + h % 3).isBetween(-1, 3);
        }
        assertThat(negatives).isPositive();
    }

    @Test
    void invalidCellsAreRejected() {
        assertThatThrownBy(() -> new Cell(Block.SNOW, 0, false, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Block.SNOW_PILE, 9, false, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Block.ICE, 1, false, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Change change(BlockPos pos, Kind kind) {
        return new Change(pos, kind, 0, false);
    }

    private static Cell cell(Block b, boolean snowSurvives, boolean sky) {
        return new Cell(b, 0, false, snowSurvives, false, false, sky);
    }

    private static Cell water() {
        return new Cell(Block.WATER, 0, true, false, false, true, true);
    }

    /** Air with sky everywhere, except where put; no snow survives on air. Heights are 0 (no icicle search). */
    static final class Grid implements SnowRule.View {
        private final Map<BlockPos, Cell> cells = new HashMap<>();

        Grid put(BlockPos p, Cell c) {
            cells.put(p, c);
            return this;
        }

        /** A solid block at {@code p}; the air above it holds snow. */
        Grid ground(BlockPos p) {
            put(p, new Cell(Block.OTHER, 0, false, false, true, true, false));
            return put(p.offset(0, 1, 0), cell(Block.AIR, true, true));
        }

        @Override
        public Cell at(BlockPos pos) {
            return cells.getOrDefault(pos, cell(Block.AIR, false, true));
        }

        @Override
        public int height(int x, int z) {
            return 0;
        }
    }
}
