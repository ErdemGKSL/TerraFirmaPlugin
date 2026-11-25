/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrappedChestBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java @ c3b4d9536 (18 slots, isValid)
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.chest.ChestState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * PAR-DECO-04: TFC's chest and trapped chest as device kinds {@code tfp:wood/chest} and {@code tfp:wood/trapped_chest}
 * (each covers its 20 woods by prefix): 18 slots of items up to the chest's size ({@code TFCChestBlockEntity.isValid}),
 * the contents dropped when the block is broken ({@code InventoryBlockEntity} / vanilla {@code Containers.dropContents}).
 *
 * <p>A right click without sneaking (or sneaking with an empty main hand) opens the screen, unless a solid block or a sitting
 * cat lies above the half ({@code ChestBlock.isChestBlockedAt}).
 */
public final class ChestDevice {
    public static final int SLOTS = ChestRules.SINGLE_SLOTS;

    private ChestDevice() {
    }

    /** The kind of one chest block family ({@link ChestBlocks#CHEST} or {@link ChestBlocks#TRAPPED_CHEST}). */
    public static DeviceKind<ChestState> kind(String id, Predicate<ItemStack> fits, BiConsumer<Player, Block> open,
            ChestPlacement placement) {
        return DeviceKind.builder(id, ChestState.CODEC, ChestState.INSTANCE)
            .slots(SLOTS)
            .filter((slot, stack) -> fits.test(stack))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(open, placement))
            .build();
    }

    /**
     * Vanilla {@code ChestBlock.isChestBlockedAt}: a full occluding block above (a {@code isRedstoneConductor} check; the
     * occluding test is its stand-in) or a sitting cat in the block above ({@code isCatSittingOnChest}: an ocelot is not
     * a cat and does not block). Region thread of the chest.
     */
    static boolean blocked(Block chest) {
        Material above = chest.getRelative(0, 1, 0).getType();
        return above.isOccluding() || catSitting(chest);
    }

    static boolean catSitting(Block chest) {
        BoundingBox box = new BoundingBox(chest.getX(), chest.getY() + 1, chest.getZ(),
            chest.getX() + 1, chest.getY() + 2, chest.getZ() + 1);
        for (Entity e : chest.getWorld().getNearbyEntities(box, entity -> entity instanceof Cat)) {
            if (((Cat) e).isSitting()) {
                return true;
            }
        }
        return false;
    }

    private record Handler(BiConsumer<Player, Block> open, ChestPlacement placement) implements DeviceHandler<ChestState> {
        @Override
        public DeviceResult<ChestState> useWithItem(DeviceContext<ChestState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() != EquipmentSlot.HAND) {
                return DeviceResult.pass();
            }
            if (player.isSneaking() && !StackCodec.isEmpty(ctx.item())) {
                return DeviceResult.pass(); // a sneaking player with an item in hand places it against the chest
            }
            if (!blocked(ctx.block())) {
                open.accept(player, ctx.block());
            }
            return DeviceResult.consume();
        }

        @Override
        public void removed(DeviceContext<ChestState> ctx) {
            placement.removed(ctx.block());
        }
    }
}
