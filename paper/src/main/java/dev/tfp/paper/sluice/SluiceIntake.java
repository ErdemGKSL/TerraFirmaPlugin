/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick: the once per
 * second Helpers.gatherAndConsumeItems(level, AABB(-0.2, 0.5, -0.2, 1.2, 1.25, 1.2), inventory, 0, MAX_SOIL - 1, 1))
 */
package dev.tfp.paper.sluice;

import dev.tfp.core.sluice.SluiceState;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Takes thrown soil into a sluice (PAR-PROC-13). TFC's sluice pulls item entities out of its gathering box once a second
 * while it works; here the item entity looks for a sluice itself: after a second on the ground (and again each second
 * while a sluice is under it that does not work yet, up to {@link #TRIES}) it is offered to the sluice whose box holds it.
 * Only soil items are watched, so no entity is scanned otherwise ("decide async, apply sync": the event only enqueues; the
 * check and the insert run on the item's region).
 */
final class SluiceIntake implements Listener {
    static final long DELAY = 20;
    static final int TRIES = 60;

    private final DeviceService devices;
    private final DeviceKind<SluiceState> kind;
    private final CustomBlocks blocks;
    private final SluiceDevice.Services services;
    private final Scheduler scheduler;

    SluiceIntake(DeviceService devices, DeviceKind<SluiceState> kind, CustomBlocks blocks, SluiceDevice.Services services,
            Scheduler scheduler) {
        this.devices = devices;
        this.kind = kind;
        this.blocks = blocks;
        this.services = services;
        this.scheduler = scheduler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (services.deposit().apply(item.getItemStack()).isPresent()) {
            arm(item, TRIES);
        }
    }

    private void arm(Item item, int tries) {
        scheduler.entityDelayed(item, DELAY, () -> offer(item, tries), () -> { });
    }

    private void offer(Item item, int tries) {
        if (!item.isValid()) {
            return;
        }
        Optional<Block> sluice = find(item.getLocation());
        if (sluice.isEmpty()) {
            return;
        }
        if (!take(sluice.get(), item) && tries > 1) {
            arm(item, tries - 1);
        }
    }

    /** The upper half whose gathering box holds {@code at}. */
    Optional<Block> find(Location at) {
        Block base = at.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    Block candidate = base.getRelative(dx, dy, dz);
                    if (SluiceDevice.inGatherBox(at.getX() - candidate.getX(), at.getY() - candidate.getY(),
                            at.getZ() - candidate.getZ()) && isUpperSluice(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isUpperSluice(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        // the block family is tfp:wood/sluice/<wood> (PAR-PROC-53: an exact match never found a real sluice)
        return state.isPresent() && isSluice(state.get().id()) && SluiceDevice.isUpper(state.get());
    }

    static boolean isSluice(String id) {
        return id.equals(SluiceDevice.ID) || id.startsWith(SluiceDevice.ID + "/");
    }

    /**
     * Moves as many items of {@code item} as there are free slots into a working sluice, one per slot.
     *
     * @return true when the item entity was emptied or the sluice took something
     */
    boolean take(Block upper, Item item) {
        ItemStack stack = item.getItemStack();
        int[] taken = {0};
        devices.neighbourChanged(upper); // re-reads the water first
        devices.edit(upper, kind, (record, now) -> {
            if (!record.state().working()) {
                return record;
            }
            DeviceInventory<SluiceState> inv = new DeviceInventory<>(kind, record, devices.stacks());
            ItemStack one = stack.clone();
            one.setAmount(1);
            for (int i = 0; i < stack.getAmount(); i++) {
                DeviceInventory.Insert<SluiceState> r = inv.insert(one);
                if (!StackCodec.isEmpty(r.remainder())) {
                    break;
                }
                inv = r.inventory();
                taken[0]++;
            }
            return inv.record();
        });
        if (taken[0] == 0) {
            return false;
        }
        if (taken[0] >= stack.getAmount()) {
            item.remove();
        } else {
            ItemStack left = stack.clone();
            left.setAmount(stack.getAmount() - taken[0]);
            item.setItemStack(left);
        }
        devices.neighbourChanged(upper); // arms the wake-up for the new soil
        return true;
    }
}
