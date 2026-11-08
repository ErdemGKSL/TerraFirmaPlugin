/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenBlockHandlerAndItem,
 * transferBetweenItemAndOther)
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelFluidTransfer.ItemTank;
import dev.tfp.core.barrel.BarrelFluidTransfer.Mode;
import dev.tfp.core.fluid.FluidTransferRules.BlockTransfer;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class BarrelFluidTransferTest {
    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;
    private static final Predicate<String> ANY = f -> true;
    private static final Predicate<String> NO_LAVA = f -> !f.equals("minecraft:lava");

    private static FluidAmount f(String id, int mb) {
        return new FluidAmount(id, mb);
    }

    private static Optional<BarrelFluidTransfer.Result> run(FluidAmount barrel, ItemTank item) {
        return BarrelFluidTransfer.transfer(barrel, false, NO_LAVA, CAP, item);
    }

    @Test
    void aFullWoodenBucketEmptiesIntoTheBarrel() {
        BarrelFluidTransfer.Result r = run(FluidAmount.EMPTY, new ItemTank(f("tfp:brine", 1000), 1000, Mode.TANK, ANY))
            .orElseThrow();
        assertThat(r.direction()).isEqualTo(BlockTransfer.DRAIN_ITEM);
        assertThat(r.barrel()).isEqualTo(f("tfp:brine", 1000));
        assertThat(r.item()).isEqualTo(FluidAmount.EMPTY);
    }

    @Test
    void aTankGivesOnlyWhatFitsAndKeepsTheRest() {
        BarrelFluidTransfer.Result r = run(f("tfp:brine", 9700), new ItemTank(f("tfp:brine", 1000), 1000, Mode.TANK, ANY))
            .orElseThrow();
        assertThat(r.barrel()).isEqualTo(f("tfp:brine", 10_000));
        assertThat(r.item()).isEqualTo(f("tfp:brine", 700));
        assertThat(r.moved()).isEqualTo(f("tfp:brine", 300));
    }

    @Test
    void aVanillaBucketEmptiesWholeAndWastesWhatDoesNotFit() {
        BarrelFluidTransfer.Result r = run(f("minecraft:water", 9700),
            new ItemTank(f("minecraft:water", 1000), 1000, Mode.BUCKET, ANY)).orElseThrow();
        assertThat(r.barrel()).isEqualTo(f("minecraft:water", 10_000));
        assertThat(r.item()).as("TFC attempt 2: the whole bucket, the rest lost").isEqualTo(FluidAmount.EMPTY);
    }

    @Test
    void anEmptyContainerTakesFromTheBarrel() {
        BarrelFluidTransfer.Result r = run(f("tfp:brine", 5000), new ItemTank(FluidAmount.EMPTY, 100, Mode.TANK, ANY))
            .orElseThrow();
        assertThat(r.direction()).isEqualTo(BlockTransfer.FILL_ITEM);
        assertThat(r.item()).isEqualTo(f("tfp:brine", 100));
        assertThat(r.barrel()).isEqualTo(f("tfp:brine", 4900));
    }

    @Test
    void anEmptyVanillaBucketNeedsAWholeBucket() {
        assertThat(run(f("minecraft:water", 999), new ItemTank(FluidAmount.EMPTY, 1000, Mode.BUCKET, ANY))).isEmpty();
        BarrelFluidTransfer.Result r = run(f("minecraft:water", 1500),
            new ItemTank(FluidAmount.EMPTY, 1000, Mode.BUCKET, ANY)).orElseThrow();
        assertThat(r.item()).isEqualTo(f("minecraft:water", 1000));
        assertThat(r.barrel()).isEqualTo(f("minecraft:water", 500));
    }

    @Test
    void aContainerThatCannotHoldTheFluidTakesNothing() {
        assertThat(run(f("tfp:brine", 5000), new ItemTank(FluidAmount.EMPTY, 1000, Mode.BUCKET,
            id -> id.equals("minecraft:water")))).isEmpty();
    }

    @Test
    void anotherFluidIsRefusedBothWays() {
        // the barrel takes no vinegar into brine, and the vinegar jug takes no brine
        assertThat(run(f("tfp:brine", 5000), new ItemTank(f("tfp:vinegar", 50), 100, Mode.TANK, ANY))).isEmpty();
    }

    @Test
    void aFullBarrelFillsTheHeldContainerOfTheSameFluid() {
        BarrelFluidTransfer.Result r = run(f("tfp:brine", 10_000), new ItemTank(f("tfp:brine", 40), 100, Mode.TANK, ANY))
            .orElseThrow();
        assertThat(r.direction()).as("the block accepts none, so it fills the item").isEqualTo(BlockTransfer.FILL_ITEM);
        assertThat(r.item()).isEqualTo(f("tfp:brine", 100));
        assertThat(r.barrel()).isEqualTo(f("tfp:brine", 9940));
    }

    @Test
    void theBarrelRefusesFluidsOutsideItsTagAndNothingMovesWhileSealed() {
        assertThat(run(FluidAmount.EMPTY, new ItemTank(f("minecraft:lava", 1000), 1000, Mode.BUCKET, ANY))).isEmpty();
        assertThat(BarrelFluidTransfer.transfer(FluidAmount.EMPTY, true, ANY, CAP,
            new ItemTank(f("tfp:brine", 1000), 1000, Mode.TANK, ANY))).isEmpty();
        assertThat(BarrelFluidTransfer.transfer(f("tfp:brine", 1000), true, ANY, CAP,
            new ItemTank(FluidAmount.EMPTY, 1000, Mode.TANK, ANY))).isEmpty();
    }

    @Test
    void assemblyCountsFollowTfc() {
        // 10 items, 1000 mB, 100 mB + 1 item per multiple, 500 mB output of the same fluid
        assertThat(BarrelAssembly.multiplier(1000, 100, 10, 1, 500, true, CAP)).as("room 9000 / 500").isEqualTo(10);
        assertThat(BarrelAssembly.multiplier(1000, 100, 10, 1, 5000, true, CAP)).isEqualTo(1);
        assertThat(BarrelAssembly.multiplier(1000, 100, 3, 1, 0, false, CAP)).isEqualTo(3);
        assertThat(BarrelAssembly.multiplier(1000, 300, 0, 0, 0, false, CAP)).isEqualTo(3);
        assertThat(BarrelAssembly.retainedFluid(1000, 300, 3)).isEqualTo(100);
        assertThat(BarrelAssembly.remainingItems(10, 4, 2)).isEqualTo(2);
        assertThat(BarrelAssembly.outputFluid(9000, 500, 10, true, CAP)).isEqualTo(CAP);
        assertThat(BarrelAssembly.moreFluidThanItems(true, 125, 125, 2, 1)).isFalse();
        assertThat(BarrelAssembly.moreFluidThanItems(false, 125, 125, 2, 1)).isTrue();
        assertThat(BarrelAssembly.moreItemsThanFluid(true, 1000, 100, 5, 1)).as("10 fluid multiples, 5 items").isFalse();
        assertThat(BarrelAssembly.moreItemsThanFluid(false, 1000, 100, 5, 1)).isTrue();
        assertThat(BarrelAssembly.instantFluidMultiplier(9000, 9, 1000, 1)).isEqualTo(1000);
        assertThat(BarrelAssembly.instantFluidOutput(10, 1000, CAP)).isEqualTo(CAP);
    }
}
