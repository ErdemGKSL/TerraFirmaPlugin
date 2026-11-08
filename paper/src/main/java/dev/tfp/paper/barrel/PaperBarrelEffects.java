/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (toggleSeal's block state and
 * WOOD_PLACE sound at pitch 0.85; attack: NOTE_BLOCK_BASEDRUM, RECORDS, volume 3),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (onSeal / onUnseal sounds,
 * the instant recipe sound, ejectInventory's PLAYER_SPLASH),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (playTransferSound),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (spawnItem)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link BarrelEffects}. The {@code sealed} block state is written by a region task after the callback
 * with {@code setBlockData(data, false)} (CraftEngine keeps the block entity while the block stays the same barrel). TFC's
 * own barrel sounds ({@code tfc:block.barrel.open/close}) are not in the pack: the vanilla barrel's stand in (ledger).
 */
public final class PaperBarrelEffects implements BarrelEffects {
    static final Sound OPEN = Sound.BLOCK_BARREL_OPEN;
    static final Sound CLOSE = Sound.BLOCK_BARREL_CLOSE;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;

    public PaperBarrelEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    @Override
    public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
        Location at = centre(block);
        block.getWorld().playSound(at, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.85f);
        block.getWorld().playSound(at, sealed ? CLOSE : OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> syncState(block, wanted));
    }

    private void syncState(Block block, BlockStateRef wanted) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(wanted.id()) || now.get().equals(wanted)) {
            return; // broken, replaced or already right
        }
        Optional<BlockData> data = visuals.get().blockData(wanted);
        data.ifPresent(d -> block.setBlockData(d, false));
    }

    @Override
    public void recipe(Block block, String sound) {
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
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
    public void spawn(Block block, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        block.getWorld().dropItem(centre(block), stack, item -> item.setPickupDelay(0));
    }

    @Override
    public void drum(Block block, float pitch) {
        block.getWorld().playSound(centre(block), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, 3.0f, pitch);
    }

    @Override
    public void spilled(Block block, FluidAmount fluid) {
        block.getWorld().playSound(centre(block), Sound.ENTITY_PLAYER_SPLASH, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}
