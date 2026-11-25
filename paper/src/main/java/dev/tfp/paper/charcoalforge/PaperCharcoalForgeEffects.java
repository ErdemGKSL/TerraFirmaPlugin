/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-08: {@link CharcoalForgeEffects} on Paper. State writes are region tasks; callbacks are delayed region tasks. */
public final class PaperCharcoalForgeEffects implements CharcoalForgeEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> callback;
    private final Map<Pos, Long> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** @param callback the device router's neighbour update */
    public PaperCharcoalForgeEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Consumer<Block> callback) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    @Override
    public void show(Block block, int heatLevel) {
        BlockStateRef wanted = CharcoalForgeDevice.state(heatLevel);
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(CharcoalForgeDevice.ID) || wanted.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void give(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    @Override
    public java.util.List<Drop> dropped(Block block) {
        BoundingBox box = new BoundingBox(block.getX() - 0.2, block.getY() + 0.875, block.getZ() - 0.2, block.getX() + 1.2,
            block.getY() + 1.25, block.getZ() + 1.2);
        java.util.List<Drop> out = new java.util.ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(box, x -> x instanceof Item i && i.isValid())) {
            Item item = (Item) e;
            out.add(new Drop() {
                @Override
                public ItemStack stack() {
                    return item.getItemStack();
                }

                @Override
                public int count() {
                    return item.getItemStack().getAmount();
                }

                @Override
                public void take(int n) {
                    ItemStack stack = item.getItemStack();
                    if (stack.getAmount() <= n) {
                        item.remove();
                    } else {
                        stack.setAmount(stack.getAmount() - n);
                        item.setItemStack(stack);
                    }
                }
            });
        }
        return out;
    }

    @Override
    public void callbackIn(Block block, long ticks) {
        Pos key = Pos.of(block);
        long id = sequence.incrementAndGet();
        pending.put(key, id);
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1, ticks), () -> {
            if (pending.remove(key, id)) {
                callback.accept(block);
            }
        });
    }
}
