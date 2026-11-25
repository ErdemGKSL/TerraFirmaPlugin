/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (what the block entity
 *       does to the world), src/main/java/net/dries007/tfc/common/blocks/MoltenBlock.java @ c3b4d9536 (manageMoltenBlockTower)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.paper.firepit.PaperFirepitEffects;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * {@link BloomeryEffects} on Paper. Every write is a region task of the block it changes, never inline, so a device callback
 * can ask for it while it still holds the record. The callback runs on the server clock and on the device clock
 * ({@link PaperFirepitEffects.BurnOutWatch}), so a calendar jump finishes a lit bloomery within a second.
 */
public final class PaperBloomeryEffects implements BloomeryEffects {
    /** Places the bloom's record after its block is placed (the {@code BloomDevice}). */
    @FunctionalInterface
    public interface BloomSeed {
        void seed(Block inside, ItemStack item, int count);
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<BlockVisuals> visuals;
    private final BloomSeed seed;
    private final PaperFirepitEffects.BurnOutWatch watch;

    /** @param clock the device clock (player ticks); @param callback the device router neighbour update */
    public PaperBloomeryEffects(Scheduler scheduler, CustomBlocks blocks, CustomItems items, Supplier<BlockVisuals> visuals,
            LongSupplier clock, Consumer<Block> callback, BloomSeed seed) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.seed = Objects.requireNonNull(seed, "seed");
        this.watch = new PaperFirepitEffects.BurnOutWatch(scheduler, clock, callback);
    }

    /** Starts the calendar-jump sweep; the handle stops it. */
    public Scheduler.TaskHandle start() {
        return watch.start();
    }

    private void region(Block block, Runnable task) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task);
    }

    private static Location centre(Block b) {
        return b.getLocation().add(0.5, 0.5, 0.5);
    }

    @Override
    public boolean owns(Block block) {
        World w = block.getWorld();
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        return w.isChunkLoaded(cx, cz) && scheduler.isOwner(w, cx, cz);
    }

    @Override
    public void show(Block gate, String facing, boolean lit, boolean open) {
        BlockStateRef wanted = BloomeryDevice.state(facing, lit, open);
        region(gate, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(gate);
            if (now.isEmpty() || !now.get().id().equals(BloomeryDevice.ID) || wanted.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> gate.setBlockData(d, false));
        });
    }

    @Override
    public void doorSound(Block gate, boolean open) {
        gate.getWorld().playSound(centre(gate), open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE,
            SoundCategory.BLOCKS, 1f, ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f);
    }

    @Override
    public void callbackIn(Block gate, long ticks) {
        watch.callbackIn(gate, ticks);
    }

    @Override
    public List<Drop> dropped(Block inside, int chimneyLevels) {
        // AABB.encapsulatingFullBlocks(inside, inside.offset(1, levels + 1, 1)): two blocks wide, levels + 2 high
        BoundingBox box = new BoundingBox(inside.getX(), inside.getY(), inside.getZ(), inside.getX() + 2,
            inside.getY() + chimneyLevels + 2, inside.getZ() + 2);
        List<Drop> out = new ArrayList<>();
        for (Entity e : inside.getWorld().getNearbyEntities(box, x -> x instanceof Item i && i.isValid())) {
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
    public void spawn(Block at, ItemStack stack) {
        at.getWorld().dropItem(centre(at), stack);
    }

    private static boolean replaceable(Optional<String> id, boolean air) {
        // a bloom in the chimney is kept (TFC would overwrite it: ledger)
        return air || id.filter(BloomeryWorld.MOLTEN::equals).isPresent();
    }

    @Override
    public void molten(Block inside, int[] layers, boolean lit) {
        for (int y = 0; y < layers.length; y++) {
            Block at = inside.getRelative(0, y, 0);
            int n = layers[y];
            region(at, () -> setMolten(at, n, lit));
        }
    }

    private void setMolten(Block at, int layers, boolean lit) {
        Optional<BlockStateRef> now = blocks.stateAt(at);
        if (layers <= 0) {
            if (now.isPresent() && now.get().id().equals(BloomeryWorld.MOLTEN)) {
                blocks.remove(at);
            }
            return;
        }
        BlockStateRef wanted = BlockStateRef.of(BloomeryWorld.MOLTEN).with("layers", Integer.toString(layers))
            .with("lit", Boolean.toString(lit));
        if (now.isPresent() && now.get().equals(wanted)) {
            return;
        }
        if (replaceable(now.map(BlockStateRef::id), now.isEmpty() && at.getType().isAir())) {
            blocks.place(at, wanted, false);
        }
    }

    @Override
    public void removeMolten(Block gate, int maxHeight) {
        for (int[] d : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            Block base = gate.getRelative(d[0], 0, d[1]);
            for (int y = 0; y < maxHeight; y++) {
                Block at = base.getRelative(0, y, 0);
                region(at, () -> setMolten(at, 0, false));
            }
        }
    }

    @Override
    public void placeBloom(Block inside, ItemStack item, int count) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        region(inside, () -> {
            int layers = dev.tfp.core.bloomery.BloomData.made(count).layers();
            BlockStateRef bloom = BlockStateRef.of(BloomeryWorld.BLOOM).with("layers", Integer.toString(layers));
            if (blocks.place(inside, bloom, false)) {
                seed.seed(inside, copy, count);
            }
        });
    }

    @Override
    public void showBloom(Block bloom, int layers) {
        BlockStateRef wanted = BlockStateRef.of(BloomeryWorld.BLOOM).with("layers", Integer.toString(layers));
        region(bloom, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(bloom);
            if (now.isEmpty() || !now.get().id().equals(BloomeryWorld.BLOOM) || wanted.equals(now.get())) {
                return;
            }
            visuals.get().blockData(wanted).ifPresent(d -> bloom.setBlockData(d, false));
        });
    }

    @Override
    public void removeBloom(Block bloom) {
        region(bloom, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(bloom);
            if (now.isPresent() && now.get().id().equals(BloomeryWorld.BLOOM)) {
                blocks.remove(bloom);
            }
        });
    }

    @Override
    public void breakGate(Block gate) {
        region(gate, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(gate);
            if (now.isPresent() && now.get().id().equals(BloomeryDevice.ID)) {
                blocks.remove(gate);
                items.create(BloomeryDevice.ID, 1).ifPresent(i -> gate.getWorld().dropItem(centre(gate), i));
            }
        });
    }
}
