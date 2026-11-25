/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java @ c3b4d9536 (initRetreatActivity,
 * wantsToStopFleeing), ai/prey/AvoidPredatorAndRammersBehavior.java, ai/prey/AvoidPredatorsBehavior.java,
 * prey/TFCArmadilloAi.java (initRetreatActivity at speed 2), same commit
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.LivingEntity;

/**
 * TFC's {@code AVOID} activity: the nearest avoided sighting ({@link dev.tfp.core.prey.Avoidance}) starts a flee with no
 * expiry; a hit while fleeing switches the threat with a 7 to 22 second retreat ({@link PreyBrain#hurt}). While the threat
 * is closer than 15 blocks the animal runs to a random spot away from it; the flee ends once the threat is more than 20
 * blocks away, the retreat time is over, or the threat is gone.
 *
 * <p>Asked every {@value #PERIOD} ticks, staggered per animal; the sensor reading is refreshed at most every
 * {@value #SENSE_TICKS} ticks (vanilla's sensor rate). The decision is the pure {@link PreyFlee} rules on a snapshot of
 * the sightings; the goal only applies it (path, stop).
 */
final class PreyFleeGoal extends AnimalGoal {
    static final int PERIOD = 5;
    /** Vanilla {@code NearestLivingEntitySensor}: every 20 ticks, 16 blocks. */
    static final int SENSE_TICKS = 20;
    static final double SENSE_RANGE = 16.0;
    /** {@code RandomPos.generateRandomPos}: up to 10 candidates. */
    static final int ATTEMPTS = 10;

    private final PreyBrain brain;
    private final PreyBody body;
    private final PreySenses senses;
    private final LongSupplier clock;
    private final RandomGenerator random;

    PreyFleeGoal(PreyBrain brain, PreyBody body, PreySenses senses, LongSupplier clock, RandomGenerator random) {
        super(brain.mob, "prey_flee", PERIOD, GoalType.MOVE);
        this.brain = brain;
        this.body = body;
        this.senses = senses;
        this.clock = clock;
        this.random = random;
    }

    /** The visible entities, re-read at most every {@link #SENSE_TICKS}. Shared by the brain's goals. */
    static List<LivingEntity> seen(PreyBrain brain, PreyBody body, long now) {
        if (brain.seenAt == Long.MIN_VALUE || now - brain.seenAt >= SENSE_TICKS || now < brain.seenAt) {
            brain.seen = body.visible(brain.mob, SENSE_RANGE);
            brain.seenAt = now;
        }
        return brain.seen;
    }

    private double threatDistanceSq() {
        return PreySenses.at(brain.threat).distanceSq(PreySenses.at(mob));
    }

    @Override
    protected boolean wants() {
        long now = clock.getAsLong();
        if (brain.fleeing(now)) {
            return !PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq());
        }
        brain.setThreat(null, dev.tfp.core.prey.FleeState.IDLE);
        List<LivingEntity> seen = seen(brain, body, now);
        List<Sighting> sightings = new ArrayList<>(seen.size());
        for (LivingEntity e : seen) {
            sightings.add(senses.sighting(e));
        }
        Optional<Sighting> found = PreyFlee.trigger(false, PreySenses.at(mob), sightings, brain.avoidance);
        if (found.isEmpty()) {
            return false;
        }
        brain.setThreat(seen.get(sightings.indexOf(found.get())), PreyFlee.sighted(found.get().type()));
        return true;
    }

    @Override
    protected boolean keepsGoing() {
        long now = clock.getAsLong();
        return brain.fleeing(now) && !PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq());
    }

    @Override
    protected void begin() {
        body.stop(mob);
        run();
    }

    @Override
    protected void step() {
        if ((mob.getTicksLived() + mob.getEntityId()) % 10 == 0 && !body.hasPath(mob)) {
            run();
        }
    }

    /** {@code SetWalkTargetAwayFrom}: a new walk target away from the threat while it is within 15 blocks. */
    private void run() {
        if (!PreyBrain.alive(brain.threat) || !PreyFlee.runsAway(threatDistanceSq())
                || (mob instanceof Armadillo a && a.getState() != Armadillo.State.IDLE)) {
            return;
        }
        Location self = mob.getLocation();
        Location from = brain.threat.getLocation();
        for (int i = 0; i < ATTEMPTS; i++) {
            Optional<PreyFlee.Offset> off = PreyFlee.awayOffset(self.getX() - from.getX(), self.getZ() - from.getZ(), random);
            if (off.isEmpty()) {
                continue;
            }
            Location to = body.standable(mob, off.get().dx(), off.get().dy(), off.get().dz());
            if (to != null && body.moveTo(mob, to, brain.fleeSpeed)) {
                return;
            }
        }
    }

    @Override
    protected void end() {
        body.stop(mob);
        long now = clock.getAsLong();
        // EraseMemoryIf(wantsToStopFleeing): a goal pre-empted by the panic keeps its memory
        if (!brain.fleeing(now) || PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq())) {
            brain.setThreat(null, dev.tfp.core.prey.FleeState.IDLE);
        }
    }
}
