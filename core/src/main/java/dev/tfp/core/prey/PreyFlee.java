/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java @ c3b4d9536 (AVOID_RANGE, RETREAT_DURATION, wasHurtBy,
 * broadcastAvoidTarget, setAvoidTarget, wantsToStopFleeing), src/main/java/net/dries007/tfc/common/entities/ai/prey/
 * AvoidPredatorsBehavior.java (isAvoided), src/main/java/net/dries007/tfc/common/entities/ai/prey/
 * AvoidPredatorAndRammersBehavior.java (create), src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java
 * (createIdleMovementBehaviors is not ported here), src/main/java/net/dries007/tfc/common/entities/prey/Prey.java
 * (hurt); the away-position draw follows vanilla 1.21.1 SetWalkTargetAwayFrom / LandRandomPos.getPosAway /
 * RandomPos.generateRandomDirectionWithinRadians, which TFC calls
 */
package dev.tfp.core.prey;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * TFC's prey flee rules as pure functions of the stored {@link FleeState}, the sighted entities, the positions and the
 * caller-drawn random values. Nothing here ticks and nothing reads the world.
 *
 * <p>The rules are:
 * <ul>
 *   <li>Trigger ({@link #trigger}): when not already fleeing, the nearest sighting that is avoided becomes the threat.
 *       TFC has no distance check of its own here; the sensor decides what is visible. The sighting memory is set
 *       with no expiry ({@link #sighted}): only the 20-block rule ends that flee.</li>
 *   <li>Retreat length ({@link #retreatTicks}): uniform in 7 to 22 seconds, in player ticks, drawn by the caller. Only the
 *       memory set from a hit ({@link #hurt}, {@link #broadcast}) carries it.</li>
 *   <li>Run ({@link #runsAway}): a new walk target away from the threat is chosen only while the threat is closer than
 *       15 blocks ({@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.1F, 15, false)}), at speed 1.1.</li>
 *   <li>Stop ({@link #wantsToStopFleeing}): the memory has expired, or the threat is more than 20 blocks away
 *       (the squared distance is strictly greater than 400).</li>
 *   <li>Hurt ({@link #hurt}): only while fleeing, and only from a different entity type, the attacker replaces the threat.</li>
 *   <li>Friends ({@link #broadcast}): a same-type friend that sees the attacker takes the nearer of its own threat and the attacker.</li>
 *   <li>Away position ({@link #awayOffset}): vanilla's {@code getPosAway(mob, 16, 7, threat)} candidate, a random point
 *       within a half-circle facing away from the threat, at most 16 blocks out on each axis.</li>
 * </ul>
 *
 * <p>Out of scope: the brain, the activities and priorities, the sensors, the walk and look targets, the {@code AnimalPanic}
 * behaviour for a plain hit, the speed of the run, the visible-entity sorting, and the sounds and animations.
 */
public final class PreyFlee {

    /** TFC {@code AVOID_RANGE = 20 * 20}: the squared distance above which a fleeing prey stops. */
    public static final int AVOID_RANGE_SQ = 20 * 20;

    /** The retreat length bounds in seconds, from TFC's {@code TimeUtil.rangeOfSeconds(7, 22)}. */
    public static final int RETREAT_MIN_SECONDS = 7;
    public static final int RETREAT_MAX_SECONDS = 22;

    /** The retreat length bounds in player ticks, inclusive: {@code 7 * 20} and {@code 22 * 20}. */
    public static final int RETREAT_MIN_TICKS = RETREAT_MIN_SECONDS * CalendarTicks.PLAYER_TICKS_PER_SECOND;
    public static final int RETREAT_MAX_TICKS = RETREAT_MAX_SECONDS * CalendarTicks.PLAYER_TICKS_PER_SECOND;

    /** TFC {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.1F, 15, false)}: a new away target only while closer than 15. */
    public static final int AWAY_DISTANCE = 15;

    /** TFC's speed modifier for the run ({@code 1.1F}); the armadillo uses {@code 2.0F}. */
    public static final float FLEE_SPEED = 1.1f;
    public static final float ARMADILLO_FLEE_SPEED = 2.0f;

    /** Vanilla {@code LandRandomPos.getPosAway(mob, 16, 7, pos)} as called by {@code SetWalkTargetAwayFrom}. */
    public static final int AWAY_HORIZONTAL = 16;
    public static final int AWAY_VERTICAL = 7;

    /** A block offset from the prey's block position. */
    public record Offset(int dx, int dy, int dz) {
    }

    private PreyFlee() {
    }

    /**
     * TFC's trigger in {@code AvoidPredatorAndRammersBehavior}: while not fleeing, the nearest visible sighting that is
     * avoided starts a flee. Nearest is by squared three-dimensional distance, the {@code findClosest} choice; ties keep
     * the earlier sighting in the list.
     *
     * @param fleeing  {@code true} if the {@code AVOID_TARGET} memory is present (TFC's {@code absent} precondition fails)
     * @param prey     the prey animal's position
     * @param visible  the visible living entities, in any order
     * @return the new threat, or empty when already fleeing or no sighting is avoided
     */
    public static Optional<Sighting> trigger(boolean fleeing, Position prey, List<Sighting> visible) {
        return trigger(fleeing, prey, visible, Avoidance.PREDATORS_PLAYERS_AND_RAMMERS);
    }

    /**
     * The trigger for a given brain's {@link Avoidance} form.
     *
     * @param fleeing   {@code true} if the {@code AVOID_TARGET} memory is present
     * @param prey      the prey animal's position
     * @param visible   the visible living entities
     * @param avoidance which sightings this brain avoids
     * @return the new threat, or empty
     */
    public static Optional<Sighting> trigger(boolean fleeing, Position prey, List<Sighting> visible, Avoidance avoidance) {
        if (fleeing) {
            return Optional.empty();
        }
        Sighting best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Sighting sighting : visible) {
            if (!avoidance.avoids(sighting)) {
                continue;
            }
            final double distance = prey.distanceSq(sighting.at());
            if (best == null || distance < bestDistance) {
                best = sighting;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The retreat length for a fresh flee: {@code RETREAT_DURATION.sample(random)} in player ticks, inclusive at both ends.
     *
     * @param random the caller's random source
     * @return a tick count in [{@link #RETREAT_MIN_TICKS}, {@link #RETREAT_MAX_TICKS}]
     */
    public static int retreatTicks(RandomGenerator random) {
        return retreatTicksForOffset(random.nextInt(RETREAT_MAX_TICKS - RETREAT_MIN_TICKS + 1));
    }

    /**
     * The retreat length for a drawn offset: {@code RETREAT_MIN_TICKS + offset}.
     *
     * @param offset the draw, in [0, {@code RETREAT_MAX_TICKS - RETREAT_MIN_TICKS}]
     * @return the tick count
     * @throws IllegalArgumentException if the offset is out of range
     */
    public static int retreatTicksForOffset(int offset) {
        if (offset < 0 || offset > RETREAT_MAX_TICKS - RETREAT_MIN_TICKS) {
            throw new IllegalArgumentException("offset outside [0, " + (RETREAT_MAX_TICKS - RETREAT_MIN_TICKS) + "]: " + offset);
        }
        return RETREAT_MIN_TICKS + offset;
    }

    /**
     * The memory set by {@code AvoidPredatorsBehavior}: {@code avoiding.set(closest)}, with no expiry. The flee ends only by
     * {@link #wantsToStopFleeing} (threat more than 20 blocks away) or when the threat is gone.
     *
     * @param threatType the sighted threat's type
     * @return the new state
     */
    public static FleeState sighted(String threatType) {
        return new FleeState(threatType, Long.MAX_VALUE);
    }

    /**
     * Whether the run picks a new walk target away from the threat: vanilla {@code mob.closerThan(pos, 15)}, a squared
     * distance strictly below {@code 15 * 15}. Between 15 and 20 blocks the prey stays in the avoid activity and idles.
     *
     * @param threatDistanceSq the squared distance to the threat
     * @return {@code true} to run away
     */
    public static boolean runsAway(double threatDistanceSq) {
        return threatDistanceSq < AWAY_DISTANCE * AWAY_DISTANCE;
    }

    /**
     * TFC {@code setAvoidTarget}: starts (or restarts) a flee from the attacker with a fresh retreat length.
     *
     * @param nowTick     the current player tick
     * @param threatType  the attacker's type
     * @param retreatTicks the drawn retreat length, see {@link #retreatTicks}
     * @return the new state
     */
    public static FleeState startFlee(long nowTick, String threatType, int retreatTicks) {
        return new FleeState(threatType, nowTick + retreatTicks);
    }

    /**
     * TFC {@code PreyAi.wantsToStopFleeing}: the flee ends when the memory has expired, or when the squared distance from the
     * prey to its threat is strictly greater than {@link #AVOID_RANGE_SQ}. Exactly 20 blocks (squared 400) keeps fleeing.
     *
     * @param state                the stored flee state
     * @param nowTick              the current player tick
     * @param threatDistanceSq     the squared distance to the threat's current position; ignored when not fleeing
     * @return {@code true} if the prey should stop fleeing
     */
    public static boolean wantsToStopFleeing(FleeState state, long nowTick, double threatDistanceSq) {
        return !state.isActive(nowTick) || threatDistanceSq > AVOID_RANGE_SQ;
    }

    /**
     * TFC {@code PreyAi.wasHurtBy}: when a flee is active and the attacker is a different type from the current threat,
     * the attacker becomes the threat with a fresh retreat length. When not fleeing, or the attacker is the same type,
     * the state is unchanged. (A plain hit with no flee is handled by the vanilla {@code AnimalPanic} behaviour, which is
     * not ported here.)
     *
     * @param state        the stored flee state
     * @param nowTick      the current player tick
     * @param attackerType the attacker's type
     * @param retreatTicks the drawn retreat length, used only when the threat switches
     * @return the new state; equal to {@code state} when nothing changes
     */
    public static FleeState hurt(FleeState state, long nowTick, String attackerType, int retreatTicks) {
        if (!state.isActive(nowTick) || state.threatType().equals(attackerType)) {
            return state;
        }
        return startFlee(nowTick, attackerType, retreatTicks);
    }

    /**
     * TFC {@code broadcastAvoidTarget} for one friend: a same-type friend that sees the attacker sets its threat to the
     * nearer of its own current threat and the attacker (vanilla {@code BehaviorUtils.getNearestTarget}), with a fresh
     * retreat length. A friend with no current threat takes the attacker.
     *
     * <p>The caller passes only friends of the hurt prey's type (TFC filters with {@code e.getType() == prey.getType()}).
     *
     * @param friendPosition    the friend's position
     * @param friendState       the friend's stored flee state
     * @param friendNowTick     the current player tick
     * @param currentThreat     the position of the friend's current threat, or {@code null} if it has none
     * @param attacker          the attacker's sighting (type and position)
     * @param retreatTicks      the drawn retreat length
     * @return the friend's new state
     */
    public static FleeState broadcast(Position friendPosition, FleeState friendState, long friendNowTick,
                                      Position currentThreat, Sighting attacker, int retreatTicks) {
        final String nearest = friendTakesAttacker(friendPosition, friendState, friendNowTick, currentThreat, attacker.at())
            ? attacker.type() : friendState.threatType();
        return startFlee(friendNowTick, nearest, retreatTicks);
    }

    /**
     * Which entity {@link #broadcast} keeps: the attacker when the friend has no active threat, or when the attacker is
     * strictly closer (vanilla {@code BehaviorUtils.getNearestTarget}); otherwise the friend's own threat.
     *
     * @return {@code true} if the friend's new threat is the attacker
     */
    public static boolean friendTakesAttacker(Position friendPosition, FleeState friendState, long friendNowTick,
                                              Position currentThreat, Position attacker) {
        if (!friendState.isActive(friendNowTick) || currentThreat == null) {
            return true;
        }
        return friendPosition.distanceSq(attacker) < friendPosition.distanceSq(currentThreat);
    }

    /**
     * Vanilla {@code RandomPos.generateRandomDirectionWithinRadians(random, 16, 7, 0, dx, dz, PI / 2)}, the candidate of
     * {@code getPosAway}: an angle within a quarter turn either side of the away direction, a radius of
     * {@code sqrt(u) * sqrt(2) * 16}, rejected when either horizontal offset exceeds 16. The draws are in vanilla order:
     * {@code nextFloat} (angle), {@code nextDouble} (radius), then {@code nextInt(15)} (height) only when accepted.
     *
     * @param awayX  the prey x minus the threat x
     * @param awayZ  the prey z minus the threat z
     * @param random the draws
     * @return the block offset from the prey, or empty when the candidate is rejected
     */
    public static Optional<Offset> awayOffset(double awayX, double awayZ, RandomGenerator random) {
        final float angleDraw = random.nextFloat();
        final double radiusDraw = random.nextDouble();
        final Optional<double[]> xz = awayXz(awayX, awayZ, angleDraw, radiusDraw);
        if (xz.isEmpty()) {
            return Optional.empty();
        }
        final int dy = random.nextInt(2 * AWAY_VERTICAL + 1) - AWAY_VERTICAL;
        return Optional.of(new Offset((int) Math.floor(xz.get()[0]), dy, (int) Math.floor(xz.get()[1])));
    }

    /**
     * The horizontal part of {@link #awayOffset} for given draws.
     *
     * @param awayX      the prey x minus the threat x
     * @param awayZ      the prey z minus the threat z
     * @param angleDraw  the {@code nextFloat()} draw in [0, 1)
     * @param radiusDraw the {@code nextDouble()} draw in [0, 1)
     * @return {x, z}, or empty when rejected
     */
    public static Optional<double[]> awayXz(double awayX, double awayZ, float angleDraw, double radiusDraw) {
        final double maxAngle = (float) Math.PI / 2F;
        final double base = Math.atan2(awayZ, awayX) - (double) ((float) Math.PI / 2F);
        final double angle = base + (double) (2.0F * angleDraw - 1.0F) * maxAngle;
        final double radius = Math.sqrt(radiusDraw) * (double) (float) Math.sqrt(2.0F) * AWAY_HORIZONTAL;
        final double x = -radius * Math.sin(angle);
        final double z = radius * Math.cos(angle);
        if (Math.abs(x) > AWAY_HORIZONTAL || Math.abs(z) > AWAY_HORIZONTAL) {
            return Optional.empty();
        }
        return Optional.of(new double[] {x, z});
    }
}
