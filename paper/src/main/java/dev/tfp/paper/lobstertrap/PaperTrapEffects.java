/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536 (bubbles, updateTrapState),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound: volume 1 + rand, pitch rand + 0.7 + 0.3)
 */
package dev.tfp.paper.lobstertrap;

import dev.tfp.core.fishing.LobsterTrap;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** {@link TrapEffects} on Paper: the state write and the particles run as region tasks of the block. */
public final class PaperTrapEffects implements TrapEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final TrapFinder finder;

    public PaperTrapEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, TrapFinder finder) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.finder = Objects.requireNonNull(finder, "finder");
    }

    @Override
    public void show(Block block, LobsterTrap.State state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(LobsterTrapDevice.ID)) {
                return;
            }
            BlockStateRef wanted = LobsterTrapDevice.withTrap(now.get(), state);
            if (!wanted.equals(now.get())) {
                Optional<BlockData> data = visuals.get().blockData(wanted);
                data.ifPresent(d -> block.setBlockData(d, false));
            }
        });
    }

    @Override
    public void sound(Block block, Sound sound) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        org.bukkit.Sound s = switch (sound) {
            case BAIT -> org.bukkit.Sound.ITEM_BUNDLE_INSERT;
            case CAUGHT -> org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE;
        };
        block.getWorld().playSound(at, s, SoundCategory.BLOCKS, 1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
    }

    @Override
    public void bubbles(Block block, int count) {
        // TFCParticles.BUBBLE is TFC's own particle; the vanilla underwater bubble stands in (ledger)
        block.getWorld().spawnParticle(Particle.BUBBLE, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, count,
            0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public void baited(Block block, boolean baited) {
        BlockPos at = new BlockPos(block.getX(), block.getY(), block.getZ());
        if (baited) {
            finder.add(block.getWorld().getUID(), at);
        } else {
            finder.remove(block.getWorld().getUID(), at);
        }
    }

    @Override
    public void removed(Block block) {
        finder.remove(block.getWorld().getUID(), new BlockPos(block.getX(), block.getY(), block.getZ()));
    }
}
