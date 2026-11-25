/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536 (useItemOn, updateShape, tick,
 *       canSurvive, onRemove),
 *       src/main/java/net/dries007/tfc/common/blockentities/IngotPileBlockEntity.java @ c3b4d9536 (entries)
 */
package dev.tfp.paper.pile;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.pile.PileKind;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * An ingot pile or a double ingot pile as a device kind (PAR-METAL-16): one slot per ingot of the capacity (64, 36), one item
 * each, no state of its own (TFC keeps only the ingots in the block entity). A plain use takes the top ingot; a neighbour
 * change checks the support; the removal pops every ingot. Adding an ingot is a sneak use of the item, which the
 * {@link PileListener} handles (it knows the clicked face).
 */
final class PileDevice {
    /** No state: the slots are the pile. */
    static final VersionedCodec<Boolean> CODEC = VersionedCodec.<Boolean>builder("ingot_pile", 1)
        .build((v, out) -> out.putInt("pile", 0), in -> Boolean.TRUE);

    private PileDevice() {
    }

    static DeviceKind<Boolean> kind(PileKind kind, PileService service) {
        return DeviceKind.builder(PileService.id(kind), CODEC, Boolean.TRUE)
            .slots(kind.capacity())
            .slotLimit(1)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(service))
            .build();
    }

    private record Handler(PileService service) implements DeviceHandler<Boolean> {
        /** TFC {@code useItemOn}: without sneaking, whatever the hand holds, the top ingot comes off; sneaking passes. */
        @Override
        public DeviceResult<Boolean> useWithItem(DeviceContext<Boolean> ctx) {
            Player player = ctx.player();
            if (player == null || player.isSneaking()) {
                return DeviceResult.pass();
            }
            DeviceRecord<Boolean> next = service.remove(ctx.block(), ctx.record(), player);
            return next == null ? DeviceResult.pass() : DeviceResult.consume(next);
        }

        /** TFC {@code updateShape} (schedules a tick) and {@code tick}: a pile with nothing sturdy below pops off. */
        @Override
        public DeviceResult<Boolean> neighbourChanged(DeviceContext<Boolean> ctx) {
            service.checkSupport(ctx.block());
            return DeviceResult.pass();
        }

        /** TFC {@code onRemove}: the ingots pop out (voided when a creative player broke the pile). */
        @Override
        public void removed(DeviceContext<Boolean> ctx) {
            service.removed(ctx.block(), ctx.inventory().contents().stream().map(ItemStack::clone).toList());
        }
    }
}
