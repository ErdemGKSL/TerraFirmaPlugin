/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.fluid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Hand-traced cases of TFC's {@code FluidComponent.fill} and {@code FluidComponent.drain} (src/main/java/net/dries007/tfc/common/
 * component/fluid/FluidComponent.java @ c3b4d9536). Each expected value is read from the TFC branch it names.
 */
class FluidContainerRulesTest {

    private static final String WATER = "minecraft:water";
    private static final String COPPER = "tfc:metal/copper";

    private static FluidAmount f(String fluid, int amount) {
        return new FluidAmount(fluid, amount);
    }

    /** TFC fill, empty content: {@code min(input, capacity)} is the new content and the fill. */
    @Test
    void fillIntoEmptyTakesUpToCapacity() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(FluidAmount.EMPTY, f(WATER, 700), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 700));
        assertThat(fill.filled()).isEqualTo(700);

        fill = FluidContainerRules.fill(FluidAmount.EMPTY, f(WATER, 1500), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 1000));
        assertThat(fill.filled()).isEqualTo(1000);
    }

    /** TFC fill, same fluid: {@code min(content + input, capacity) - content}, so a fill leaves room. */
    @Test
    void fillOfTheSameFluidTakesTheRoomLeft() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(f(WATER, 600), f(WATER, 700), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 1000));
        assertThat(fill.filled()).isEqualTo(400);

        fill = FluidContainerRules.fill(f(WATER, 100), f(WATER, 200), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 300));
        assertThat(fill.filled()).isEqualTo(200);
    }

    /** TFC fill, full container: the room is 0, so nothing is taken. */
    @Test
    void fillOfAFullContainerTakesNothing() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(f(WATER, 1000), f(WATER, 50), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 1000));
        assertThat(fill.filled()).isZero();
    }

    /** TFC fill, a different fluid into a non-empty container: {@code FluidStack.isSameFluidSameComponents} fails, so 0. */
    @Test
    void fillOfAnotherFluidIntoANonEmptyContainerTakesNothing() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(f(WATER, 300), f(COPPER, 100), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 300));
        assertThat(fill.filled()).isZero();
    }

    /** TFC fill, {@code !info.canContainFluid(input)} returns the content unchanged with 0 filled. */
    @Test
    void fillOfAFluidTheContainerRefusesTakesNothing() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(FluidAmount.EMPTY, f(WATER, 100), 1000, false);
        assertThat(fill.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(fill.filled()).isZero();
    }

    /** TFC fill, an empty input is refused before the capacity is read. */
    @Test
    void fillOfNothingTakesNothing() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(f(WATER, 40), FluidAmount.EMPTY, 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 40));
        assertThat(fill.filled()).isZero();
    }

    /** A capacity of 0 fills nothing, and the empty content stays empty (TFC's {@code copyWithAmount(0)} is empty). */
    @Test
    void zeroCapacityFillsNothing() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(FluidAmount.EMPTY, f(WATER, 100), 0, true);
        assertThat(fill.content().isEmpty()).isTrue();
        assertThat(fill.filled()).isZero();
    }

    /**
     * Divergence (ledger): a container over its capacity takes nothing. TFC's {@code FluidComponent.fill} sets the content to
     * the capacity, and reports the negative {@code capacity - content} as the fill, so the fluid is lost.
     */
    @Test
    void overCapacityContainerIsNotShrunkByAFill() {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(f(WATER, 1200), f(WATER, 50), 1000, true);
        assertThat(fill.content()).isEqualTo(f(WATER, 1200));
        assertThat(fill.filled()).isZero();
    }

    /** TFC drain, a part: the content loses the amount, and the drained stack is the amount of the same fluid. */
    @Test
    void partialDrainTakesExactlyTheAmount() {
        FluidContainerRules.Drain drain = FluidContainerRules.drain(f(WATER, 800), 300);
        assertThat(drain.content()).isEqualTo(f(WATER, 500));
        assertThat(drain.drained()).isEqualTo(f(WATER, 300));
    }

    /** TFC drain, more than the content: the container is emptied and the drained stack is all of the content. */
    @Test
    void drainOfMoreThanTheContentEmptiesIt() {
        FluidContainerRules.Drain drain = FluidContainerRules.drain(f(WATER, 800), 900);
        assertThat(drain.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(drain.drained()).isEqualTo(f(WATER, 800));
    }

    /** TFC drain, exactly the content: the branch is {@code amount > content} false, so the content is {@code copyWithAmount(0)}. */
    @Test
    void drainOfExactlyTheContentEmptiesIt() {
        FluidContainerRules.Drain drain = FluidContainerRules.drain(f(WATER, 800), 800);
        assertThat(drain.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(drain.drained()).isEqualTo(f(WATER, 800));
    }

    /** TFC drain, empty content: nothing comes out. */
    @Test
    void drainOfAnEmptyContainerTakesNothing() {
        FluidContainerRules.Drain drain = FluidContainerRules.drain(FluidAmount.EMPTY, 100);
        assertThat(drain.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(drain.drained()).isEqualTo(FluidAmount.EMPTY);
    }

    /** TFC drain of 0 from a non-empty container: the content is the same and nothing is drained. */
    @Test
    void drainOfZeroChangesNothing() {
        FluidContainerRules.Drain drain = FluidContainerRules.drain(f(WATER, 80), 0);
        assertThat(drain.content()).isEqualTo(f(WATER, 80));
        assertThat(drain.drained()).isEqualTo(FluidAmount.EMPTY);
    }

    @Test
    void negativeArgumentsAreRejected() {
        assertThatThrownBy(() -> FluidContainerRules.fill(FluidAmount.EMPTY, f(WATER, 1), -1, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FluidContainerRules.drain(f(WATER, 1), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** Over many seeded cases, a fill never exceeds the capacity and moves exactly what it reports, and a drain conserves. */
    @Test
    void fillAndDrainConserveTheFluid() {
        Random random = new Random(0x7fcL);
        String[] fluids = {WATER, COPPER};
        for (int i = 0; i < 5000; i++) {
            int capacity = random.nextInt(5000);
            FluidAmount content = random.nextInt(4) == 0 ? FluidAmount.EMPTY
                : f(fluids[random.nextInt(2)], random.nextInt(capacity + 1));
            FluidAmount input = f(fluids[random.nextInt(2)], random.nextInt(6000));
            boolean canContain = random.nextBoolean();

            FluidContainerRules.Fill fill = FluidContainerRules.fill(content, input, capacity, canContain);
            assertThat(fill.content().amount()).isLessThanOrEqualTo(Math.max(capacity, content.amount()));
            assertThat(fill.content().amount() - content.amount()).isEqualTo(fill.filled());
            assertThat(fill.filled()).isBetween(0, input.amount());

            int take = random.nextInt(6000);
            FluidContainerRules.Drain drain = FluidContainerRules.drain(fill.content(), take);
            assertThat(drain.content().amount() + drain.drained().amount()).isEqualTo(fill.content().amount());
            assertThat(drain.drained().amount()).isEqualTo(Math.min(take, fill.content().amount()));
        }
    }
}
