/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (addItemsFromWorld box,
 *       dumpItems), src/main/java/net/dries007/tfc/common/blocks/MoltenBlock.java @ c3b4d9536 (manageMoltenBlockTower,
 *       removeMoltenBlockTower)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.paper.firepit.PaperFirepitEffects;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * {@link BlastFurnaceEffects} on Paper. Every write is a region task of the block it changes, never inline, so a device
 * callback can ask for it while it still holds the record. The callback runs on the server clock and on the device clock
 * ({@link PaperFirepitEffects.BurnOutWatch}), so a calendar jump finishes a burn within a second.
 */
public final class PaperBlastFurnaceEffects implements BlastFurnaceEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final PaperFirepitEffects.BurnOutWatch watch;

    /** @param clock the device clock (player ticks); @param callback the device router neighbour update */
    public PaperBlastFurnaceEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, LongSupplier clock,
            Consumer<Block> callback) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
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
    public void show(Block furnace, boolean lit) {
        BlockStateRef wanted = BlastFurnaceDevice.state(lit);
        region(furnace, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(furnace);
            if (now.isEmpty() || !now.get().id().equals(BlastFurnaceDevice.ID) || wanted.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> furnace.setBlockData(d, false));
        });
    }

    @Override
    public void callbackIn(Block furnace, long ticks) {
        watch.callbackIn(furnace, ticks);
    }

    @Override
    public List<Drop> dropped(Block furnace, int chimneyLevels) {
        // AABB.encapsulatingFullBlocks(pos, pos.offset(1, levels + 2, 1)): two blocks wide, levels + 3 high
        BoundingBox box = new BoundingBox(furnace.getX(), furnace.getY(), furnace.getZ(), furnace.getX() + 2,
            furnace.getY() + chimneyLevels + 3, furnace.getZ() + 2);
        List<Drop> out = new ArrayList<>();
        for (Entity e : furnace.getWorld().getNearbyEntities(box, x -> x instanceof Item i && i.isValid())) {
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

    @Override
    public void molten(Block base, int[] layers, boolean lit) {
        for (int y = 0; y < layers.length; y++) {
            Block at = base.getRelative(0, y, 0);
            int n = layers[y];
            region(at, () -> setMolten(at, n, lit));
        }
    }

    private void setMolten(Block at, int layers, boolean lit) {
        Optional<BlockStateRef> now = blocks.stateAt(at);
        if (layers <= 0) {
            if (now.isPresent() && now.get().id().equals(PaperBlastFurnaceProbe.MOLTEN)) {
                blocks.remove(at);
            }
            return;
        }
        BlockStateRef wanted = BlockStateRef.of(PaperBlastFurnaceProbe.MOLTEN).with("layers", Integer.toString(layers))
            .with("lit", Boolean.toString(lit));
        if (now.isPresent() && now.get().equals(wanted)) {
            return;
        }
        boolean free = now.isEmpty() ? at.getType().isAir() : now.get().id().equals(PaperBlastFurnaceProbe.MOLTEN);
        if (free) {
            blocks.place(at, wanted, false);
        }
    }

    @Override
    public void removeMolten(Block furnace, int maxHeight) {
        for (int y = 1; y <= maxHeight; y++) {
            Block at = furnace.getRelative(0, y, 0);
            region(at, () -> setMolten(at, 0, false));
        }
    }
}
