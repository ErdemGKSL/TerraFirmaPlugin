/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (the fields, serverTick,
 *       getRealRotationDegrees), src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (angle, positiveDirection)
 *
 * The golden values are hand-traced from the TFC lines named in each test's Javadoc. The angles are given in degrees, and the
 * tests feed the accumulated angle that TFC's tickable rotation would hold, so the degrees the hammer reads are the ones named.
 */
package dev.tfp.core.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.machine.TripHammerRules.Action;
import dev.tfp.core.machine.TripHammerRules.State;
import dev.tfp.core.machine.TripHammerRules.Tick;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TripHammerGoldenTest {

    private static final float SPEED = 0.1f;

    /** The accumulated angle that makes the hammer read {@code degrees} next tick for this direction and speed. */
    private static float accumulatedFor(Direction direction, float degrees, float speed) {
        final float radians = degrees / RotationAngles.RAD_TO_DEG;
        return axisStep(direction) > 0 ? radians - speed : RotationAngles.TWO_PI - radians - speed;
    }

    private static int axisStep(Direction direction) {
        return direction == Direction.UP || direction == Direction.SOUTH || direction == Direction.EAST ? 1 : -1;
    }

    private static Tick step(State state, float degrees, Rotation rotation, Direction facing, Optional<BooleanSupplier> anvil) {
        return TripHammerRules.tick(state, rotation, accumulatedFor(rotation.direction(), degrees, rotation.speed()), true, facing, anvil);
    }

    /**
     * TFC {@code TripHammerBlockEntity} field initialisers: {@code cooldownTicks = 10}, {@code lastAngle = NEGATIVE_INFINITY};
     * the strike cooldown is {@code Mth.ceil(0.8f * Mth.TWO_PI / positiveSpeed)}; the light hit is
     * {@code workRemotely(ForgeStep.HIT_LIGHT, 12, true)}.
     */
    @Test
    void constantsMatchTheTripHammerBlockEntity() {
        assertThat(TripHammerRules.START_COOLDOWN_TICKS).isEqualTo(10);
        assertThat(TripHammerRules.COOLDOWN_TURNS).isEqualTo(0.8f);
        assertThat(TripHammerRules.ANVIL_LIGHT_HIT_MOVEMENT).isEqualTo(12);
        assertThat(RotationAngles.TWO_PI).isEqualTo(6.2831855f);
        assertThat(State.NEW.cooldownTicks()).isEqualTo(10);
        assertThat(State.NEW.lastAngleDegrees()).isEqualTo(Float.NEGATIVE_INFINITY);
    }

    /**
     * TFC {@code serverTick}: {@code cooldown = cooldownTicks--} is tested before the strike, so the hammer waits ten ticks from a
     * new start, strikes on the eleventh, and then sets the cooldown to {@code ceil(0.8 * 2pi / 0.1) = ceil(50.27) = 51}. The field is
     * counted down again from 51 and the next strike comes on the 63rd tick: 52 ticks after the last, which is the cooldown plus one.
     */
    @Test
    void strikesEveryCooldownPlusOneTicksAtTenthSpeed() {
        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Optional<BooleanSupplier> anvil = Optional.of(() -> true);
        State state = State.NEW;
        final List<Integer> strikes = new ArrayList<>();
        for (int tick = 1; tick <= 63; tick++) {
            // Alternating 170 and 190 degrees: every tick crosses the bottom of the circle.
            final float degrees = tick % 2 == 1 ? 170f : 190f;
            final Tick result = step(state, degrees, rotation, Direction.NORTH, anvil);
            if (result.action() == Action.STRIKE) {
                strikes.add(tick);
                assertThat(result.state().cooldownTicks()).isEqualTo(51);
            }
            state = result.state();
        }
        assertThat(strikes).containsExactly(11, 63);
    }

    /**
     * TFC {@code serverTick} strike window: {@code angle > 90 && angle < 270 && minAngle > 0 && minAngle < 180 && maxAngle > 180}.
     * A sweep from 80 to 100 degrees does not cross the bottom, and one from 350 to 10 degrees does not reach the window.
     */
    @Test
    void sweepsThatMissTheStrikeWindowDoNothing() {
        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Optional<BooleanSupplier> anvil = Optional.of(() -> true);
        assertThat(step(new State(0, 80f), 100f, rotation, Direction.NORTH, anvil).action()).isEqualTo(Action.MISS);
        assertThat(step(new State(0, 350f), 10f, rotation, Direction.NORTH, anvil).action()).isEqualTo(Action.MISS);
        // A sweep that lands on 180 from one side but stays below it, and one that starts past it, are not crossings.
        assertThat(step(new State(0, 170f), 179f, rotation, Direction.NORTH, anvil).action()).isEqualTo(Action.MISS);
        assertThat(step(new State(0, 181f), 200f, rotation, Direction.NORTH, anvil).action()).isEqualTo(Action.MISS);
    }

    /**
     * TFC {@code serverTick}: the crossing test does not depend on the sweep's direction, so a reversing rotation from 190 down to
     * 170 degrees strikes too, as the forward sweep does.
     */
    @Test
    void aReversingSweepAcrossTheBottomStrikes() {
        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Tick result = step(new State(0, 190f), 170f, rotation, Direction.NORTH, Optional.of(() -> true));
        assertThat(result.action()).isEqualTo(Action.STRIKE);
        assertThat(result.state().cooldownTicks()).isEqualTo(51);
        assertThat(result.state().lastAngleDegrees()).isCloseTo(170f, within(1e-3f));
    }

    /**
     * TFC {@code serverTick}: {@code if (rotation.positiveDirection() != FACING.getClockWise())} destroys the block. With the hammer
     * facing north its clockwise is east, so a rotation that runs west (a negative speed on an east direction) breaks the hammer
     * before the anvil is touched, and the anvil callback must not run.
     */
    @Test
    void theWrongHandednessBreaksTheHammerBeforeTheAnvilIsHit() {
        final Rotation rotation = Rotation.of(Direction.EAST, -SPEED);
        final Optional<BooleanSupplier> anvil = Optional.of(() -> {
            throw new AssertionError("the anvil must not be hit when the hammer breaks");
        });
        final Tick result = step(new State(0, 170f), 190f, rotation, Direction.NORTH, anvil);
        assertThat(result.action()).isEqualTo(Action.BREAK);
        assertThat(result.itemDamaged()).isFalse();
    }

    /**
     * TFC {@code serverTick}: the clockwise test uses the hammer's facing, so a south-facing hammer needs a west positive rotation.
     * Its handedness is the mirror of an east one, and a west rotation with a positive speed strikes.
     */
    @Test
    void aSouthFacingHammerStrikesOnAPositiveWestRotation() {
        final Rotation rotation = Rotation.of(Direction.WEST, SPEED);
        final Tick result = step(new State(0, 170f), 190f, rotation, Direction.SOUTH, Optional.of(() -> true));
        assertThat(result.action()).isEqualTo(Action.STRIKE);
    }

    /**
     * TFC {@code serverTick}: with no anvil under the hammer (its {@code instanceof AnvilBlockEntity} test fails) the crossing does
     * nothing except record the angle, and the cooldown is not set, so the next crossing can strike at once.
     */
    @Test
    void aCrossingWithoutAnAnvilSetsNoCooldown() {
        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Tick result = step(new State(0, 170f), 190f, rotation, Direction.NORTH, Optional.empty());
        assertThat(result.action()).isEqualTo(Action.NO_ANVIL);
        assertThat(result.state().cooldownTicks()).isEqualTo(-1);
        assertThat(result.state().lastAngleDegrees()).isCloseTo(190f, within(1e-3f));
    }

    /**
     * TFC {@code AnvilBlockEntity.workRemotely} result: the hammer's item takes damage only when the anvil reports it worked, and the
     * cooldown is set either way once the anvil is there.
     */
    @Test
    void theItemIsDamagedOnlyWhenTheAnvilAccepts() {
        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Tick refused = step(new State(0, 170f), 190f, rotation, Direction.NORTH, Optional.of(() -> false));
        assertThat(refused.action()).isEqualTo(Action.STRIKE);
        assertThat(refused.itemDamaged()).isFalse();
        assertThat(refused.state().cooldownTicks()).isEqualTo(51);

        final Tick accepted = step(new State(0, 170f), 190f, rotation, Direction.NORTH, Optional.of(() -> true));
        assertThat(accepted.itemDamaged()).isTrue();
    }

    /**
     * TFC {@code serverTick}: with no rotation the cooldown is still counted down and the last angle is reset to negative infinity.
     * With no item the crossing is ignored, but the angle is kept. Without power the cooldown reaches zero after ten ticks, so the
     * first powered crossing afterwards strikes.
     */
    @Test
    void theCooldownCountsDownWithoutPowerAndNoItemIgnoresCrossings() {
        State state = State.NEW;
        for (int i = 0; i < 10; i++) {
            final Tick idle = TripHammerRules.tick(state, null, 0f, true, Direction.NORTH, Optional.of(() -> true));
            assertThat(idle.action()).isEqualTo(Action.IDLE);
            state = idle.state();
        }
        assertThat(state.cooldownTicks()).isZero();
        assertThat(state.lastAngleDegrees()).isEqualTo(Float.NEGATIVE_INFINITY);

        final Rotation rotation = Rotation.of(Direction.EAST, SPEED);
        final Tick noItem = TripHammerRules.tick(new State(0, 170f), rotation, accumulatedFor(Direction.EAST, 190f, SPEED), false,
            Direction.NORTH, Optional.of(() -> true));
        assertThat(noItem.action()).isEqualTo(Action.IDLE);
        assertThat(noItem.state().lastAngleDegrees()).isCloseTo(190f, within(1e-3f));
    }

    /**
     * TFC {@code Rotation.angle(rotation, partialTick)} and the tickable {@code tick}: the angle advances by the speed and wraps
     * into {@code [0, 2pi]}; a rotation whose direction runs against its axis reads the mirror angle, {@code 2pi - angle}.
     */
    @Test
    void theAngleWrapsAndMirrorsAsTheRotationDoes() {
        assertThat(RotationAngles.advance(6.2831855f, 0.1f)).isCloseTo(0.1f, within(1e-4f));
        assertThat(RotationAngles.advance(0.05f, -0.1f)).isCloseTo(6.2331855f, within(1e-4f));

        // east, speed 0.1 rad/tick: 0.1 rad = 5.7296 degrees
        assertThat(RotationAngles.degreesAtNextTick(Direction.EAST, 0f, 0.1f)).isCloseTo(5.7296f, within(1e-3f));
        // west, same angle: the mirror, 2pi - 0.1 rad = 354.27 degrees
        assertThat(RotationAngles.degreesAtNextTick(Direction.WEST, 0f, 0.1f)).isCloseTo(354.27f, within(1e-2f));
        // east with a negative speed reads the same mirror
        assertThat(RotationAngles.degreesAtNextTick(Direction.EAST, 0f, -0.1f)).isCloseTo(354.27f, within(1e-2f));
    }
}
