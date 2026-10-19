/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (serverTick: the
 *       cooldown decrement, the wait while on cooldown or without an item, the strike window between 90 and 270 degrees crossing
 *       180, the handedness check, the anvil light hit, the cooldown after a strike; getRealRotationDegrees)
 */
package dev.tfp.core.machine;

import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The trip hammer's strike cycle. Each tick the hammer reads its rotation, and strikes the anvil below it when the rotation sweeps
 * across the bottom of its circle (the angle crosses 180 degrees between 90 and 270) while the hammer has an item and is off
 * cooldown. A strike sets the cooldown to {@code ceil(0.8 turn / speed)} ticks. The cooldown counts down every tick, even without
 * power, so the next strike comes {@code cooldown + 1} ticks after the last one.
 *
 * <p>The anvil hit is a callback: TFC calls {@code AnvilBlockEntity.workRemotely} and damages the hammer's item when it returns
 * true. The callback is only invoked on a strike with an anvil present, so the caller decides the side effect once. The item and
 * the blocks are the caller's: {@link Tick#itemDamaged} says whether to damage the item, and {@link Action#BREAK} says the hammer
 * was on the wrong side of its shaft and breaks.
 */
public final class TripHammerRules {

    /** TFC's initial {@code cooldownTicks}: the hammer waits this many ticks before its first strike. */
    public static final int START_COOLDOWN_TICKS = 10;

    /** TFC's {@code 0.8f} of a turn in the cooldown after a strike. */
    public static final float COOLDOWN_TURNS = 0.8f;

    /** The {@code movement} argument of the light hit ({@code workRemotely(ForgeStep.HIT_LIGHT, 12, true)}). */
    public static final int ANVIL_LIGHT_HIT_MOVEMENT = 12;

    private TripHammerRules() {
    }

    /**
     * The hammer's own state between ticks.
     *
     * @param cooldownTicks   TFC's {@code cooldownTicks}, which may go below zero
     * @param lastAngleDegrees the angle of the previous tick, in degrees, or negative infinity when there is none
     */
    public record State(int cooldownTicks, float lastAngleDegrees) {
        /** A new hammer: the start cooldown and no last angle, as TFC's field initialisers. */
        public static final State NEW = new State(START_COOLDOWN_TICKS, Float.NEGATIVE_INFINITY);
    }

    /** What the hammer did on a tick. */
    public enum Action {
        /** Nothing to do: no rotation, on cooldown, or no item. */
        IDLE,
        /** The angle did not cross the strike point this tick. */
        MISS,
        /** The angle crossed the strike point, but there is no anvil below, so nothing happens. */
        NO_ANVIL,
        /** The rotation ran the wrong way for the hammer's facing, so the hammer breaks. */
        BREAK,
        /** The hammer struck the anvil. */
        STRIKE
    }

    /**
     * The outcome of one tick.
     *
     * @param state        the state after the tick
     * @param action       what happened
     * @param itemDamaged  whether the strike damaged the hammer's item (only on {@link Action#STRIKE})
     */
    public record Tick(State state, Action action, boolean itemDamaged) {}

    /**
     * One hammer tick, as TFC's {@code serverTick}.
     *
     * @param state            the hammer's state before the tick
     * @param rotation         the rotation from the solver, or {@code null} when the hammer is not powered
     * @param accumulatedRadians the caller's angle of the rotation, as TFC's tickable angle, in {@code [0, TWO_PI]}
     * @param hasItem          whether the hammer holds a valid item (TFC's {@code isItemValid} tag, checked by the caller)
     * @param facing           the hammer's horizontal facing
     * @param anvilHit         the anvil's light hit, invoked only on a strike with an anvil below; empty when there is none
     * @return the next state and what happened
     */
    public static Tick tick(State state, Rotation rotation, float accumulatedRadians, boolean hasItem, Direction facing,
                            Optional<BooleanSupplier> anvilHit) {
        final int cooldown = state.cooldownTicks();
        final int next = cooldown - 1;
        if (rotation == null) {
            return new Tick(new State(next, Float.NEGATIVE_INFINITY), Action.IDLE, false);
        }

        final float angle = RotationAngles.degreesAtNextTick(rotation.direction(), accumulatedRadians, rotation.speed());
        if (cooldown > 0 || !hasItem) {
            return new Tick(new State(next, angle), Action.IDLE, false);
        }

        // The angle wraps from 360 to 0, may jump past the strike point, and may run backwards: test the two readings as a span.
        final float last = state.lastAngleDegrees();
        final float min = Math.min(angle, last);
        final float max = Math.max(angle, last);
        if (angle > 90 && angle < 270 && min > 0 && min < 180 && max > 180) {
            if (positiveDirection(rotation) != clockwise(facing)) {
                return new Tick(new State(next, last), Action.BREAK, false);
            }
            if (anvilHit.isEmpty()) {
                return new Tick(new State(next, angle), Action.NO_ANVIL, false);
            }
            final boolean damaged = anvilHit.get().getAsBoolean();
            final int strikeCooldown = (int) Math.ceil(COOLDOWN_TURNS * RotationAngles.TWO_PI / rotation.positiveSpeed());
            return new Tick(new State(strikeCooldown, angle), Action.STRIKE, damaged);
        }
        return new Tick(new State(next, angle), Action.MISS, false);
    }

    /** The direction the rotation turns in, using a left-hand rule: the opposite of the direction when the speed is negative. */
    static Direction positiveDirection(Rotation rotation) {
        return rotation.speed() < 0 ? rotation.direction().opposite() : rotation.direction();
    }

    /** Minecraft's {@code Direction.getClockWise} for a horizontal direction: N to E, E to S, S to W, W to N. */
    static Direction clockwise(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP, DOWN -> throw new IllegalArgumentException("the hammer faces horizontally: " + facing);
        };
    }
}
