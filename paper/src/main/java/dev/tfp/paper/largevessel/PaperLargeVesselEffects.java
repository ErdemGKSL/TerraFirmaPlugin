/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (onSeal, onUnseal),
 *       src/main/java/net/dries007/tfc/client/TFCSounds.java @ c3b4d9536 (OPEN_VESSEL, CLOSE_VESSEL)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.sound.Sounds;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * PAR-POT-06: {@link LargeVesselEffects} on Paper. The state write is a region task of the block that changes only the
 * {@code sealed} property of the CraftEngine state, so a facing written by the placement listener survives.
 */
public final class PaperLargeVesselEffects implements LargeVesselEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;

    public PaperLargeVesselEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    @Override
    public void show(Block block, boolean sealed) {
        change(block, now -> now.with(LargeVesselDevice.SEALED, Boolean.toString(sealed)));
    }

    /** Writes {@code facing} onto the vessel at {@code block} (TFC {@code getStateForPlacement}). */
    void facing(Block block, String facing) {
        change(block, now -> now.with(LargeVesselDevice.FACING, facing));
    }

    private void change(Block block, java.util.function.UnaryOperator<BlockStateRef> edit) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !LargeVesselDevice.covers(now.get().id())) {
                return;
            }
            BlockStateRef wanted = edit.apply(now.get());
            if (wanted.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    /**
     * TFC plays {@code tfc:block.large_vessel.close} when sealing and {@code .open} when opening ({@code Helpers.playSound}:
     * blocks category, full volume, pitch 1); the pack ships them as {@code tfp:block.large_vessel.*} (PAR-DATA-08).
     */
    @Override
    public void sound(Block block, boolean sealed) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        Sounds.play(block.getWorld(), at, sealed ? Sounds.LARGE_VESSEL_CLOSE : Sounds.LARGE_VESSEL_OPEN,
            SoundCategory.BLOCKS, 1f, 1f);
    }
}
