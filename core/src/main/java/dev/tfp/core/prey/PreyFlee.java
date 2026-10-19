/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java (AVOID_RANGE, RETREAT_DURATION, wasHurtBy,
 * broadcastAvoidTarget, setAvoidTarget, wantsToStopFleeing), src/main/java/net/dries007/tfc/common/entities/ai/prey/
 * AvoidPredatorsBehavior.java (isAvoided), src/main/java/net/dries007/tfc/common/entities/ai/prey/
 * AvoidPredatorAndRammersBehavior.java (create), src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java
 * (createIdleMovementBehaviors is not ported here), src/main/java/net/dries007/tfc/common/entities/prey/Prey.java
 * (hurt) @ c3b4d9536
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
 *       TFC has no distance check of its own here; the sensor decides what is visible.</li>
 *   <li>Retreat length ({@link #retreatTicks}): uniform in 7 to 22 seconds, in player ticks, drawn by the caller.</li>
 *   <li>Stop ({@link #wantsToStopFleeing}): the memory has expired, or the threat is more than 20 blocks away
 *       (the squared distance is strictly greater than 400).</li>
 *   <li>Hurt ({@link #hurt}): only while fleeing, and only from a different entity type, the attacker replaces the threat.</li>
 *   <li>Friends ({@link #broadcast}): a same-type friend that sees the attacker takes the nearer of its own threat and the attacker.</li>
 *   <li>Flee target ({@link #fleeTarget}): 15 blocks directly away from the threat, with the caller's radius scale and angle offset.</li>
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

    /** The horizontal radius passed to TFC's {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.1F, 15, false)}. */
    public static final double FLEE_RADIUS_BLOCKS = 15.0;

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
        if (fleeing) {
            return Optional.empty();
        }
        Sighting best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Sighting sighting : visible) {
            if (!sighting.avoided()) {
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
        final boolean hasThreat = friendState.isActive(friendNowTick) && currentThreat != null;
        if (!hasThreat) {
            return startFlee(friendNowTick, attacker.type(), retreatTicks);
        }
        // Vanilla getCloserEntity: keep the current threat unless the attacker is strictly closer.
        final boolean attackerCloser = friendPosition.distanceSq(attacker.at()) < friendPosition.distanceSq(currentThreat);
        final String nearest = attackerCloser ? attacker.type() : friendState.threatType();
        return startFlee(friendNowTick, nearest, retreatTicks);
    }

    /**
     * The flee target: a point {@code FLEE_RADIUS_BLOCKS * radiusScale} blocks from the prey in the direction away from the
     * threat, rotated by {@code angleOffsetRadians}. The caller draws the scale and the offset (TFC's
     * {@code DefaultRandomPos} candidate is vanilla and not in the TFC tree). The Y is kept at the prey's Y.
     *
     * <p>When the prey and the threat share a horizontal position, the base direction is the +x axis.
     *
     * @param prey                the prey position
     * @param threat              the threat position
     * @param radiusScale         the fraction of the radius, in (0, 1]
     * @param angleOffsetRadians  the rotation added to the away direction
     * @return the target position
     * @throws IllegalArgumentException if the scale is outside (0, 1]
     */
    public static Position fleeTarget(Position prey, Position threat, double radiusScale, double angleOffsetRadians) {
        if (!(radiusScale > 0.0 && radiusScale <= 1.0)) {
            throw new IllegalArgumentException("radiusScale outside (0, 1]: " + radiusScale);
        }
        final double dx = prey.x() - threat.x();
        final double dz = prey.z() - threat.z();
        final double base = (dx == 0.0 && dz == 0.0) ? 0.0 : Math.atan2(dz, dx);
        final double angle = base + angleOffsetRadians;
        final double distance = FLEE_RADIUS_BLOCKS * radiusScale;
        return new Position(prey.x() + Math.cos(angle) * distance, prey.y(), prey.z() + Math.sin(angle) * distance);
    }
}
