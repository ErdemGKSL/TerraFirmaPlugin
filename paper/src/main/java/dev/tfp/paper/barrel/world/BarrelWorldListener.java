/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: gatherAndConsumeItems),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (gatherAndConsumeItems)
 */
package dev.tfp.paper.barrel.world;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.barrel.BarrelDevice;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * PAR-PROC-42: the barrel's two world rules without a per-block tick.
 *
 * <ul>
 *   <li>Redstone: a neighbour update of a barrel block asks the device router to run the barrel's
 *       {@code neighbourChanged}, which reads the signal and applies {@code BarrelRedstone} (TFC
 *       {@code BarrelBlock.neighborChanged}).</li>
 *   <li>Gathering: TFC's {@code serverTick} (every 20 ticks, unsealed, facing up) gathers item entities in the box
 *       (0.25, 0.0625, 0.25)-(0.75, 0.9375, 0.75) into the item slot. Here every spawned item entity gets an entity task
 *       every {@value #PERIOD} ticks (it follows the entity across regions) that stops once the item is gone, was
 *       consumed, or has rested {@value #REST_CHECKS} checks; when it lies in an open upright barrel's box, one device
 *       edit moves what the slot accepts and shrinks the entity.</li>
 * </ul>
 */
public final class BarrelWorldListener implements Listener {
    /** TFC's gather period. */
    public static final long PERIOD = 20;
    /** Checks made while the item rests before the task stops. */
    public static final int REST_CHECKS = 10;
    /** TFC's gather box, block-local. */
    public static final BoundingBox BOX = new BoundingBox(0.25, 0.0625, 0.25, 0.75, 0.9375, 0.75);

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final String prefix;

    /** @param prefix the barrel block id prefix ({@code tfp:wood/barrel/}) */
    public BarrelWorldListener(Scheduler scheduler, CustomBlocks blocks, DeviceService devices, String prefix) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.devices = devices;
        this.prefix = prefix;
    }

    private Optional<BlockStateRef> barrel(Block block) {
        return blocks.stateAt(block).filter(s -> s.id().startsWith(prefix));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (barrel(block).isPresent()) {
            scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
                if (barrel(block).isPresent()) {
                    devices.neighbourChanged(block);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        AtomicInteger rested = new AtomicInteger();
        double[] last = new double[3];
        Scheduler.TaskHandle[] handle = new Scheduler.TaskHandle[1];
        handle[0] = scheduler.entityRepeating(item, PERIOD, PERIOD, () -> {
            if (!item.isValid() || item.getItemStack().getType().isAir()) {
                handle[0].cancel();
                return;
            }
            boolean moved = gather(item);
            boolean still = item.getLocation().getX() == last[0] && item.getLocation().getY() == last[1]
                && item.getLocation().getZ() == last[2];
            last[0] = item.getLocation().getX();
            last[1] = item.getLocation().getY();
            last[2] = item.getLocation().getZ();
            if (!still || moved) {
                rested.set(0);
            } else if (rested.incrementAndGet() >= REST_CHECKS) {
                handle[0].cancel();
            }
        }, () -> { });
    }

    /** Entity thread of {@code item}: one gather check. Returns whether anything moved. */
    boolean gather(Item item) {
        Block block = item.getLocation().getBlock();
        Optional<BlockStateRef> state = barrel(block);
        if (state.isEmpty() || !"up".equals(state.get().properties().getOrDefault(BarrelDevice.FACING, "up"))
                || "true".equals(state.get().properties().get(BarrelDevice.SEALED))) {
            return false;
        }
        BoundingBox box = BOX.clone().shift(block.getX(), block.getY(), block.getZ());
        if (!box.overlaps(item.getBoundingBox())) {
            return false;
        }
        // the device edit needs the block's region: this runs on the item's entity thread, which owns the block
        // only while both are in one region (always on Paper); otherwise the next check retries from the region side
        if (!scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Optional<DeviceKind<BarrelData>> kind = devices.kinds().find(state.get().id()).map(k -> (DeviceKind<BarrelData>) k);
        if (kind.isEmpty()) {
            return false;
        }
        int[] taken = new int[1];
        ItemStack stack = item.getItemStack();
        devices.edit(block, kind.get(), (record, now) -> {
            BarrelData data = record.state();
            if (data.sealed() || !kind.get().filter().canInsert(BarrelDevice.SLOT_ITEM, stack)) {
                return record;
            }
            ItemStack main = devices.stacks().decode(record.slot(BarrelDevice.SLOT_ITEM));
            int n = BarrelGather.accepted(main, stack);
            if (n <= 0) {
                return record;
            }
            ItemStack merged = StackCodec.isEmpty(main) ? stack.clone() : main.clone();
            merged.setAmount((StackCodec.isEmpty(main) ? 0 : main.getAmount()) + n);
            taken[0] = n;
            DeviceRecord<BarrelData> next = record.withSlot(BarrelDevice.SLOT_ITEM, devices.stacks().encode(merged));
            return next;
        });
        if (taken[0] > 0) {
            if (taken[0] >= stack.getAmount()) {
                item.remove();
            } else {
                ItemStack rest = stack.clone();
                rest.setAmount(stack.getAmount() - taken[0]);
                item.setItemStack(rest);
            }
        }
        return taken[0] > 0;
    }
}
