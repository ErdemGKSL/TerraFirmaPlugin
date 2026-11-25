/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (serverTick, run once
 *       per game tick, with the source's tickable angle advancing every tick before it; the hammer item's damage and break)
 */
package dev.tfp.core.triphammer;

import dev.tfp.core.machine.RotationAngles;
import dev.tfp.core.machine.TripHammerRules;
import dev.tfp.core.machine.TripHammerRules.Action;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A batch of trip hammer ticks: {@link TripHammerRules#tick} stepped once per game tick over a span, as TFC's {@code serverTick},
 * with the hammer's own copy of the rotation's angle advancing by the speed each tick ({@link RotationAngles#advance}, TFC's
 * tickable source angle). The world side answers through {@link World}: whether the hammer holds an item, the anvil in front and
 * its light hit, the item's damage. A batch of {@code n} ticks does exactly what {@code n} single ticks do; it ends early when the
 * hammer breaks (the rotation turns the wrong way for its facing).
 */
public final class TripHammerRun {
    private TripHammerRun() {
    }

    /** The hammer's state between batches: TFC's fields and the hammer's angle of the rotation. */
    public record Drive(TripHammerRules.State state, float angle) {
        public static final Drive FRESH = new Drive(TripHammerRules.State.NEW, 0f);

        public Drive {
            Objects.requireNonNull(state, "state");
        }
    }

    /** The world side of a batch; called on the thread that runs it. */
    public interface World {
        /** Whether the hammer holds an item of the trip hammers tag. */
        boolean hasItem();

        /** The anvil's light hit ({@code workRemotely(HIT_LIGHT, 12, true)}), empty when no metal anvil is in front. */
        Optional<BooleanSupplier> anvil();

        /** TFC {@code Helpers.damageItem}: one point of damage to the hammer's item after a hit the anvil took. */
        void damageItem();
    }

    /**
     * What a batch did.
     *
     * @param drive   the state after it
     * @param strikes the strikes (anvil present)
     * @param broke   whether the hammer must break (TFC {@code level.destroyBlock(pos, true)})
     */
    public record Result(Drive drive, int strikes, boolean broke) {
    }

    /**
     * Steps {@code ticks} game ticks.
     *
     * @param rotation the rotation the bladed axle above receives, null when there is none (the angle restarts at 0 then)
     */
    public static Result run(Drive drive, long ticks, Rotation rotation, Direction facing, World world) {
        TripHammerRules.State state = drive.state();
        float angle = rotation == null ? 0f : drive.angle();
        int strikes = 0;
        for (long t = 0; t < ticks; t++) {
            if (rotation != null) {
                angle = RotationAngles.advance(angle, rotation.speed());
            }
            TripHammerRules.Tick tick = TripHammerRules.tick(state, rotation, angle, world.hasItem(), facing, world.anvil());
            state = tick.state();
            if (tick.action() == Action.BREAK) {
                return new Result(new Drive(state, angle), strikes, true);
            }
            if (tick.action() == Action.STRIKE) {
                strikes++;
                if (tick.itemDamaged()) {
                    world.damageItem();
                }
            }
        }
        return new Result(new Drive(state, angle), strikes, false);
    }
}
