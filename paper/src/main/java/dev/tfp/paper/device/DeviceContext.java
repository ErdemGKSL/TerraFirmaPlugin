/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * One routed device callback (PAR-INFRA-02): the device, who acted, and the record already caught up to {@link #now}.
 *
 * @param kind   the device's kind
 * @param block  the device block (after removal: the position, holding the new block)
 * @param state  the custom block state ({@code tfp:device/x[lit=true]}); after removal the state that was removed
 * @param player the acting player, {@code null} for neighbour updates, removal by the world and catch-up
 * @param hand   the hand used, {@code null} without a player click
 * @param item   a copy of the stack in {@code hand}, {@link ItemStack#empty()} when none
 * @param record the caught-up record
 * @param now    player ticks of this callback (the clock snapshot taken once for it)
 * @param stacks the slot codec
 */
public record DeviceContext<S>(DeviceKind<S> kind, Block block, BlockStateRef state, Player player, EquipmentSlot hand,
        ItemStack item, DeviceRecord<S> record, long now, StackCodec stacks) {

    public Optional<Player> actor() {
        return Optional.ofNullable(player);
    }

    /** The record's slots as stacks. */
    public DeviceInventory<S> inventory() {
        return new DeviceInventory<>(kind, record, stacks);
    }

    public S deviceState() {
        return record.state();
    }
}
