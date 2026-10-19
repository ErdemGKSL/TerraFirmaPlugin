/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (of, positiveSpeed, the speed and direction
 *       fields), src/main/java/net/dries007/tfc/util/rotation/AxleNode.java @ c3b4d9536 (AxleRotation.length)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.Direction;

/**
 * A rotation as it travels through a network: the handedness {@code direction}, the signed {@code speed} in radians per tick
 * (negative speed reverses the hand, as in TFC), and the number of consecutive axles it has passed.
 *
 * <p>{@code axleLength} is 0 for a plain rotation (a source, or anything after a gearbox). TFC models the same thing as
 * {@code AxleRotation} wrapping a plain rotation; {@code Rotation.of} in TFC drops the wrapper, which is why a gearbox resets
 * the count. Angles are not modelled here: they belong to the client view.
 *
 * @param direction  the handedness direction, in the TFC convention of {@code Rotation.direction()}
 * @param speed      signed speed in radians per tick
 * @param axleLength consecutive axles passed, 0 for a plain rotation
 */
public record Rotation(Direction direction, float speed, int axleLength) {
    public Rotation {
        if (direction == null) {
            throw new IllegalArgumentException("direction");
        }
        if (axleLength < 0) {
            throw new IllegalArgumentException("axleLength < 0: " + axleLength);
        }
    }

    /** TFC {@code Rotation.of(direction, speed)}: a plain rotation with no axles counted. */
    public static Rotation of(Direction direction, float speed) {
        return new Rotation(direction, speed, 0);
    }

    /** TFC {@code Rotation.positiveSpeed()}: the magnitude of the speed. */
    public float positiveSpeed() {
        return Math.abs(speed);
    }
}
