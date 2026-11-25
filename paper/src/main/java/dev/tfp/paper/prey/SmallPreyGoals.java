/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/TFCAvoidEntityGoal.java @ c3b4d9536, prey/TFCPanda.java
 * (PandaAvoidGoal), prey/TFCArmadillo.java (isScaredBy), ai/prey/ScareSensor.java, same commit
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.SmallPrey;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Panda;

/** The fox, rabbit, panda and armadillo additions to their vanilla AI. */
final class SmallPreyGoals {
    private SmallPreyGoals() {
    }

    /**
     * {@code TFCAvoidEntityGoal(mob, PathfinderMob.class, 8, far, near, LAND_PREDATORS)}: run from the nearest moving land
     * predator within 8 blocks to a random spot away from it that is further from it, at the far speed beyond 7 blocks and
     * the near speed within. The panda only when it is worried and free to act ({@code PandaAvoidGoal}).
     */
    static final class AvoidPredator extends AnimalGoal {
        static final int PERIOD = 4;
        static final int ATTEMPTS = 10;
        private final PreyBody body;
        private final PreySenses senses;
        private final SmallPrey.Speeds speeds;
        private final RandomGenerator random;
        private LivingEntity threat;
        private Location target;
        private double speed;

        AvoidPredator(PreyBrain brain, PreyBody body, PreySenses senses, SmallPrey.Speeds speeds, RandomGenerator random) {
            super(brain.mob, "prey_avoid_predator", PERIOD, GoalType.MOVE);
            this.body = body;
            this.senses = senses;
            this.speeds = speeds;
            this.random = random;
        }

        LivingEntity threat() {
            return threat;
        }

        private boolean pandaReady() {
            if (!(mob instanceof Panda p)) {
                return true;
            }
            return p.getCombinedGene() == Panda.Gene.WORRIED && !p.isOnBack() && !p.isScared() && !p.isEating()
                && !p.isRolling() && !p.isSitting();
        }

        @Override
        protected boolean wants() {
            threat = null;
            if (!pandaReady()) {
                return false;
            }
            List<LivingEntity> near = new ArrayList<>();
            List<SmallPrey.Threat> facts = new ArrayList<>();
            double y = mob.getLocation().getY();
            for (LivingEntity e : body.visible(mob, SmallPrey.AVOID_RANGE)) {
                if (Math.abs(e.getLocation().getY() - y) <= SmallPrey.AVOID_VERTICAL && senses.landPredator(e)) {
                    near.add(e);
                    facts.add(senses.threat(e));
                }
            }
            Optional<SmallPrey.Threat> t = SmallPrey.avoid(PreySenses.at(mob), facts);
            if (t.isEmpty()) {
                return false;
            }
            LivingEntity from = near.get(facts.indexOf(t.get()));
            Location self = mob.getLocation();
            Location at = from.getLocation();
            double threatSq = at.distanceSquared(self);
            for (int i = 0; i < ATTEMPTS; i++) {
                Optional<PreyFlee.Offset> off = PreyFlee.awayOffset(self.getX() - at.getX(), self.getZ() - at.getZ(), random);
                if (off.isEmpty()) {
                    continue;
                }
                Location to = body.standable(mob, off.get().dx(), off.get().dy(), off.get().dz());
                // AvoidEntityGoal: the spot must not be closer to the threat than the animal is
                if (to == null || at.distanceSquared(to) < threatSq) {
                    continue;
                }
                speed = speeds.at(threatSq);
                if (body.moveTo(mob, to, speed)) {
                    threat = from;
                    target = to;
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean keepsGoing() {
            return body.hasPath(mob) && PreyBrain.alive(threat);
        }

        @Override
        protected void step() {
            if (!PreyBrain.alive(threat) || target == null) {
                return;
            }
            double wanted = speeds.at(threat.getLocation().distanceSquared(mob.getLocation()));
            if (wanted != speed) {
                speed = wanted;
                body.moveTo(mob, target, speed);
            }
        }

        @Override
        protected void end() {
            threat = null;
            target = null;
            body.stop(mob);
        }
    }

    /**
     * {@code ScareSensor} with {@code TFCArmadillo.isScaredBy}: an awake land predator within 7 blocks across and 2 up or
     * down sets {@code DANGER_DETECTED_RECENTLY} for 80 ticks; the armadillo's vanilla brain then rolls it up and rolls
     * it out when the memory is gone. Asked every 5 ticks (the sensor rate); it never runs as a goal itself.
     */
    static final class ArmadilloScare extends AnimalGoal {
        private final PreyBrain brain;
        private final PreyBody body;
        private final PreySenses senses;
        private final LongSupplier clock;
        private final java.util.function.BiConsumer<LivingEntity, Boolean> danger;

        ArmadilloScare(PreyBrain brain, PreyBody body, PreySenses senses, LongSupplier clock,
                java.util.function.BiConsumer<LivingEntity, Boolean> danger) {
            super(brain.mob, "prey_armadillo_scare", 5);
            this.brain = brain;
            this.body = body;
            this.senses = senses;
            this.clock = clock;
            this.danger = danger;
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            List<SmallPrey.Threat> predators = new ArrayList<>();
            for (LivingEntity e : PreyFleeGoal.seen(brain, body, now)) {
                if (senses.landPredator(e)) {
                    predators.add(senses.threat(e));
                }
            }
            if (SmallPrey.armadilloScared(PreySenses.at(mob), predators)) {
                brain.dangerUntil = now + SmallPrey.SCARE_MEMORY_TICKS;
                danger.accept(mob, true);
            } else if (brain.dangerUntil != Long.MIN_VALUE && now >= brain.dangerUntil) {
                brain.dangerUntil = Long.MIN_VALUE;
                danger.accept(mob, false);
            }
            return false;
        }
    }
}
