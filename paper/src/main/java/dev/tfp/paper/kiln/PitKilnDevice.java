/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (STAGE 0..16,
 *       InventoryRemoveBehavior.DROP, updateShape),
 *       src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (ejectInventory: the
 *       contents, and the straw and logs only while unlit)
 */
package dev.tfp.paper.kiln;

import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.placed.PlacedItemEffects;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The pit kiln as a device kind (PAR-INFRA-02): the CraftEngine block {@value #ID} with the property {@value #STAGE}
 * ({@code 0..16}, TFC's {@code PIT_KILN_STAGE}) and the {@link PitKilnState}. Twenty slots of one item: the four contents
 * ({@code PlacedItemBlockEntity}), eight straw and eight logs. {@code DROP}: TFC drops the straw and logs only while unlit;
 * here lighting clears them (they burn), so dropping every slot is the same.
 *
 * <p>The handler only reacts to a neighbour update by handing the kiln to {@code evaluate} on its region (TFC
 * {@code updateShape} and the checks of {@code serverTick}), and removes the content displays and the clock entry with
 * the block. Clicks come from {@link PitKilnListener}; the record changes through {@link PitKilns}.
 */
public final class PitKilnDevice {
    /** The CraftEngine block id. */
    public static final String ID = "tfp:pit_kiln";
    /** The stage property. */
    public static final String STAGE = "stage";

    private PitKilnDevice() {
    }

    /** The block state at {@code stage}. */
    public static BlockStateRef state(int stage) {
        if (stage < 0 || stage > PitKilnLayers.LIT) {
            throw new IllegalArgumentException("stage must be 0.." + PitKilnLayers.LIT + ": " + stage);
        }
        return BlockStateRef.of(ID).with(STAGE, Integer.toString(stage));
    }

    /** The stage of a kiln block state; {@code -1} when it is not a kiln or the property is unreadable. */
    public static int stage(BlockStateRef state) {
        if (state == null || !state.id().equals(ID)) {
            return -1;
        }
        try {
            int s = Integer.parseInt(state.properties().getOrDefault(STAGE, "0"));
            return s < 0 || s > PitKilnLayers.LIT ? -1 : s;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @param contents the content displays (shared with the placed item block)
     * @param later    hands a kiln to {@code evaluate} on its region, after the current callback
     * @param clock    the lit-kiln clock
     */
    public static DeviceKind<PitKilnState> kind(PlacedItemEffects contents, Consumer<org.bukkit.block.Block> later,
            KilnClock clock) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(later, "later");
        Objects.requireNonNull(clock, "clock");
        return DeviceKind.builder(ID, PitKilnState.CODEC, PitKilnState.FRESH)
            .slots(PitKilnState.SLOTS)
            .slotLimit(1)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new DeviceHandler<>() {
                @Override
                public DeviceResult<PitKilnState> neighbourChanged(DeviceContext<PitKilnState> ctx) {
                    later.accept(ctx.block());
                    return DeviceResult.pass();
                }

                @Override
                public void removed(DeviceContext<PitKilnState> ctx) {
                    contents.hide(ctx.block());
                    clock.untrack(ctx.block());
                }
            })
            .build();
    }
}
