/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536 (WHEEL_RADIUS,
 *       ARM_LENGTH, PISTON_LENGTH, calculateShaftMovement, calculateRealRotationAngle, getCrankShaftAt, isPart, the SinkNode
 *       connection, getExtensionLength),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java @ c3b4d9536 (getStateForPlacement,
 *       getPartnerPos, useItemOn, updateShape),
 *       src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (tickBoth, getExtensionLength
 *       with a crankshaft)
 */
package dev.tfp.core.crankshaft;

import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.core.machine.RotationAngles;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.core.worldgen.Mth;
import java.util.Optional;
import java.util.function.Function;

/**
 * The crankshaft as pure rules: its two-block shape (a base and a shaft in front of it, placed with a steel rod), the sink face it
 * takes rotation from, the shaft stroke from the rotation angle, and the powered bellows' push cadence (TFC's
 * {@code BellowsBlockEntity.tickBoth}). The float arithmetic and Minecraft's sine table follow TFC.
 */
public final class CrankshaftRules {
    /** TFC {@code WHEEL_RADIUS}. */
    public static final float WHEEL_RADIUS = 4f / 16f;
    /** TFC {@code ARM_LENGTH}. */
    public static final float ARM_LENGTH = 12 / 16f;
    /** TFC {@code PISTON_LENGTH = 1.5 + WHEEL_RADIUS - ARM_LENGTH}. */
    public static final float PISTON_LENGTH = 1.5f + WHEEL_RADIUS - ARM_LENGTH;
    /** TFC {@code Mth.HALF_PI}. */
    static final float HALF_PI = (float) (Math.PI / 2);
    /** The steel rod tag a base takes to grow its shaft. */
    public static final String STEEL_RODS = "c:rods/steel";

    private CrankshaftRules() {
    }

    /** TFC {@code CrankshaftBlock.Part}. */
    public enum Part {
        BASE,
        SHAFT;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The part of a state value; empty for anything else. */
        public static Optional<Part> parse(String value) {
            if ("base".equals(value)) {
                return Optional.of(BASE);
            }
            return "shaft".equals(value) ? Optional.of(SHAFT) : Optional.empty();
        }
    }

    /** One crankshaft block as the caller reads it: its part and its {@code facing}. */
    public record Piece(Part part, Direction facing) {
    }

    /** TFC {@code ShaftMovement}. */
    public record ShaftMovement(float lengthEH, float raiseAngle) {
    }

    /** Vanilla {@code Direction.getClockWise} (seen from above) of a horizontal direction. */
    public static Direction clockWise(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> throw new IllegalArgumentException("not horizontal: " + d);
        };
    }

    /** Vanilla {@code Direction.getCounterClockWise}. */
    public static Direction counterClockWise(Direction d) {
        return clockWise(d).opposite();
    }

    /** TFC {@code getStateForPlacement}: the base faces the clockwise side of the placer's horizontal look. */
    public static Direction placementFacing(Direction horizontalLook) {
        return clockWise(horizontalLook);
    }

    /**
     * TFC's {@code SinkNode(pos, FACING.getCounterClockWise())}: the one face the base takes its rotation from. Only the base is a
     * network node (TFC {@code onLoadAdditional} adds only the base).
     */
    public static Direction sinkFace(Direction facing) {
        return counterClockWise(facing);
    }

    /** TFC {@code getPartnerPos}: a base's shaft is in front of it, a shaft's base behind it. */
    public static BlockPos partner(BlockPos pos, Piece piece) {
        return pos.offset(piece.part() == Part.BASE ? piece.facing() : piece.facing().opposite());
    }

    /**
     * TFC {@code useItemOn}: a steel rod used on a base places the shaft in front of it when that block can be replaced and holds
     * no fluid.
     *
     * @param piece           the clicked block
     * @param steelRod        whether the held item is in {@code c:rods/steel}
     * @param partnerReplaceable whether the block in front can be replaced and has no fluid
     * @return the shaft to place, empty when the click passes
     */
    public static Optional<Piece> rodUse(Piece piece, boolean steelRod, boolean partnerReplaceable) {
        if (piece.part() != Part.BASE || !steelRod || !partnerReplaceable) {
            return Optional.empty();
        }
        return Optional.of(new Piece(Part.SHAFT, piece.facing()));
    }

    /**
     * TFC {@code updateShape}: a shaft survives only while its base (any crankshaft piece of the same facing) is behind it.
     *
     * @param shaft  the shaft
     * @param behind the crankshaft piece at its partner position, empty for any other block
     */
    public static boolean shaftSurvives(Piece shaft, Optional<Piece> behind) {
        return shaft.part() != Part.SHAFT || behind.filter(b -> b.facing() == shaft.facing()).isPresent();
    }

    /**
     * TFC {@code getCrankShaftAt(level, pos, direction)}: the base of the crankshaft whose shaft is next to {@code pos} on
     * {@code direction} and whose base is two blocks away, both facing back toward {@code pos}.
     *
     * @param read the crankshaft piece at a position, empty for any other block
     * @return the base position, empty when there is no such crankshaft
     */
    public static Optional<BlockPos> crankAt(BlockPos pos, Direction direction, Function<BlockPos, Optional<Piece>> read) {
        Piece want = new Piece(Part.SHAFT, direction.opposite());
        if (!read.apply(pos.offset(direction)).filter(want::equals).isPresent()) {
            return Optional.empty();
        }
        BlockPos base = pos.offset(direction).offset(direction);
        return read.apply(base).filter(new Piece(Part.BASE, direction.opposite())::equals).map(p -> base);
    }

    /** TFC {@code calculateShaftMovement}. */
    public static ShaftMovement shaftMovement(float rotationAngle) {
        final int quadrant = Math.max(0, Math.min(3, (int) (rotationAngle / HALF_PI))); // Mth.clamp(int, 0, 3)
        final float unitCircleAngle = quadrant == 0 || quadrant == 3
            ? rotationAngle
            : (quadrant == 1 ? Mth.PI - rotationAngle : rotationAngle - Mth.PI);
        final float lengthCB = WHEEL_RADIUS * Mth.sin(unitCircleAngle);
        final float lengthOC = WHEEL_RADIUS * Mth.cos(unitCircleAngle);
        final float lengthCH = (float) Math.sqrt(ARM_LENGTH * ARM_LENGTH - lengthCB * lengthCB);
        final float angleHCB = (float) Math.acos(lengthCH / ARM_LENGTH);
        final float raiseAngle = quadrant == 2 || quadrant == 3 ? angleHCB : -angleHCB;
        final float lengthEH = 1.5f - lengthCH + (quadrant == 1 || quadrant == 2 ? lengthOC : -lengthOC);
        return new ShaftMovement(lengthEH, raiseAngle);
    }

    /**
     * TFC {@code calculateRealRotationAngle}: the node's angle ({@code getRotationAngle}), mirrored for a crank facing north or
     * east, then turned half a turn so the shaft rests furthest withdrawn.
     */
    public static float realRotationAngle(float nodeAngle, Direction facing) {
        float angle = nodeAngle;
        if (facing == Direction.NORTH || facing == Direction.EAST) {
            angle = RotationAngles.TWO_PI - angle;
        }
        angle += Mth.PI;
        if (angle > RotationAngles.TWO_PI) {
            angle -= RotationAngles.TWO_PI;
        }
        return angle;
    }

    /** TFC {@code CrankshaftBlockEntity.getExtensionLength} of a crank in a network: {@code [0, 2 * WHEEL_RADIUS]}. */
    public static float extension(float nodeAngle, Direction facing) {
        return Math.max(0, PISTON_LENGTH - shaftMovement(realRotationAngle(nodeAngle, facing)).lengthEH());
    }

    /** TFC {@code BellowsBlockEntity.getExtensionLength} with a crankshaft behind it. */
    public static float bellowsExtension(float crankExtension) {
        return Mth.clamp(BellowsMotion.MIN_EXTENSION + crankExtension, BellowsMotion.MIN_EXTENSION, BellowsMotion.MAX_EXTENSION);
    }

    /**
     * The powered bellows' state: TFC's {@code lastPushed} and {@code justPushed} fields.
     */
    public record Bellows(long lastPushed, boolean justPushed) {
        public static final Bellows FRESH = new Bellows(0L, false);
    }

    /** What one tick of a powered bellows did. */
    public enum Push {
        /** Nothing. */
        NONE,
        /** TFC {@code doPush}: the air goes out (when the receivers allow it). */
        PUSH,
        /** TFC {@code afterPush} on the tick after a push: only the effects. */
        AFTER
    }

    /** One tick's result. */
    public record Tick(Bellows bellows, Push push) {
    }

    /**
     * TFC {@code tickBoth} for a bellows with a turning crank: it pushes when the stroke is within 0.05 of its maximum, it did not
     * push last tick, and more than 20 ticks passed since {@code lastPushed}; {@code lastPushed} is then set ahead so that a slow
     * crank does not push twice in one stroke. TFC's {@code doPush} sets {@code lastPushed} and {@code justPushed} itself only when
     * the receivers allow it; {@code tickBoth} sets them after it in any case, so its values win.
     *
     * @param now        the game time
     * @param before     the bellows' fields
     * @param extension  {@link #bellowsExtension} this tick
     * @param speed      the crank rotation's signed speed, radians per tick
     */
    public static Tick tick(long now, Bellows before, float extension, float speed) {
        if (extension > BellowsMotion.MAX_EXTENSION - 0.05f && !before.justPushed() && before.lastPushed() + 20 < now) {
            long last = now - 20 + Math.max(20, (int) (0.8f * RotationAngles.TWO_PI / speed));
            return new Tick(new Bellows(last, true), Push.PUSH);
        }
        if (before.justPushed()) {
            return new Tick(new Bellows(before.lastPushed(), false), Push.AFTER);
        }
        return new Tick(before, Push.NONE);
    }

    /**
     * The crank's state between runs: the node angle it keeps (TFC keeps it on the source's tickable rotation) and the bellows'
     * fields.
     */
    public record Drive(float angle, Bellows bellows) {
        public static final Drive FRESH = new Drive(0f, Bellows.FRESH);
    }

    /** The pushes of {@link #run}: the ticks they happened on. */
    public record Run(Drive drive, java.util.List<Long> pushes) {
        public Run {
            pushes = java.util.List.copyOf(pushes);
        }
    }

    /**
     * Steps a powered bellows from {@code from} (exclusive) to {@code to} (inclusive), one game tick at a time as TFC does: the
     * source's angle advances by the speed ({@link RotationAngles#advance}), then the bellows reads the crank's stroke at
     * {@code partialTick = 1} ({@link RotationAngles#radiansAtNextTick}) and ticks. Pure; the caller runs it in batches.
     *
     * @param direction the crank rotation's direction ({@code Rotation.direction()})
     * @param speed     its signed speed
     * @param facing    the crank's facing
     */
    public static Run run(Drive drive, long from, long to, Direction direction, float speed, Direction facing) {
        float angle = drive.angle();
        Bellows bellows = drive.bellows();
        java.util.List<Long> pushes = new java.util.ArrayList<>();
        for (long t = from + 1; t <= to; t++) {
            angle = RotationAngles.advance(angle, speed);
            float node = RotationAngles.radiansAtNextTick(direction, angle, speed);
            Tick tick = tick(t, bellows, bellowsExtension(extension(node, facing)), speed);
            bellows = tick.bellows();
            if (tick.push() == Push.PUSH) {
                pushes.add(t);
            }
        }
        return new Run(new Drive(angle, bellows), pushes);
    }
}
