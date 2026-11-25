/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java @ c3b4d9536 (canSurvive,
 *       getStateForPlacement, updateShape), src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java,
 *       AnemometerBlock.java and VaneBlock.java @ c3b4d9536 (the same), src/main/java/net/dries007/tfc/util/Helpers.java
 *       @ c3b4d9536 (getSupportedDirectionalStateForPlacement)
 */
package dev.tfp.paper.instrument;

import dev.tfp.paper.instrument.InstrumentReadout.Kind;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * PAR-CLIM-28: where the four instruments may stand and what they attach to. Pure rules over a {@link Support} (the world
 * questions); the handler takes the answers on the block's region thread and applies the one resulting write, as TFC's
 * {@code updateShape} does for a neighbour change.
 */
public final class InstrumentRules {
    /** TFC tags {@code #tfc:thermometer_readable} and {@code #tfc:clock_readable}. */
    public static final String THERMOMETER_READABLE = "tfc:thermometer_readable";
    public static final String CLOCK_READABLE = "tfc:clock_readable";

    /** The world questions the rules ask (region thread of the block asked about). */
    public interface Support {
        /** Whether {@code block} is in {@code #tfc:thermometer_readable} / {@code #tfc:clock_readable} for {@code kind}. */
        boolean readable(Block block, Kind kind);

        /** TFC {@code BlockState.isFaceSturdy(level, pos, face)}: the {@code face} of {@code block} is a full solid face. */
        boolean sturdyFace(Block block, BlockFace face);

        /** TFC {@code Block.canSupportCenter(level, pos, UP)}: the top of {@code block} holds a centred post. */
        boolean centerSupportUp(Block block);

        /** The custom block id at {@code block}, empty for a vanilla block. */
        Optional<String> id(Block block);
    }

    private static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.UP, BlockFace.DOWN);

    private InstrumentRules() {
    }

    /** TFC {@code canSurvive} of the instrument {@code kind} in {@code state} at {@code block}. */
    public static boolean canSurvive(Kind kind, Block block, BlockStateRef state, Support s) {
        switch (kind) {
            case THERMOMETER, CALENDAR_CLOCK -> {
                BlockFace facing = InstrumentDevices.face(state);
                return facing != null && mounted(kind, block, facing, s);
            }
            case ANEMOMETER -> {
                return s.centerSupportUp(block.getRelative(BlockFace.DOWN));
            }
            default -> {
                Block below = block.getRelative(BlockFace.DOWN);
                return s.id(below).filter(InstrumentDevices.ANEMOMETER::equals).isPresent() || s.centerSupportUp(below);
            }
        }
    }

    /** {@code ThermometerBlock.canSurvive(level, pos, facing)}: the block behind is readable or has a sturdy face towards us. */
    static boolean mounted(Kind kind, Block block, BlockFace facing, Support s) {
        Block behind = block.getRelative(facing.getOppositeFace());
        return s.readable(behind, kind) || s.sturdyFace(behind, facing);
    }

    /**
     * TFC {@code Helpers.getSupportedDirectionalStateForPlacement}: the first of the directions the player looks along,
     * nearest first, whose opposite is a supported facing. {@code horizontal} is the thermometer. Null when none holds
     * (TFC then refuses the placement).
     */
    public static BlockFace placementFacing(Kind kind, Block block, Vector look, boolean horizontal, Support s) {
        List<BlockFace> order = FACES.stream().sorted(Comparator.comparingDouble(
            (BlockFace f) -> -(f.getModX() * look.getX() + f.getModY() * look.getY() + f.getModZ() * look.getZ()))).toList();
        for (BlockFace looking : order) {
            if (horizontal && (looking == BlockFace.UP || looking == BlockFace.DOWN)) {
                continue;
            }
            BlockFace facing = looking.getOppositeFace();
            if (mounted(kind, block, facing, s)) {
                return facing;
            }
        }
        return null;
    }

    /** {@code ThermometerBlock.getStateForPlacement}: attached when the block behind is thermometer readable. */
    public static boolean thermometerAttached(Block block, BlockFace facing, Support s) {
        return s.readable(block.getRelative(facing.getOppositeFace()), Kind.THERMOMETER);
    }

    /**
     * {@code attached_wind_devices} of a vane (an anemometer below it) or an anemometer (a vane above it); false for the others.
     */
    public static boolean windAttached(Kind kind, Block block, Support s) {
        return switch (kind) {
            case VANE -> s.id(block.getRelative(BlockFace.DOWN)).filter(InstrumentDevices.ANEMOMETER::equals).isPresent();
            case ANEMOMETER -> s.id(block.getRelative(BlockFace.UP)).filter(InstrumentDevices.VANE::equals).isPresent();
            default -> false;
        };
    }
}
