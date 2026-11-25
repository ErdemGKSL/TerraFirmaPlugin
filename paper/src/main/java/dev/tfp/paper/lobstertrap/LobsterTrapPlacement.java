/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java @ c3b4d9536 (getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (fillWithFluid)
 */
package dev.tfp.paper.lobstertrap;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pump.BlockWriter;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-FAUNA-37: TFC {@code LobsterTrapBlock.getStateForPlacement}. CraftEngine places the default state; right after the
 * placement event the trap takes {@code facing} = the opposite of the placer's horizontal direction and {@code fluid} = water
 * when the block it replaced was a water source ({@code FluidHelpers.fillWithFluid} of the clicked position's fluid: flowing
 * water is not in the property's list of fluids and leaves the trap dry). Written as one state change on the block's region.
 *
 * <p>Threads: the event runs on the placer's region; the write is a region task of the block.
 */
public final class LobsterTrapPlacement implements Listener {
    public static final String FACING = "facing";
    public static final String FLUID = "fluid";
    public static final String WATER = "water";
    public static final String EMPTY = "empty";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BlockWriter writer;

    public LobsterTrapPlacement(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        BlockData replaced = event.getBlockReplacedState().getBlockData();
        boolean wet = isWaterSource(replaced);
        String facing = facing(event.getPlayer().getLocation().getYaw());
        scheduler.region(block.getLocation(), () -> placed(block, wet, facing));
    }

    /** A water source block (level 0); flowing and falling water have higher levels. */
    static boolean isWaterSource(BlockData data) {
        return data.getMaterial() == Material.WATER && data instanceof Levelled l && l.getLevel() == 0;
    }

    /**
     * Vanilla {@code Direction.fromYRot(yaw).getOpposite()}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east
     * for the direction the player faces, so the trap's facing is north, east, south, west.
     */
    public static String facing(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
    }

    /** Writes the placement state over the trap at {@code block}, if it still is one. Region thread. */
    public void placed(Block block, boolean wet, String facing) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().equals(LobsterTrapDevice.ID)) {
            return;
        }
        BlockStateRef next = state.get().with(FACING, facing).with(FLUID, wet ? WATER : EMPTY);
        if (!next.equals(state.get())) {
            writer.set(block, next);
        }
    }
}
