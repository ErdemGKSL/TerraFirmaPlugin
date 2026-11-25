/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 * getSlotStackLimit, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement)
 */
package dev.tfp.paper.loom;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** The loom's world effects on Paper (PAR-PROC-06). */
public final class PaperLoomEffects implements LoomEffects {
    private record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> waker;
    private final Set<Key> pending = ConcurrentHashMap.newKeySet();

    public PaperLoomEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, Consumer<Block> waker) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.visuals = visuals;
        this.waker = waker;
    }

    @Override
    public void weave(Block block) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float pitch = 1 + (r.nextFloat() - r.nextFloat()) / 16;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 1f, pitch);
    }

    @Override
    public void wake(Block block, long ticks) {
        Key key = Key.of(block);
        if (!pending.add(key)) {
            return;
        }
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1, ticks), () -> {
            pending.remove(key);
            waker.accept(block);
        });
    }

    @Override
    public void state(Block block, BlockStateRef wanted) {
        scheduler.region(block.getLocation(), () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(wanted.id()) || now.get().equals(wanted)) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }
}
