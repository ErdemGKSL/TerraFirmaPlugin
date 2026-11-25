/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound: volume 1 + nextFloat, pitch
 *       nextFloat + 0.7 + 0.3)
 */
package dev.tfp.paper.lamp;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.firepit.PaperFirepitEffects;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * The lamp's world effects on Paper. Block changes run as region tasks on the next tick, after the click's record is stored,
 * and only while the block is still a lamp. The run-dry callback works as the firepit's: the latest one per block wins, and a
 * calendar jump past it fires within a second.
 */
public final class PaperLampEffects implements LampEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final PaperFirepitEffects.BurnOutWatch watch;

    /**
     * @param clock    the device clock (the calendar's player ticks)
     * @param callback calls the device back (the device router's neighbour update)
     */
    public PaperLampEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, LongSupplier clock,
            Consumer<Block> callback) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.watch = new PaperFirepitEffects.BurnOutWatch(scheduler, Objects.requireNonNull(clock, "clock"),
            Objects.requireNonNull(callback, "callback"));
    }

    /** Starts the calendar-jump sweep of the run-dry watch; the handle stops it. */
    public Scheduler.TaskHandle start() {
        return watch.start();
    }

    private boolean isLamp(Block block) {
        return blocks.stateAt(block).map(s -> LampDevice.metal(s.id()) != null).orElse(false);
    }

    @Override
    public void show(Block block, BlockStateRef state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(state.id()) || state.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(state);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void extinguished(Block block) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(centre(block), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f + r.nextFloat(),
            r.nextFloat() + 1f);
    }

    @Override
    public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        boolean lava = TfcFluids.LAVA.equals(fluid.fluid());
        Sound sound = intoItem
            ? (lava ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL)
            : (lava ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY);
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void callbackIn(Block block, long ticks) {
        watch.callbackIn(block, ticks);
    }

    @Override
    public void breakBlock(Block block) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            if (isLamp(block)) {
                blocks.remove(block);
            }
        });
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
