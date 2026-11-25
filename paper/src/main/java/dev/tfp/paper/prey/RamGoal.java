/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/RammingPreyAi.java @ c3b4d9536 (initCoreActivity,
 * initRamActivity, updateActivity), ai/prey/PrepareRamNearestTargetTFC.java, ai/prey/RamTargetTFC.java, same commit
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.prey.RamCharge;
import dev.tfp.core.prey.RamRules;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * TFC's {@code RAM} activity for the four rammers: with no cooldown and no flee, pick the nearest visible entity that the
 * ram conditions allow ({@link RamRules#canRam}), stand still looking at it for 20 ticks, then charge at speed 3 to the
 * far edge of its block; the first allowed entity touched takes {@code 6 x multiplier} damage and the knockback. A
 * cooldown of 600 to 1000 ticks (male) or 1000 to 1600 (female) follows; a nearby attacker erases it.
 *
 * <p>The phases are the pure {@link RamCharge} machine; the goal reads the positions, feeds them in and applies what it
 * returns (path, look, damage). The question is asked every {@value #PERIOD} ticks, staggered per animal.
 */
final class RamGoal extends AnimalGoal {
    static final int PERIOD = 10;

    private final PreyBrain brain;
    private final PreyBody body;
    private final PreySenses senses;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final double reach;

    RamGoal(PreyBrain brain, PreyBody body, PreySenses senses, LongSupplier clock, RandomGenerator random) {
        super(brain.mob, "prey_ram", PERIOD, GoalType.MOVE, GoalType.LOOK);
        this.brain = brain;
        this.body = body;
        this.senses = senses;
        this.clock = clock;
        this.random = random;
        this.reach = RamRules.rammingReach(brain.species);
        if (brain.ram == null) {
            // RammingPreyAi.initMemories: a cooldown drawn at spawn
            brain.ram = RamCharge.cooling(clock.getAsLong() + RamRules.cooldown(PreySenses.male(mob), random));
        }
    }

    private static int[] block(LivingEntity e) {
        Location l = e.getLocation();
        return new int[] {l.getBlockX(), l.getBlockY(), l.getBlockZ()};
    }

    private boolean adultMale(long now) {
        return PreySenses.male(mob) && !PreySenses.baby(mob, now);
    }

    @Override
    protected boolean wants() {
        long now = clock.getAsLong();
        // core activity: EraseMemoryIf(hasNearbyAttacker, RAM_COOLDOWN_TICKS) and EraseMemoryIf(attackerHasLeft, HURT_BY_ENTITY)
        LivingEntity attacker = brain.attackerAt(now);
        if (attacker != null) {
            double d = PreySenses.at(attacker).distanceSq(PreySenses.at(mob));
            if (RamRules.attackerHasLeft(d)) {
                brain.attacker = null;
            } else if (RamRules.hasNearbyAttacker(d)) {
                brain.ram = brain.ram.provoked(now);
            }
        }
        // updateActivity: AVOID before RAM
        if (brain.fleeing(now) || !brain.ram.ready(now)) {
            return false;
        }
        boolean male = adultMale(now);
        LivingEntity best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        Reading seen = PreyFleeGoal.seen(brain, body, now);
        for (int i = 0; i < seen.size(); i++) {
            LivingEntity e = seen.get(i);
            if (!PreyBrain.alive(e)) {
                continue;
            }
            double d = PreySenses.at(e).distanceSq(PreySenses.at(mob));
            if (d < bestSq && RamRules.canRam(male, senses.candidate(e, now)) && seen.visible(i)) {
                best = e;
                bestSq = d;
            }
        }
        if (best == null) {
            brain.ram = RamCharge.failed(now);
            return false;
        }
        brain.ramTarget = best;
        brain.ram = RamCharge.prepare(now, block(mob), block(best));
        return true;
    }

    @Override
    protected boolean keepsGoing() {
        return brain.ram.phase() != RamCharge.Phase.COOLDOWN && !brain.fleeing(clock.getAsLong());
    }

    @Override
    protected void begin() {
        body.stop(mob);
    }

    @Override
    protected void step() {
        long now = clock.getAsLong();
        RamCharge ram = brain.ram;
        if (ram.phase() == RamCharge.Phase.PREPARING) {
            LivingEntity target = brain.ramTarget;
            boolean alive = PreyBrain.alive(target);
            if (alive) {
                body.lookAt(mob, target);
            }
            RamCharge next = ram.tickPrepare(now, block(mob), alive ? block(target) : null);
            if (next.phase() == RamCharge.Phase.CHARGING) {
                body.sound(mob, "entity." + brain.species.substring(brain.species.indexOf(':') + 1) + ".attack");
                double[] t = next.ramTarget();
                PathBudget.urgent();
                body.moveTo(mob, new Location(mob.getWorld(), t[0], t[1], t[2]), RamRules.RAM_SPEED);
            }
            brain.ram = next;
        } else if (ram.phase() == RamCharge.Phase.CHARGING) {
            boolean male = adultMale(now);
            LivingEntity hit = null;
            for (LivingEntity e : body.touching(mob, reach)) {
                if (RamRules.canRam(male, senses.candidate(e, now))) {
                    hit = e;
                    break;
                }
            }
            if (hit != null) {
                strike(hit, ram, now);
            }
            boolean arrived = !body.hasPath(mob);
            boolean finishing = hit != null || arrived || now - ram.startedAt() >= RamRules.RAM_TIMEOUT;
            brain.ram = ram.tickCharge(now, hit != null, arrived,
                    finishing ? RamRules.cooldown(PreySenses.male(mob), random) : 0);
        }
    }

    private void strike(LivingEntity target, RamCharge ram, long now) {
        PotionEffect speedFx = mob.getPotionEffect(PotionEffectType.SPEED);
        PotionEffect slowFx = mob.getPotionEffect(PotionEffectType.SLOWNESS);
        double base = mob.getAttribute(Attribute.MOVEMENT_SPEED) == null ? 0.16
                : mob.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        boolean blocked = target instanceof HumanEntity h && h.isBlocking();
        double strength = RamRules.knockback((float) (base * RamRules.RAM_SPEED),
                speedFx == null ? 0 : speedFx.getAmplifier() + 1, slowFx == null ? 0 : slowFx.getAmplifier() + 1,
                blocked, ram.multiplier(), PreySenses.baby(mob, now));
        body.ram(mob, target, RamRules.damage(ram.multiplier()), strength, ram.direction()[0], ram.direction()[1]);
        body.sound(mob, "entity.ramming.impact");
    }

    @Override
    protected void end() {
        long now = clock.getAsLong();
        if (brain.ram.phase() == RamCharge.Phase.PREPARING) {
            brain.ram = RamCharge.failed(now);
        } else if (brain.ram.phase() == RamCharge.Phase.CHARGING) {
            brain.ram = RamCharge.cooling(now + RamRules.cooldown(PreySenses.male(mob), random));
        }
        brain.ramTarget = null;
        body.stop(mob);
    }
}
