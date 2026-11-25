/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (useItemOn sounds),
 *       src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536
 *       (convertPitKilnToPlacedItem: FIRE_EXTINGUISH),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536
 *       (convertPlacedItemToPitKiln: GRASS_PLACE 0.5, 1.0)
 */
package dev.tfp.paper.kiln;

import dev.tfp.paper.integration.BlockVisuals;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The server side of {@link KilnEffects}: the stage as CraftEngine block data, vanilla sounds, item drops. */
public final class PaperKilnEffects implements KilnEffects {
    private final Supplier<BlockVisuals> visuals;

    public PaperKilnEffects(Supplier<BlockVisuals> visuals) {
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    @Override
    public void stage(Block block, int stage, boolean physics) {
        visuals.get().blockData(PitKilnDevice.state(stage)).ifPresent(d -> block.setBlockData(d, physics));
    }

    @Override
    public void sound(Block block, Sound sound) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (sound) {
            case CONVERT -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.5f, 1f);
            // Helpers.playPlaceSound: volume (soundType volume + 1) / 2, pitch soundType pitch x 0.8
            case STRAW -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
            case LOG -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
            // Helpers.playSound: volume 1 + nextFloat, pitch nextFloat + 0.7 + 0.3
            case EXTINGUISH -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS,
                1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
            default -> throw new IllegalStateException(sound.name());
        }
    }

    @Override
    public void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        block.getWorld().dropItem(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5),
            stack, item -> item.setPickupDelay(0));
    }
}
