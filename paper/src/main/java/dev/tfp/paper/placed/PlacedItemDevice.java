/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (four slots of one item)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import java.util.Objects;

/**
 * The placed item block as a device kind (PAR-INFRA-02): TFC's {@code PlacedItemBlock} + {@code PlacedItemBlockEntity}.
 * Four slots of one item each, the {@link PlacedState} (large flag, rotations), the slots dropped when the block goes
 * ({@code DROP}). Insertion and take-back go through {@link PlacedItems} (they need the hit position, which the device
 * callbacks do not carry); the handler only removes the displays with the block.
 */
public final class PlacedItemDevice {
    /** The CraftEngine block id (datagen {@code placed/PlacedItemBlock}). */
    public static final String ID = "tfp:placed_item";

    private PlacedItemDevice() {
    }

    public static DeviceKind<PlacedState> kind(PlacedItemEffects effects) {
        Objects.requireNonNull(effects, "effects");
        return DeviceKind.builder(ID, PlacedState.CODEC, PlacedState.EMPTY)
            .slots(PlacedRules.SLOTS)
            .slotLimit(1)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new DeviceHandler<>() {
                @Override
                public void removed(DeviceContext<PlacedState> ctx) {
                    effects.hide(ctx.block());
                }
            })
            .build();
    }
}
