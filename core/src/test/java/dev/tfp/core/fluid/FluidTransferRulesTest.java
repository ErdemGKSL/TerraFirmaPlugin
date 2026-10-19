/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.fluid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.fluid.FluidTransferRules.BlockTransfer;
import dev.tfp.core.fluid.FluidTransferRules.WorldAttempt;
import org.junit.jupiter.api.Test;

/**
 * The decisions of TFC's {@code FluidHelpers.transferBetweenWorldAndItem} and {@code transferBetweenBlockHandlerAndItem}
 * (src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536).
 */
class FluidTransferRulesTest {

    /** TFC: an empty item tries a pickup, whether or not placing is allowed. */
    @Test
    void emptyItemTriesPickup() {
        assertThat(FluidTransferRules.worldAttempt(true, false)).isEqualTo(WorldAttempt.PICKUP);
        assertThat(FluidTransferRules.worldAttempt(true, true)).isEqualTo(WorldAttempt.PICKUP);
    }

    /** TFC: a full item places its fluid only when {@code allowPlacingAnyLiquidBlocks} is set. */
    @Test
    void fullItemPlacesOnlyWhenAllowed() {
        assertThat(FluidTransferRules.worldAttempt(false, true)).isEqualTo(WorldAttempt.PLACE);
        assertThat(FluidTransferRules.worldAttempt(false, false)).isEqualTo(WorldAttempt.NONE);
    }

    /** TFC: an empty item fills from the block's tank, even if the block accepts nothing. */
    @Test
    void emptyItemFillsFromTheBlock() {
        assertThat(FluidTransferRules.blockTransfer(true, 0)).isEqualTo(BlockTransfer.FILL_ITEM);
        assertThat(FluidTransferRules.blockTransfer(true, 500)).isEqualTo(BlockTransfer.FILL_ITEM);
    }

    /** TFC: a full item fills from the block when the block accepts none of its fluid. */
    @Test
    void fullItemIntoAFullBlockFillsFromTheBlock() {
        assertThat(FluidTransferRules.blockTransfer(false, 0)).isEqualTo(BlockTransfer.FILL_ITEM);
    }

    /** TFC: a full item drains into the block when the block accepts some of its fluid. */
    @Test
    void fullItemDrainsIntoABlockThatAcceptsIt() {
        assertThat(FluidTransferRules.blockTransfer(false, 300)).isEqualTo(BlockTransfer.DRAIN_ITEM);
    }

    @Test
    void negativeFillSimulationIsRejected() {
        assertThatThrownBy(() -> FluidTransferRules.blockTransfer(false, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
