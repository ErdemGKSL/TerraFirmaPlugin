/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianAi.java @ c3b4d9536 (initIdleActivity,
 * initFightActivity, canSetWalkTargetFromLookTarget), ai/amphibian/PinnipedAI.java (initIdleActivity,
 * createIdleMovementBehaviors, initRetreatActivity, MAX_WANDER_DISTANCE), ai/amphibian/AmphibiousStrollToPoi.java (create),
 * ai/SetLookTarget.java (create), ai/livestock/DelegatingTemptingSensor.java (TEMPTATION_RANGE), same commit; with vanilla
 * 1.21.1 TryFindWater, RandomStroll, DoNothing, GateBehavior, RunOne/ShufflingList, SetEntityLookTargetSometimes.Ticker,
 * BabyFollowAdult, FollowTemptation, MeleeAttack, BlockPos.withinManhattan
 */
package dev.tfp.core.amphibian;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The idle behaviours of the amphibian brains as pure decisions, in TFC's priority order and with its random draws in
 * order: {@code SetLookTarget} (priority 0), {@code RunOne(BabyFollowAdult, FollowTemptation)} (2), {@code TryFindWater}
 * (3) and the idle-movement {@code GateBehavior} (4: start attacking a small fish, random swim, random stroll, walk to the
 * look target, do nothing, stroll home). The Paper glue finds the positions and entities and applies the result.
 */
public final class AmphibianIdle {
    private AmphibianIdle() {
    }

    /** {@code SetLookTarget.create(TURTLE_FRIENDS, 6.0F, ...)}: the look range. */
    public static final double LOOK_RANGE = 6.0;
    /** {@code UniformInt.of(30, 60)}: the look interval. */
    public static final int LOOK_INTERVAL_MIN = 30;
    public static final int LOOK_INTERVAL_MAX = 60;
    /** {@code ADULT_FOLLOW_RANGE = UniformInt.of(15, 30)}. */
    public static final int ADULT_FOLLOW_MIN = 15;
    public static final int ADULT_FOLLOW_MAX = 30;
    /** {@code DelegatingTemptingSensor.TEMPTATION_RANGE}. */
    public static final double TEMPTATION_RANGE = 10.0;
    /** Vanilla {@code FollowTemptation}: stop within 2.5 blocks; 100 ticks of cooldown after it stops. */
    public static final double TEMPT_CLOSE_ENOUGH = 2.5;
    public static final int TEMPTATION_COOLDOWN = 100;
    /** {@code TryFindWater.create(6, 0.15F)}. */
    public static final int FIND_WATER_RANGE = 6;
    public static final float FIND_WATER_SPEED = 0.15f;
    /** Vanilla {@code TryFindWater}: the wait pushed forward while waiting, and the wait after a search. */
    public static final int FIND_WATER_WAIT_PUSH = 22;
    public static final int FIND_WATER_WAIT = 40;
    /** {@code RandomStroll.swim(0.5F)} and {@code RandomStroll.stroll(0.15F, false)}; vanilla's 10 x 7 search. */
    public static final float SWIM_SPEED = 0.5f;
    public static final float STROLL_SPEED = 0.15f;
    public static final int STROLL_HORIZONTAL = 10;
    public static final int STROLL_VERTICAL = 7;
    /** {@code SetWalkTargetFromLookTarget.create(..., 3)}. */
    public static final int LOOK_WALK_CLOSE_ENOUGH = 3;
    /** {@code new DoNothing(30, 60)}. */
    public static final int DO_NOTHING_MIN = 30;
    public static final int DO_NOTHING_MAX = 60;
    /** {@code AmphibiousStrollToPoi.create(HOME, ..., 5, 100)} and its 80-tick re-target wait. */
    public static final int HOME_CLOSE_ENOUGH = 5;
    public static final int HOME_MAX_DISTANCE = 100;
    public static final int HOME_INTERVAL = 80;
    /** {@code PinnipedAI.MAX_WANDER_DISTANCE} (a distance, not squared, in {@code closerToCenterThan}). */
    public static final int PINNIPED_RETREAT_HOME_MAX = 100 * 100;
    /** {@code MeleeAttack.create(20)}. */
    public static final int MELEE_COOLDOWN = 20;

    /** The random source, drawn in the order the brain draws. */
    public interface Draws {
        int nextInt(int bound);

        float nextFloat();
    }

    /** Vanilla {@code Mth.randomBetweenInclusive}, as {@code UniformInt.sample} draws. */
    public static int between(Draws d, int min, int max) {
        return d.nextInt(max - min + 1) + min;
    }

    // ---- SetLookTarget --------------------------------------------------------------------------------------------

    /**
     * Vanilla {@code SetEntityLookTargetSometimes.Ticker.tickDownAndCheck}, run only when a turtle friend is in range and no
     * look target is set: at zero it draws the next interval and does not fire; otherwise it counts down and fires at zero.
     *
     * @param ticksUntilNextStart the ticker (0 at first)
     * @return the new ticker, and whether to look now
     */
    public static Ticked lookTicker(int ticksUntilNextStart, Draws d) {
        if (ticksUntilNextStart == 0) {
            return new Ticked(between(d, LOOK_INTERVAL_MIN, LOOK_INTERVAL_MAX), false);
        }
        int next = ticksUntilNextStart - 1;
        return new Ticked(next, next == 0);
    }

    /** The ticker after one step and whether it fired. */
    public record Ticked(int ticksUntilNextStart, boolean fire) {
    }

    // ---- RunOne(BabyFollowAdult, FollowTemptation) ----------------------------------------------------------------

    /**
     * {@code RunOne} with weights 1 and 1: {@code ShufflingList.shuffle} draws one float per entry (baby-follow first) and
     * sorts by {@code -f}, so the larger draw goes first and a tie keeps the listed order.
     *
     * @return true if the temptation is tried before the baby-follow
     */
    public static boolean temptationFirst(Draws d) {
        float follow = d.nextFloat();
        float tempt = d.nextFloat();
        return tempt > follow;
    }

    /**
     * Vanilla {@code BabyFollowAdult}: a baby with a visible adult of its kind walks to it when it is closer than 31 blocks
     * but not closer than 15, and stops 14 blocks away.
     *
     * @param baby     the mob is a baby
     * @param distance the distance to the nearest visible adult, or NaN without one
     */
    public static boolean followsAdult(boolean baby, double distance) {
        return baby && !Double.isNaN(distance) && distance < ADULT_FOLLOW_MAX + 1 && !(distance < ADULT_FOLLOW_MIN);
    }

    /** {@code BabyFollowAdult}: the walk target's close-enough distance. */
    public static int adultCloseEnough() {
        return ADULT_FOLLOW_MIN - 1;
    }

    // ---- TryFindWater -------------------------------------------------------------------------------------------

    /** What the world answers for {@link #findWater}. */
    public interface WaterProbe {
        /** The block at the offset is {@code minecraft:water}. */
        boolean water(int dx, int dy, int dz);

        /** The block above the offset is air. */
        boolean airAbove(int dx, int dy, int dz);

        /** The block centre of the offset is within 1.5 blocks of the mob's position. */
        boolean close(int dx, int dy, int dz);
    }

    /**
     * Vanilla {@code TryFindWater} once its memories allow it (no attack target, no walk target) and the mob's block is not
     * water. Before the wait is over it pushes the wait to {@code now + 22} and does nothing; else it searches the 6-block
     * Manhattan diamond for water with air above outside the mob's column, else the first water farther than 1.5 blocks,
     * and waits 40 ticks.
     *
     * @param now    the game time
     * @param waitAt the end of the current wait (0 at first)
     * @return the new wait end and the offset {dx, dy, dz} to walk to (empty if the search found nothing or did not run)
     */
    public static FoundWater findWater(long now, long waitAt, WaterProbe probe) {
        if (now < waitAt) {
            return new FoundWater(now + FIND_WATER_WAIT_PUSH, Optional.empty());
        }
        int[] best = null;
        int[] fallback = null;
        for (int[] p : withinManhattan(FIND_WATER_RANGE, FIND_WATER_RANGE, FIND_WATER_RANGE)) {
            if (p[0] == 0 && p[2] == 0) {
                continue;
            }
            if (probe.water(p[0], p[1], p[2])) {
                if (probe.airAbove(p[0], p[1], p[2])) {
                    best = p;
                    break;
                }
                if (fallback == null && !probe.close(p[0], p[1], p[2])) {
                    fallback = p;
                }
            }
        }
        if (best == null) {
            best = fallback;
        }
        return new FoundWater(now + FIND_WATER_WAIT, Optional.ofNullable(best));
    }

    /** The result of {@link #findWater}. */
    public record FoundWater(long waitAt, Optional<int[]> offset) {
    }

    /** Vanilla {@code BlockPos.withinManhattan} around the origin, in its iteration order. */
    public static List<int[]> withinManhattan(int xSize, int ySize, int zSize) {
        List<int[]> out = new ArrayList<>();
        int total = xSize + ySize + zSize;
        for (int depth = 0; depth <= total; depth++) {
            int maxX = Math.min(xSize, depth);
            for (int x = -maxX; x <= maxX; x++) {
                int maxY = Math.min(ySize, depth - Math.abs(x));
                for (int y = -maxY; y <= maxY; y++) {
                    int z = depth - Math.abs(x) - Math.abs(y);
                    if (z <= zSize) {
                        out.add(new int[] {x, y, z});
                        if (z != 0) {
                            out.add(new int[] {x, y, -z});
                        }
                    }
                }
            }
        }
        return out;
    }

    // ---- the idle-movement gate -----------------------------------------------------------------------------------

    /**
     * What the world answers for {@link #gate}, asked lazily in TFC's order.
     *
     * @param <P> a position
     */
    public interface GateWorld<P> {
        /** {@code StartAttacking(getAttackTarget)}: the nearest visible living small fish, if any. */
        boolean smallFishVisible();

        /** {@code RandomStroll.swim}'s target ({@code BehaviorUtils.getRandomSwimmablePos(10, 7)}). */
        Optional<P> swimTarget();

        /** {@code RandomStroll.stroll}'s target ({@code LandRandomPos.getPos(10, 7)}). */
        Optional<P> strollTarget();

        /** The look target's block position if there is one and its water matches the mob's; else empty. */
        Optional<P> lookTargetInSameMedium();

        /** The home if it is in this level and within {@code maxDistance} blocks of the mob's position; else empty. */
        Optional<P> home(int maxDistance);
    }

    /**
     * A walk target.
     *
     * @param target      where to
     * @param speed       the speed modifier
     * @param closeEnough the stop distance
     */
    public record Walk<P>(P target, float speed, int closeEnough) {
    }

    /**
     * The outcome of one start of the gate.
     *
     * @param attack     a small fish became the attack target
     * @param walk       the walk target set (the last writer wins), or null
     * @param busyTicks  the {@code DoNothing} duration drawn; the gate restarts {@code busyTicks + 2} ticks later
     * @param homeWaitAt the new end of the home stroll's wait
     */
    public record Gate<P>(boolean attack, Walk<P> walk, int busyTicks, long homeWaitAt) {
    }

    /**
     * The idle-movement {@code GateBehavior} (ordered, try all), started only while there is no walk target: start
     * attacking a visible small fish unless hunted recently; swim to a random spot in water at 0.5, or stroll on land at
     * 0.15; walk to the look target within 3 if it is in the same medium; do nothing for 30 to 60 ticks (the draw); stroll
     * home within 5 if home is within {@code homeMax} blocks and the 80-tick wait is over.
     *
     * @param inWater        the mob is in water or a bubble column
     * @param huntedRecently {@code HUNTED_RECENTLY} is present
     * @param now            the game time
     * @param homeWaitAt     the home stroll's wait end (0 at first)
     * @param homeMax        100 for the idle gate
     */
    public static <P> Gate<P> gate(boolean inWater, boolean huntedRecently, long now, long homeWaitAt, int homeMax,
            GateWorld<P> world, Draws d) {
        boolean attack = !huntedRecently && world.smallFishVisible();
        Walk<P> walk = null;
        if (inWater) {
            walk = world.swimTarget().map(p -> new Walk<>(p, SWIM_SPEED, 0)).orElse(null);
        } else {
            walk = world.strollTarget().map(p -> new Walk<>(p, STROLL_SPEED, 0)).orElse(null);
        }
        if (walk == null) {
            Optional<P> look = world.lookTargetInSameMedium();
            if (look.isPresent()) {
                walk = new Walk<>(look.get(), AmphibianSpecies.walkSpeed(inWater), LOOK_WALK_CLOSE_ENOUGH);
            }
        }
        int busy = between(d, DO_NOTHING_MIN, DO_NOTHING_MAX);
        HomeStroll<P> home = strollHome(now, homeWaitAt, homeMax, inWater, world);
        if (home.walk() != null) {
            walk = home.walk();
        }
        return new Gate<>(attack, walk, busy, home.waitAt());
    }

    /** The result of {@link #strollHome}. */
    public record HomeStroll<P>(Walk<P> walk, long waitAt) {
    }

    /**
     * {@code AmphibiousStrollToPoi}: with home in reach, a new walk target home (stop within 5) once the wait is over, then
     * wait 80 ticks; inside the wait nothing changes.
     */
    public static <P> HomeStroll<P> strollHome(long now, long waitAt, int maxDistance, boolean inWater, GateWorld<P> world) {
        Optional<P> home = world.home(maxDistance);
        if (home.isEmpty() || now <= waitAt) {
            return new HomeStroll<>(null, waitAt);
        }
        return new HomeStroll<>(new Walk<>(home.get(), AmphibianSpecies.walkSpeed(inWater), HOME_CLOSE_ENOUGH),
                now + HOME_INTERVAL);
    }

    /** {@code GateBehavior}: started at {@code now} with a {@code DoNothing} of {@code busyTicks}, it can restart at this tick. */
    public static long gateRestartAt(long now, int busyTicks) {
        return now + busyTicks + 2;
    }
}
