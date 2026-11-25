/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/network/PlaceBlockSpecialPacket.java @ c3b4d9536 (handle: top face, existing placed
 *       item first, else a new block over an empty space when the targeted quadrant is supported),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (onRightClick,
 *       insertItem: stack.split(1), a copy in creative; giveItemToPlayer; updateBlock removes an empty block)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.item.ContainerRules;
import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Placing, inserting and taking back placed items, on the block's region thread (the caller checks ownership). Each step
 * is one block and one device edit, decided and written inline: the rules are O(1) ({@link PlacedRules}).
 */
public final class PlacedItems {
    /** TFC {@code updateStateValues}: which quadrants the block below supports. Region thread. */
    @FunctionalInterface
    public interface Support {
        boolean[] supported(Block below);
    }

    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final DeviceKind<PlacedState> kind;
    private final Function<ItemStack, ContainerRules.PlacedSlot> sizes;
    private final Support support;
    private final PlacedItemEffects effects;

    /**
     * @param sizes the item's class against {@code maxPlacedItemSize} / {@code maxPlacedLargeItemSize}
     *              ({@link ContainerRules#placed})
     */
    public PlacedItems(CustomBlocks blocks, DeviceService devices, DeviceKind<PlacedState> kind,
            Function<ItemStack, ContainerRules.PlacedSlot> sizes, Support support, PlacedItemEffects effects) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sizes = Objects.requireNonNull(sizes, "sizes");
        this.support = Objects.requireNonNull(support, "support");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** Whether {@code block} is a placed item block. */
    public boolean isPlaced(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(PlacedItemDevice.ID)).orElse(false);
    }

    /**
     * Whether a placement on the top face of {@code clicked} at {@code (x, z)} could succeed with {@code held}: the space
     * above is empty, the targeted quadrant is supported and the item fits (TFC's checks, without changing anything).
     */
    public boolean canPlace(Block clicked, double x, double z, ItemStack held, float yaw) {
        if (StackCodec.isEmpty(held) || !clicked.getRelative(BlockFace.UP).isEmpty()) {
            return false;
        }
        boolean[] sup = support.supported(clicked);
        int slot = PlacedRules.slotSelected(x, z);
        return sup[slot] && PlacedRules.insert(PlacedState.EMPTY, new boolean[PlacedRules.SLOTS], slot, sizes.apply(held),
            PlacedRules.isFullContents(sup), yaw).isPresent();
    }

    /**
     * TFC {@code PlaceBlockSpecialPacket} on a solid surface: a new placed item block above {@code clicked} holding one item
     * of the stack in {@code hand}.
     *
     * @return whether an item was placed
     */
    public boolean place(Player player, EquipmentSlot hand, Block clicked, double x, double z) {
        ItemStack held = player.getInventory().getItem(hand);
        if (!canPlace(clicked, x, z, held, player.getYaw())) {
            return false;
        }
        Block above = clicked.getRelative(BlockFace.UP);
        if (!blocks.place(above, BlockStateRef.of(PlacedItemDevice.ID), true)) {
            return false;
        }
        if (insert(player, hand, above, x, z)) {
            return true;
        }
        blocks.remove(above); // refused after all: no empty block stays
        effects.hide(above);
        return false;
    }

    /**
     * TFC {@code insertItem} on an existing placed item block: one item of the stack in {@code hand} into the slot under
     * {@code (x, z)}, or alone into slot 0 when it is large.
     *
     * @return whether an item was inserted
     */
    public boolean insert(Player player, EquipmentSlot hand, Block block, double x, double z) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return false;
        }
        boolean[] sup = support.supported(block.getRelative(BlockFace.DOWN));
        int slot = PlacedRules.slotSelected(x, z);
        ContainerRules.PlacedSlot size = sizes.apply(held);
        ItemStack one = held.clone();
        one.setAmount(1);
        float yaw = player.getYaw();
        int[] inserted = {-1};
        Optional<DeviceRecord<PlacedState>> stored = devices.edit(block, kind, (record, now) -> {
            DeviceInventory<PlacedState> inv = new DeviceInventory<>(kind, record, devices.stacks());
            Optional<PlacedRules.Insert> in = PlacedRules.insert(record.state(), occupied(inv), slot, size,
                PlacedRules.isFullContents(sup), yaw);
            if (in.isEmpty()) {
                return record;
            }
            inserted[0] = in.get().slot();
            return inv.set(in.get().slot(), one).record().withState(in.get().state());
        });
        if (stored.isEmpty() || inserted[0] < 0) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);
        }
        show(block, stored.get());
        effects.placed(block);
        return true;
    }

    /**
     * TFC {@code onRightClick} with an empty main hand or while sneaking: the item of the slot under {@code (x, z)} (slot 0
     * of a large item) goes to the player; an empty block becomes air.
     *
     * @return whether an item was taken
     */
    public boolean take(Player player, Block block, double x, double z) {
        int slot = PlacedRules.slotSelected(x, z);
        ItemStack[] taken = {null};
        boolean[] empty = {false};
        Optional<DeviceRecord<PlacedState>> stored = devices.edit(block, kind, (record, now) -> {
            DeviceInventory<PlacedState> inv = new DeviceInventory<>(kind, record, devices.stacks());
            Optional<PlacedRules.Take> t = PlacedRules.take(record.state(), occupied(inv), slot);
            if (t.isEmpty()) {
                return record;
            }
            taken[0] = inv.get(t.get().slot());
            DeviceInventory<PlacedState> after = inv.set(t.get().slot(), ItemStack.empty());
            empty[0] = PlacedRules.isEmpty(t.get().state(), occupied(after));
            return after.record().withState(t.get().state());
        });
        if (stored.isEmpty() || taken[0] == null) {
            return false;
        }
        give(player, taken[0]);
        if (empty[0]) {
            effects.hide(block);
            blocks.remove(block);
        } else {
            show(block, stored.get());
        }
        return true;
    }

    private void show(Block block, DeviceRecord<PlacedState> record) {
        DeviceInventory<PlacedState> inv = new DeviceInventory<>(kind, record, devices.stacks());
        List<ItemStack> slots = new ArrayList<>(PlacedRules.SLOTS);
        for (int i = 0; i < PlacedRules.SLOTS; i++) {
            slots.add(inv.get(i));
        }
        effects.show(block, record.state(), slots);
    }

    static boolean[] occupied(DeviceInventory<PlacedState> inv) {
        boolean[] out = new boolean[PlacedRules.SLOTS];
        for (int i = 0; i < PlacedRules.SLOTS; i++) {
            out[i] = !StackCodec.isEmpty(inv.get(i));
        }
        return out;
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    static void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(stack);
        for (ItemStack r : rest.values()) {
            player.getWorld().dropItem(player.getLocation(), r);
        }
    }
}
