/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 *       getSlotStackLimit, serverTick: the casting leaves the mold once it is solid),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java @ c3b4d9536 (useItemOn, onRemove)
 */
package dev.tfp.paper.channel;

import dev.tfp.core.channel.MoldTableData;
import dev.tfp.core.channel.MoldTableRules;
import dev.tfp.core.channel.MoldTableRules.Click;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.mold.MoldItems;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The mold table as a device kind (PAR-METAL-13): two slots (the mold, the casting), one item each; the flow it is fed by as
 * its state. The casting leaves a solid mold lazily on the next access ({@code advance}); a right click is TFC's
 * {@code onRightClick} ({@link MoldTableRules#click}); broken, it drops both slots and ends its flow.
 */
final class MoldTableDevice {
    static final String ID = ChannelWorld.TABLE;

    private final MoldItems molds;
    private final PourService pour;
    private final ChannelWorld world;
    private final StackCodec stacks;
    private final DeviceKind<MoldTableData> kind;

    MoldTableDevice(ChannelWorld world, PourService pour, MoldItems molds, StackCodec stacks) {
        this.world = Objects.requireNonNull(world, "world");
        this.pour = Objects.requireNonNull(pour, "pour");
        this.molds = Objects.requireNonNull(molds, "molds");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, MoldTableData.CODEC, MoldTableData.IDLE)
            .slots(2)
            .slotLimit(MoldTableRules.SLOT_LIMIT)
            .filter(this::isItemValid)
            .advance(this::solidify)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    DeviceKind<MoldTableData> kind() {
        return kind;
    }

    /** TFC {@code isItemValid}: the mold slot takes what is usable in a mold table (the fired molds); the output takes anything. */
    boolean isItemValid(int slot, ItemStack stack) {
        return slot != MoldTableRules.MOLD_SLOT || molds.isMold(stack);
    }

    /**
     * TFC {@code serverTick}, "move results from mold item to output stack": a mold that is no longer molten and has a casting
     * recipe gives its casting to the output slot (an empty one) and is drained. Replayed whenever the table is accessed.
     */
    DeviceRecord<MoldTableData> solidify(DeviceRecord<MoldTableData> record, long from, long to) {
        if (!record.slot(MoldTableRules.OUTPUT_SLOT).isEmpty() || record.slot(MoldTableRules.MOLD_SLOT).isEmpty()) {
            return record;
        }
        ItemStack mold = stacks.decode(record.slot(MoldTableRules.MOLD_SLOT));
        if (!molds.isMold(mold) || molds.content(mold).isEmpty() || molds.isMolten(mold)) {
            return record;
        }
        Optional<MoldItems.Cast> cast = molds.cast(mold);
        if (cast.isEmpty() || !molds.drain(mold)) {
            return record;
        }
        return record.withSlot(MoldTableRules.MOLD_SLOT, stacks.encode(mold))
            .withSlot(MoldTableRules.OUTPUT_SLOT, stacks.encode(cast.get().result()));
    }

    private final class Handler implements DeviceHandler<MoldTableData> {
        /** TFC {@code useItemOn}: {@code onRightClick}. */
        @Override
        public DeviceResult<MoldTableData> useWithItem(DeviceContext<MoldTableData> ctx) {
            Player player = ctx.player();
            EquipmentSlot hand = ctx.hand();
            if (player == null || hand == null) {
                return DeviceResult.pass();
            }
            DeviceRecord<MoldTableData> record = ctx.record();
            ItemStack mold = stacks.decode(record.slot(MoldTableRules.MOLD_SLOT));
            ItemStack output = stacks.decode(record.slot(MoldTableRules.OUTPUT_SLOT));
            ItemStack held = player.getInventory().getItem(hand);
            boolean heldValid = !StackCodec.isEmpty(held) && molds.isMold(held);
            Click click = MoldTableRules.click(player.isSneaking(), !StackCodec.isEmpty(mold), !StackCodec.isEmpty(output),
                heldValid);
            if (click == Click.PASS) {
                return DeviceResult.pass();
            }
            DeviceRecord<MoldTableData> next = record;
            switch (click) {
                case SWAP, TAKE_MOLD, INSERT -> {
                    if (!StackCodec.isEmpty(mold)) {
                        give(player, mold);
                        next = next.withSlot(MoldTableRules.MOLD_SLOT, stacks.encode(ItemStack.empty()));
                    }
                    if (click == Click.SWAP || click == Click.INSERT) {
                        ItemStack one = held.clone();
                        one.setAmount(1);
                        next = next.withSlot(MoldTableRules.MOLD_SLOT, stacks.encode(one));
                        player.getInventory().setItem(hand, held.getAmount() > 1 ? held.subtract(1) : null);
                    }
                    if (click != Click.INSERT && !StackCodec.isEmpty(output)) {
                        give(player, output);
                        next = next.withSlot(MoldTableRules.OUTPUT_SLOT, stacks.encode(ItemStack.empty()));
                    }
                }
                case TAKE_OUTPUT -> {
                    give(player, output);
                    next = next.withSlot(MoldTableRules.OUTPUT_SLOT, stacks.encode(ItemStack.empty()));
                }
                default -> {
                }
            }
            return DeviceResult.consume(next);
        }

        @Override
        public void removed(DeviceContext<MoldTableData> ctx) {
            Block block = ctx.block();
            MoldTableData data = ctx.deviceState();
            world.later(block, () -> {
                MoldTableDisplays.clear(block);
                pour.tableRemoved(block, data);
            });
        }
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest at the player's feet. */
    static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack.clone()).values()
            .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
