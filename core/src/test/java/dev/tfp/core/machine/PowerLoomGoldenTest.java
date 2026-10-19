/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (powerLoomTick: the
 *       progressTicker, PROGRESS_FOR_ONE_PUSH, the push conditions, the reset, the loom tick after the push),
 * src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (tick: the 20-tick gap before a step applies)
 *
 * The traces below step one game tick at a time from game time 1, the first tick with a rotation, and the expected pushes and
 * finish tick are worked out by hand from those lines.
 */
package dev.tfp.core.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.craft.LoomRecipe;
import dev.tfp.core.craft.LoomRules;
import dev.tfp.core.machine.PowerLoomRules.State;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PowerLoomGoldenTest {

    /** Two strings woven in three steps. */
    private static final LoomRecipe RECIPE = new LoomRecipe("tfc:loom/test", "minecraft:string", 2,
        new CraftOutput("minecraft:white_wool", 1), 3, "tfc:item/loom/test");

    /** The pushes and the finish tick of a run from {@code from} to {@code to}, with the state after the run. */
    private record Run(List<Long> pushes, long finishedAt, State state) {}

    private static Run run(LoomRecipe recipe, int slots, Rotation rotation, State start, long from, long to) {
        State state = start;
        final List<Long> pushes = new ArrayList<>();
        long finishedAt = -1;
        for (long t = from; t <= to; t++) {
            final PowerLoomRules.Tick tick = PowerLoomRules.tick(recipe, slots, rotation, state, t);
            if (tick.pushed()) {
                pushes.add(t);
            }
            if (tick.finished()) {
                finishedAt = t;
            }
            state = tick.state();
        }
        return new Run(pushes, finishedAt, state);
    }

    /**
     * TFC {@code PROGRESS_FOR_ONE_PUSH = Mth.TWO_PI / 4f}, the step count and the ingredient count of the recipe: at 0.1 rad per tick
     * the ticker passes a quarter turn on the sixteenth tick. The push at 16 applies at 36 (20 ticks later), so the push at 32 is
     * refused by the pending step (the ticker still resets), and the next push at 48 applies at 68. The push at 80 applies at 100, the
     * third step, which finishes the weave.
     */
    @Test
    void pushesEverySixteenTicksAtTenthRadianSpeedAndFinishesAtOneHundred() {
        assertThat(PowerLoomRules.PROGRESS_FOR_ONE_PUSH).isCloseTo((float) (Math.PI / 2), within(1e-6f));
        final Run run = run(RECIPE, 2, Rotation.of(Direction.EAST, 0.1f), State.NEW, 1, 100);
        assertThat(run.pushes()).containsExactly(16L, 48L, 80L);
        assertThat(run.finishedAt()).isEqualTo(100L);
        assertThat(run.state().loom().progress()).isEqualTo(3);
        assertThat(run.state().loom().pending()).isFalse();
    }

    /**
     * TFC {@code powerLoomTick}: the ticker adds {@code Mth.abs(rotation.speed())}, so a reversed rotation weaves the same way.
     */
    @Test
    void aReversedRotationPushesTheSameWay() {
        final Run forward = run(RECIPE, 2, Rotation.of(Direction.EAST, 0.1f), State.NEW, 1, 100);
        final Run reverse = run(RECIPE, 2, Rotation.of(Direction.EAST, -0.1f), State.NEW, 1, 100);
        assertThat(reverse.pushes()).isEqualTo(forward.pushes());
        assertThat(reverse.finishedAt()).isEqualTo(forward.finishedAt());
    }

    /**
     * TFC {@code powerLoomTick} at 0.5 rad per tick: the ticker passes a quarter turn every fourth tick. The push at 4 applies at 24,
     * which is refused because the step of the push at 4 is still pending until the loom tick of 24 (the loom tick runs after the push check). The push at 28 applies at 48 and the
     * push at 52 applies at 72, the third step.
     */
    @Test
    void halfRadianSpeedPushesEveryFourTicksUntilTheStepIsApplied() {
        final Run run = run(RECIPE, 2, Rotation.of(Direction.EAST, 0.5f), State.NEW, 1, 72);
        assertThat(run.pushes()).containsExactly(4L, 28L, 52L);
        assertThat(run.finishedAt()).isEqualTo(72L);
    }

    /**
     * TFC {@code powerLoomTick}: a push needs the recipe, and the loom must hold exactly the recipe's ingredient count. A refused
     * push still resets the ticker, so with no recipe, or a wrong count, the ticker passes its quarter turn on the sixteenth tick
     * and goes back to zero there, with nothing woven.
     */
    @Test
    void aRefusedPushStillResetsTheTicker() {
        final Rotation rotation = Rotation.of(Direction.EAST, 0.1f);
        final Run noRecipe = run(null, 2, rotation, State.NEW, 1, 15);
        assertThat(noRecipe.state().progressTicker()).isCloseTo(1.5f, within(1e-4f));
        final Run noRecipeAtSixteen = run(null, 2, rotation, State.NEW, 1, 16);
        assertThat(noRecipeAtSixteen.state().progressTicker()).isZero();
        assertThat(noRecipeAtSixteen.pushes()).isEmpty();

        final Run wrongCount = run(RECIPE, 1, rotation, State.NEW, 1, 16);
        assertThat(wrongCount.pushes()).isEmpty();
        assertThat(wrongCount.state().progressTicker()).isZero();
    }

    /**
     * TFC {@code powerLoomTick}: the loom tick runs whether or not there is a rotation, so a step pushed earlier still lands on the
     * twentieth tick after the push, and the ticker does not move without a rotation.
     */
    @Test
    void aPendingStepLandsWithoutARotation() {
        final State pending = new State(0f, new LoomRules.State(0, true, 10L));
        final Run run = run(RECIPE, 2, null, pending, 11, 30);
        assertThat(run.state().loom().progress()).isEqualTo(1);
        assertThat(run.state().loom().pending()).isFalse();
        assertThat(run.state().progressTicker()).isZero();
    }
}
