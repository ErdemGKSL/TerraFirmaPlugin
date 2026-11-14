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
 * chunk-load catch-up ({@link TorchService#chunkLoaded}).
 */
public final class PaperTorchEffects implements TorchEffects {
    private final Scheduler scheduler;
    private final Supplier<TorchService> service;
    private final Map<Pos, Long> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
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
        pending.put(key, id);
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1L, ticks), () -> {
            if (!pending.remove(key, id)) {
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
