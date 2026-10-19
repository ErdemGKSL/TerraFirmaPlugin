/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (MAX_SPEED and LERP_SPEED
 *       lines 33-34, MAX_FLOW line 36, the 40 tick sample line 44, the target line 56, the obstruction and flow scan lines 97-163,
 *       the ramp and obstructed stop lines 69-82)
 */
package dev.tfp.core.power;

import dev.tfp.core.rotation.SourceSpeed;
import dev.tfp.core.worldgen.river.Flow;
import java.util.Optional;

/**
 * The water wheel's flow rate, obstruction test and speed ramp, as pure functions of the blocks around it. The caller reads the
 * 5 x 5 window in the wheel's plane (a 24-cell ring around the axle) and passes one {@link Cell} per offset; the solver is not
 * involved beyond the speed the caller stores with {@link dev.tfp.core.rotation.RotationNetworks#setSourceSpeed}.
 *
 * <p>Offsets are {@code (dH, dy)}: {@code dH} runs along the wheel's plane (z for an {@link ShaftAxis#X} wheel, x for a
 * {@link ShaftAxis#Z} wheel), {@code dy} is vertical. TFC scans {@code dH} in the outer loop and {@code dy} in the inner loop, and
 * the float sums below keep that order.
 */
public final class WaterWheel {
    private WaterWheel() {}

    /** TFC {@code MAX_SPEED = Mth.TWO_PI / (4 * 20)}: radians per tick at full flow. */
    public static final float MAX_SPEED = (float) (2 * Math.PI) / (4 * 20);

    /** TFC {@code LERP_SPEED = MAX_SPEED / (20 * 20)}: the largest change in speed per tick. */
    public static final float LERP_SPEED = MAX_SPEED / (20 * 20);

    /** TFC {@code MAX_FLOW}: the net flow that reaches {@link #MAX_SPEED}. */
    public static final float MAX_FLOW = 10f;

    /** TFC server tick: the flow is sampled once every 40 game ticks ({@code gameTime % 40 == 0}, line 44). */
    public static final int SAMPLE_INTERVAL_TICKS = 40;

    /** The half-width of the scanned window; the scan covers offsets {@code -2..2} on both axes except the centre. */
    public static final int RADIUS = 2;

    /** What one block in the wheel's window is, as TFC's {@code checkForObstruction} classifies it. */
    public enum Kind {
        /** Flowing river water: contributes flow in the bottom rows, obstructs in the top rows. */
        RIVER,
        /** Still water (TFC {@code Blocks.WATER}): always a minor obstruction. */
        STILL_WATER,
        /** Air, or any block with an empty collision shape: no effect. */
        OPEN,
        /** Any other block with a collision shape: the wheel is obstructed and breaks. */
        SOLID
    }

    /**
     * One cell of the window. {@code flow} is only read for {@link Kind#RIVER}, and must be non-null there.
     *
     * @param kind the classification of the block
     * @param flow the river flow, or {@code null} for every other kind
     */
    public record Cell(Kind kind, Flow flow) {
        public Cell {
            if (kind == null) {
                throw new IllegalArgumentException("kind");
            }
            if (kind == Kind.RIVER && flow == null) {
                throw new IllegalArgumentException("A river cell needs a flow");
            }
            if (kind != Kind.RIVER && flow != null) {
                throw new IllegalArgumentException("Only a river cell carries a flow: " + kind);
            }
        }

        public static Cell river(Flow flow) {
            return new Cell(Kind.RIVER, flow);
        }

        public static Cell of(Kind kind) {
            return new Cell(kind, null);
        }
    }

    /** The window: the cell at offset {@code (dH, dy)}, for {@code dH, dy} in {@code -2..2}, never called with {@code (0, 0)}. */
    @FunctionalInterface
    public interface CellAt {
        Cell at(int dH, int dy);
    }

    /**
     * The net flow of the wheel, negative for a wheel that turns the TFC way, or empty if it is obstructed. TFC
     * {@code calculateFlowRateAndObstruction}: the contributing flow is the river flow in the bottom rows, the obstruction is the
     * river flow in the top rows plus still water (0.25 below the centre, 1 at or above it). The net is contributing minus
     * obstruction, and the sign is flipped on the way out.
     *
     * <p>A river flow component is {@code -vectorZ} for an X wheel and {@code vectorX} for a Z wheel, as in TFC (line 127). Any
     * solid cell in the window obstructs the wheel and returns empty at once (TFC returns {@code null}).
     *
     * @return the net flow in [-10, 10], or empty when the wheel is obstructed
     */
    public static Optional<Float> netFlow(ShaftAxis axis, CellAt cells) {
        float contributing = 0f;
        float obstruction = 0f;
        for (int dH = -RADIUS; dH <= RADIUS; dH++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                if (dH == 0 && dy == 0) {
                    continue;
                }
                final Cell cell = cells.at(dH, dy);
                switch (cell.kind()) {
                    case RIVER -> {
                        final float flowRate = axis == ShaftAxis.X
                            ? (float) -cell.flow().vectorZ()
                            : (float) cell.flow().vectorX();
                        if (dy < 0) {
                            contributing += flowRate;
                        } else {
                            obstruction += Math.abs(flowRate);
                        }
                    }
                    case STILL_WATER -> obstruction += dy < 0 ? 0.25f : 1f;
                    case OPEN -> {
                        // No obstruction and no flow contribution
                    }
                    case SOLID -> {
                        return Optional.empty();
                    }
                }
            }
        }
        if (contributing > 0) {
            return Optional.of(-Math.max(0, contributing - obstruction));
        }
        return Optional.of(-Math.min(0, contributing + obstruction));
    }

    /** TFC {@code targetSpeed = maybeFlowRate * MAX_SPEED / MAX_FLOW}, evaluated left to right. */
    public static float targetSpeed(float netFlow) {
        return netFlow * MAX_SPEED / MAX_FLOW;
    }

    /**
     * One tick of the wheel's speed: an obstructed wheel is stopped at once, otherwise the speed ramps toward the target by at
     * most {@link #LERP_SPEED} (TFC {@code clientTick}, lines 69-82, through {@link SourceSpeed#ramp}).
     */
    public static float step(float current, float target, boolean obstructed) {
        return obstructed ? 0f : SourceSpeed.ramp(current, target, LERP_SPEED);
    }
}
