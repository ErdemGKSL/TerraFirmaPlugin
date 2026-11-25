/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ToolRackBlockEntity.java @ c3b4d9536 (4 slots, isItemValid, getSlotStackLimit)
 * src/main/java/net/dries007/tfc/common/blockentities/BookshelfBlockEntity.java @ c3b4d9536 (vanilla ChiseledBookShelfBlockEntity: 6 slots)
 */
package dev.tfp.paper.rack;

import dev.tfp.core.rack.BookshelfRules;
import dev.tfp.core.rack.RackState;
import dev.tfp.core.rack.ToolRackRules;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DECO-05: the tool rack (4 slots, one tool each, TFC's {@code usable_on_tool_rack} tag) and the bookshelf (6 slots, one
 * book each, the {@code bookshelf_books} tag) as device kinds {@code tfp:wood/tool_rack} and {@code tfp:wood/bookshelf}
 * (each covers its 20 woods by prefix). The contents drop when the block is broken (TFC {@code InventoryRemoveBehavior.DROP}).
 * The clicks are {@link RackInteractions}: the device framework's callbacks do not carry the hit position.
 */
public final class RackDevices {
    private RackDevices() {
    }

    public static DeviceKind<RackState> toolRack(Predicate<ItemStack> isTool, Consumer<Block> removed) {
        return DeviceKind.builder(RackBlocks.TOOL_RACK, RackState.CODEC, RackState.INSTANCE)
            .slots(ToolRackRules.SLOTS)
            .slotLimit(ToolRackRules.SLOT_LIMIT)
            .filter((slot, stack) -> isTool.test(stack))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Removal(removed))
            .build();
    }

    public static DeviceKind<RackState> bookshelf(Predicate<ItemStack> isBook) {
        return DeviceKind.builder(RackBlocks.BOOKSHELF, RackState.CODEC, RackState.INSTANCE)
            .slots(BookshelfRules.SLOTS)
            .slotLimit(1)
            .filter((slot, stack) -> isBook.test(stack))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .build();
    }

    private record Removal(Consumer<Block> removed) implements DeviceHandler<RackState> {
        @Override
        public void removed(DeviceContext<RackState> ctx) {
            removed.accept(ctx.block());
        }
    }
}
