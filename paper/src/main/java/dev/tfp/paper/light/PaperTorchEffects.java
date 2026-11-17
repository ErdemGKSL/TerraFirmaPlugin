/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (useItemOn: shrink, giveItemToPlayer)
 */
package dev.tfp.paper.light;

import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link TorchEffects}. The burnout callback is one delayed region task per torch: a newer request
 * replaces the queued one (the old task fires and does nothing); a task dropped by a chunk unload is replaced by the
 * chunk-load catch-up ({@link TorchService#chunkLoaded}). A forward calendar skip re-queues them all ({@link #recheckAll}).
 */
public final class PaperTorchEffects implements TorchEffects {
    private final Scheduler scheduler;
    private final Supplier<TorchService> service;
    private final Map<Pos, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** A queued burnout callback: its request id and the torch block. */
    private record Pending(long id, Block block) {
    }

    /** @param service the torch service (set after this is built: the device and the service need each other) */
    public PaperTorchEffects(Scheduler scheduler, Supplier<TorchService> service) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public void burnout(Block block) {
        TorchService s = service.get();
        if (s != null) {
            s.burnout(block);
        }
    }

    @Override
    public void burnoutIn(Block block, long ticks) {
        Pos key = Pos.of(block);
        long id = sequence.incrementAndGet();
        Pending entry = new Pending(id, block);
        pending.put(key, entry);
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1L, ticks), () -> {
            if (!pending.remove(key, entry)) {
                return;
            }
            TorchService s = service.get();
            if (s == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                return;
            }
            long wait = s.callback(block);
            if (wait > 0) {
                burnoutIn(block, wait);
            }
        });
    }

    /**
     * PAR-HEAT-22: after a forward calendar skip, every torch with a queued callback is checked again on the next tick of
     * its region (its callback is re-queued one tick out, replacing the old one), so a torch the skip burnt out swaps at
     * once instead of at its old server-tick deadline. Any thread.
     *
     * @return the torches re-queued
     */
    public int recheckAll() {
        int n = 0;
        for (Pending p : pending.values().toArray(Pending[]::new)) {
            burnoutIn(p.block(), 1);
            n++;
        }
        return n;
    }

    /** Torches with a queued burnout callback (tests, debug). */
    int pendingCount() {
        return pending.size();
    }

    @Override
    public void takeOne(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }

    @Override
    public void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }
}
