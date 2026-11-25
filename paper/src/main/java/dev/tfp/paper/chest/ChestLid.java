/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536,
 *       vanilla net.minecraft.world.level.block.entity.ChestBlockEntity (playSound, the open and close sounds)
 */
package dev.tfp.paper.chest;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-DECO-26: the lid of a chest. While a player has the chest open its blocks carry {@code open=true}, whose display model
 * has the lid turned up (vanilla's openness 1), and the open and close sounds play at the chest's centre. A chest has one
 * viewer at a time ({@link ChestScreens}), so the openness is two steps (closed, open), the blocks of a double chest together.
 * The writes run on the region of each block ({@link Scheduler#region}).
 */
public final class ChestLid {
    /** Vanilla {@code ChestBlockEntity.playSound}: volume 0.5, pitch {@code random * 0.1 + 0.9}. */
    static final float VOLUME = 0.5f;

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final BiConsumer<Block, BlockStateRef> writer;

    /** @param writer replaces the state of a custom block of the same id without physics (the placement's writer) */
    public ChestLid(CustomBlocks blocks, Scheduler scheduler, BiConsumer<Block, BlockStateRef> writer) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** A viewer opened or closed the chest made of {@code keys}: shows the lid and plays the sound. Any thread. */
    public void set(List<ChestLink.Key> keys, boolean open) {
        for (ChestLink.Key key : keys) {
            World world = org.bukkit.Bukkit.getWorld(key.world());
            if (world == null) {
                continue;
            }
            Location at = new Location(world, key.x(), key.y(), key.z());
            scheduler.region(at, () -> write(world.getBlockAt(key.x(), key.y(), key.z()), open));
        }
        World world = org.bukkit.Bukkit.getWorld(keys.getFirst().world());
        if (world != null) {
            Location centre = centre(keys);
            scheduler.region(centre, () -> sound(world, centre, open));
        }
    }

    /** The centre of the chest: the block's centre, or the middle of the two halves. */
    static Location centre(List<ChestLink.Key> keys) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (ChestLink.Key k : keys) {
            x += k.x() + 0.5;
            y += k.y() + 0.5;
            z += k.z() + 0.5;
        }
        World world = org.bukkit.Bukkit.getWorld(keys.getFirst().world());
        return new Location(world, x / keys.size(), y / keys.size(), z / keys.size());
    }

    void write(Block block, boolean open) {
        Optional<ChestBlocks.Found> found = ChestBlocks.at(blocks, block);
        if (found.isEmpty() || found.get().open() == open) {
            return;
        }
        writer.accept(block, found.get().withOpen(open).state());
    }

    static void sound(World world, Location centre, boolean open) {
        float pitch = ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f;
        world.playSound(centre, open ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, VOLUME, pitch);
    }
}
