/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (createIdleMovementBehaviors,
 * initHuntActivity, initRetreatActivity, initRestActivity, getDistanceFromHomeSqr), ai/predator/AmphibiousPredatorAi.java
 * (initRetreatActivity), ai/predator/PredatorBehaviors.java (findNewHome), same commit; with vanilla 1.21.1 ShufflingList,
 * RunOne, RandomStroll, StrollToPoi, StrollAroundPoi, SetLookTarget, SetWalkTargetFromLookTarget, DoNothing,
 * Vec3i.distSqr and closerToCenterThan
 */
package dev.tfp.core.predator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The movement constants of the predator brain and the idle choice. Pure.
 *
 * <p>The idle movement is {@code RunOne} over five entries of weight 1: a random stroll, walking to the look target,
 * doing nothing, strolling home when within 5 blocks, and strolling about while within the wander range. Vanilla's
 * {@code ShufflingList} gives each entry {@code -pow(nextFloat(), 1 / weight)} and sorts ascending (stably), so with
 * weight 1 the try order is the draws in descending order; the first entry whose conditions hold runs.
 */
public final class PredatorIdle {

    /** The five idle movements, in TFC's list order. */
    public enum Movement {
        /** {@code RandomStroll.stroll(0.4F)}: a land position within 10 x 7 blocks. */
        STROLL,
        /** {@code SetWalkTargetFromLookTarget.create(0.4F, 3)}. */
        WALK_TO_LOOK_TARGET,
        /** {@code new DoNothing(30, 60)}. */
        WAIT,
        /** {@code StrollToPoi.create(HOME, 0.6F, 2, 5)}: home, only while within 5 blocks of it. */
        STROLL_TO_HOME,
        /** {@code StrollAroundPoi.create(HOME, 0.6F, MAX_WANDER_DISTANCE)}: a land position within 8 x 6 blocks. */
        STROLL_AROUND_HOME
    }

    public static final float STROLL_SPEED = 0.4f;
    public static final int STROLL_HORIZONTAL = 10;
    public static final int STROLL_VERTICAL = 7;
    public static final int LOOK_WALK_CLOSE_ENOUGH = 3;
    public static final int WAIT_MIN = 30;
    public static final int WAIT_MAX = 60;
    public static final float HOME_STROLL_SPEED = 0.6f;
    public static final int HOME_STROLL_CLOSE_ENOUGH = 2;
    public static final int HOME_STROLL_MAX_DISTANCE = 5;
    /** {@code MAX_WANDER_DISTANCE = 100 * 100}, passed by TFC to {@code StrollToPoi}/{@code StrollAroundPoi} as a distance in blocks. */
    public static final int WANDER_POI_DISTANCE = 100 * 100;
    public static final int AROUND_HORIZONTAL = 8;
    public static final int AROUND_VERTICAL = 6;
    /** {@code StrollToPoi}: at most one new walk target per 80 ticks; {@code StrollAroundPoi}: per 180 ticks. */
    public static final int POI_INTERVAL_TICKS = 80;
    public static final int AROUND_INTERVAL_TICKS = 180;

    /** {@code SetLookTarget.create(8.0F, UniformInt.of(30, 60))} and {@code LookAtTargetSink(45, 90)}. */
    public static final double LOOK_RANGE = 8.0;
    public static final int LOOK_INTERVAL_MIN = 30;
    public static final int LOOK_INTERVAL_MAX = 60;
    public static final int LOOK_HOLD_MIN = 45;
    public static final int LOOK_HOLD_MAX = 90;

    /** Rest: {@code StrollToPoi.create(HOME, 1.2F, 5, MAX_WANDER_DISTANCE)}. */
    public static final float REST_HOME_SPEED = 1.2f;
    public static final int REST_HOME_CLOSE_ENOUGH = 5;

    /** Retreat: {@code SetWalkTargetAwayFrom.entity(HURT_BY_ENTITY, 1.2f, 16, true)} and the stroll home at 1.2 (0.7 amphibious). */
    public static final float RETREAT_SPEED = 1.2f;
    public static final float RETREAT_HOME_SPEED = 1.2f;
    public static final float AMPHIBIOUS_RETREAT_HOME_SPEED = 0.7f;

    /** {@code findNewHome}: {@code LandRandomPos.getPos(predator, 10, 5)} and {@code new WalkTarget(found, 1.0f, 16)}. */
    public static final int NEW_HOME_HORIZONTAL = 10;
    public static final int NEW_HOME_VERTICAL = 5;
    public static final float NEW_HOME_SPEED = 1.0f;
    public static final int NEW_HOME_CLOSE_ENOUGH = 16;

    private PredatorIdle() {
    }

    /**
     * The try order of the idle movements for five {@code nextFloat()} draws made in list order.
     *
     * @param draws five values in [0, 1)
     * @return the movements, first to try first
     */
    public static List<Movement> order(float[] draws) {
        final Movement[] all = Movement.values();
        if (draws.length != all.length) {
            throw new IllegalArgumentException("need " + all.length + " draws");
        }
        final List<Integer> idx = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        // randWeight = -pow(draw, 1 / 1); List.sort is stable, ties keep list order
        idx.sort(Comparator.comparingDouble(i -> -Math.pow(draws[i], 1.0F / 1)));
        final List<Movement> out = new ArrayList<>(all.length);
        for (int i : idx) {
            out.add(all[i]);
        }
        return List.copyOf(out);
    }

    /** {@link #order(float[])} drawing the five floats from {@code random}. */
    public static List<Movement> order(RandomGenerator random) {
        final float[] draws = new float[Movement.values().length];
        for (int i = 0; i < draws.length; i++) {
            draws[i] = random.nextFloat();
        }
        return order(draws);
    }

    /**
     * {@code PredatorAi.getDistanceFromHomeSqr}: {@code blockPosition().distSqr(home)}, the squared distance between the
     * two block positions.
     */
    public static double homeDistanceSq(int bx, int by, int bz, int hx, int hy, int hz) {
        final double dx = bx - hx;
        final double dy = by - hy;
        final double dz = bz - hz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Vanilla {@code Vec3i.closerToCenterThan(position, distance)}, the range test of {@code StrollToPoi} and
     * {@code StrollAroundPoi}: the squared distance from the block centre is strictly below {@code distance}².
     */
    public static boolean homeWithin(int hx, int hy, int hz, double px, double py, double pz, double distance) {
        final double dx = hx + 0.5 - px;
        final double dy = hy + 0.5 - py;
        final double dz = hz + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }

    /** The retreat's stroll-home speed. */
    public static float retreatHomeSpeed(PredatorSpecies.Kind kind) {
        return kind == PredatorSpecies.Kind.AMPHIBIOUS ? AMPHIBIOUS_RETREAT_HOME_SPEED : RETREAT_HOME_SPEED;
    }
}
