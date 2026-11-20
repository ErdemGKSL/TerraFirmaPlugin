/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/GearBoxBlock.java @ c3b4d9536 (useItemOn, canEnable)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Set;

/** TFC {@code GearBoxBlock}'s hammer rule: which face a click toggles and whether it may be enabled. */
public final class GearBoxRules {
    /** What a hammer click does. */
    public enum Outcome {
        /** The face was disabled. */
        DISABLED,
        /** The face was enabled. */
        ENABLED,
        /** TFC plays {@code ITEM_BREAK} and fails: the face would leave no axis free. */
        REFUSED
    }

    /**
     * The result of a hammer click.
     *
     * @param outcome what happened
     * @param face    the face toggled (or refused)
     * @param faces   the enabled faces after the click
     */
    public record Toggle(Outcome outcome, Direction face, Set<Direction> faces) {
        public Toggle {
            EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
            copy.addAll(faces);
            faces = java.util.Collections.unmodifiableSet(copy);
        }
    }

    private GearBoxRules() {
    }

    /** TFC: {@code player.isShiftKeyDown() ? hit.getDirection().getOpposite() : hit.getDirection()}. */
    public static Direction face(Direction hitFace, boolean sneaking) {
        return sneaking ? hitFace.opposite() : hitFace;
    }

    /**
     * TFC {@code canEnable}: with {@code face} set, at least one axis must have neither face enabled (the gearbox is four
     * interlocking gears, so one axis is unused).
     */
    public static boolean canEnable(Set<Direction> faces, Direction face) {
        EnumSet<Direction> s = EnumSet.noneOf(Direction.class);
        s.addAll(faces);
        s.add(face);
        return !(s.contains(Direction.NORTH) || s.contains(Direction.SOUTH))
            || !(s.contains(Direction.EAST) || s.contains(Direction.WEST))
            || !(s.contains(Direction.UP) || s.contains(Direction.DOWN));
    }

    /** TFC {@code useItemOn} with a hammer: {@code if (prev || canEnable(...)) cycle else fail}. */
    public static Toggle toggle(Set<Direction> faces, Direction hitFace, boolean sneaking) {
        Direction face = face(hitFace, sneaking);
        EnumSet<Direction> next = EnumSet.noneOf(Direction.class);
        next.addAll(faces);
        if (next.contains(face)) {
            next.remove(face);
            return new Toggle(Outcome.DISABLED, face, next);
        }
        if (!canEnable(faces, face)) {
            return new Toggle(Outcome.REFUSED, face, next);
        }
        next.add(face);
        return new Toggle(Outcome.ENABLED, face, next);
    }
}
