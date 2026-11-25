/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536
 *       (calculateFlowRateAndObstruction lines 115-151: river water, Blocks.WATER, air or an empty collision shape, anything
 *       else)
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * How one block of a water wheel's window counts. The world holds vanilla water only (NP-FLUID-01): which TFC water a water
 * block stands for comes from {@link WorldWaterService} (river water with its flow, plain fresh water, salt or spring water).
 */
public final class WaterWheelCells {
    private WaterWheelCells() {
    }

    /** Reads the cell of one block; empty when it cannot be read now. Region thread of the wheel. */
    @FunctionalInterface
    public interface Reader {
        Optional<WaterWheel.Cell> at(Block block);
    }

    /**
     * The TFC classification of one block, pure.
     *
     * @param water whether the block is vanilla water
     * @param kind  which TFC water it stands for, empty when not known yet (only read for water)
     * @param flow  its river flow, empty when not known yet (only read for river water)
     * @param open  whether the block is air or has an empty collision shape (only read for other blocks)
     * @return the cell, or empty when the water is not classified yet
     */
    public static Optional<WaterWheel.Cell> classify(boolean water, Optional<WaterKind> kind, Optional<Flow> flow, boolean open) {
        if (!water) {
            return Optional.of(WaterWheel.Cell.of(open ? WaterWheel.Kind.OPEN : WaterWheel.Kind.SOLID));
        }
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return switch (kind.get()) {
            case RIVER -> {
                if (flow.isEmpty()) {
                    yield Optional.empty();
                }
                yield Optional.of(flow.get() == Flow.NONE ? WaterWheel.Cell.of(WaterWheel.Kind.STILL_WATER)
                    : WaterWheel.Cell.river(flow.get()));
            }
            // TFC Blocks.WATER: a minor obstruction
            case FRESH -> Optional.of(WaterWheel.Cell.of(WaterWheel.Kind.STILL_WATER));
            // TFC salt and spring water are other fluid blocks: no collision shape, no effect
            case SALT, SPRING -> Optional.of(WaterWheel.Cell.of(WaterWheel.Kind.OPEN));
        };
    }

    /** The reader over the live world: only blocks of loaded chunks the thread owns; any other block is unknown. */
    public static Reader live(Scheduler scheduler, Supplier<Optional<WorldWaterService>> water) {
        return block -> {
            int cx = block.getX() >> 4;
            int cz = block.getZ() >> 4;
            if (!block.getWorld().isChunkLoaded(cx, cz) || !scheduler.isOwner(block.getWorld(), cx, cz)) {
                return Optional.empty();
            }
            if (block.getType() != Material.WATER) {
                return classify(false, Optional.empty(), Optional.empty(), block.getType().isAir() || block.isPassable());
            }
            Optional<WorldWaterService> service = water.get();
            if (service.isEmpty()) {
                return classify(true, Optional.of(WaterKind.FRESH), Optional.of(Flow.NONE), true);
            }
            Optional<WaterKind> kind = service.get().knownKind(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                block.getBiome());
            Optional<Flow> flow = kind.filter(k -> k == WaterKind.RIVER).isPresent()
                ? service.get().riverFlow(block.getWorld(), block.getX(), block.getY(), block.getZ()) : Optional.of(Flow.NONE);
            return classify(true, kind, flow, true);
        };
    }
}
