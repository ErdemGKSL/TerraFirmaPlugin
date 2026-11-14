/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound, playPlaceSound, spawnItem),
 *       src/main/java/net/dries007/tfc/common/TFCSounds.java @ c3b4d9536 (CHARCOAL: volume 1, pitch 1)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link CharcoalEffects}: deferred writes as region tasks of the block's chunk (FIFO on the region),
 * custom blocks through the CraftEngine adapter, state changes as the CraftEngine block data.
 */
public final class PaperCharcoalEffects implements CharcoalEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;

    public PaperCharcoalEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    private void region(Block block, Runnable task) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task);
    }

    private boolean is(Block block, String id) {
        return blocks.stateAt(block).map(s -> s.id().equals(id)).orElse(false);
    }

    @Override
    public void show(Block block, BlockStateRef state) {
        region(block, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(state.id()) || now.get().equals(state)) {
                return;
            }
            visuals.get().blockData(state).ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void place(Block block, BlockStateRef state, String replaceId, Runnable then) {
        region(block, () -> {
            boolean free = replaceId == null ? block.getType().isAir() && blocks.stateAt(block).isEmpty() : is(block, replaceId);
            if (free && blocks.place(block, state, true) && then != null) {
                then.run();
            }
        });
    }

    @Override
    public void air(Block block, String expectedId) {
        region(block, () -> {
            if (is(block, expectedId)) {
                block.setType(Material.AIR, true);
            }
        });
    }

    @Override
    public void fire(Block block, String replaceId) {
        region(block, () -> {
            boolean free = replaceId == null ? block.getType().isAir() && blocks.stateAt(block).isEmpty() : is(block, replaceId);
            if (free) {
                block.setType(Material.FIRE, true);
            }
        });
    }

    @Override
    public void later(Block block, long ticks, Runnable task) {
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1, ticks), task);
    }

    @Override
    public void breakBlock(Block block) {
        region(block, () -> {
            if (blocks.stateAt(block).isPresent()) {
                blocks.remove(block);
            }
        });
    }

    @Override
    public boolean placeNow(Block block, BlockStateRef state) {
        return blocks.place(block, state, true);
    }

    @Override
    public void setNow(Block block, BlockStateRef state) {
        Optional<BlockData> data = visuals.get().blockData(state);
        data.ifPresent(d -> block.setBlockData(d, true));
    }

    @Override
    public void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        block.getWorld().dropItem(centre(block), stack);
    }

    @Override
    public void sound(Block block, Sound sound) {
        Location at = centre(block);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (sound) {
            // playPlaceSound(SoundType.WOOD): volume (1 + 1) / 2, pitch 1 * 0.8
            case LOG_PLACE -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
            // Helpers.playSound: volume 1 + nextFloat, pitch nextFloat + 0.7 + 0.3
            case LIGHT -> block.getWorld().playSound(at, org.bukkit.Sound.ENTITY_BLAZE_SHOOT, SoundCategory.BLOCKS,
                1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
            // BlockItemPlacement.postPlacement: the CHARCOAL sound type's place sound, (1 + 1) / 2, 1 * 0.8
            case CHARCOAL_PLACE -> block.getWorld().playSound(at, "tfp:block.charcoal.place", SoundCategory.BLOCKS, 1f, 0.8f);
            // the block break sound: (1 + 1) / 2, 1 * 0.8
            case CHARCOAL_BREAK -> block.getWorld().playSound(at, "tfp:block.charcoal.break", SoundCategory.BLOCKS, 1f, 0.8f);
            default -> throw new IllegalStateException(sound.name());
        }
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
