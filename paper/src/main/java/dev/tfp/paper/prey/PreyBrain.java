/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/Prey.java @ c3b4d9536 (hurt), ai/prey/PreyAi.java
 * (wasHurtBy, broadcastAvoidTarget, updateActivity), ai/prey/RammingPreyAi.java (updateActivity: AVOID, RAM, IDLE)
 */
package dev.tfp.paper.prey;

import dev.tfp.core.prey.Avoidance;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.RamCharge;
import dev.tfp.core.prey.WildPrey;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * The brain memories of one prey animal that its goals share: TFC's {@code AVOID_TARGET} (with expiry), the panic,
 * {@code HURT_BY_ENTITY}, the ram state, and the last sensor reading. Owned by the animal's thread; nothing ticks it.
 * Timestamps are player ticks.
 */
final class PreyBrain {
    /** Vanilla {@code LivingEntity}: the last attacker is forgotten 100 ticks after the hit ({@code HURT_BY_ENTITY}). */
    static final int HURT_BY_MEMORY_TICKS = 100;

    final Mob mob;
    final String species;
    /** The TFC class, or {@code null} for the armadillo (vanilla brain with TFC's additions). */
    final WildPrey.Kind kind;
    final Avoidance avoidance;
    final float fleeSpeed;

    FleeState flee = FleeState.IDLE;
    LivingEntity threat;
    long panicUntil = Long.MIN_VALUE;
    LivingEntity attacker;
    long hurtAt = Long.MIN_VALUE;
    RamCharge ram;
    LivingEntity ramTarget;
    /** {@code LOOK_TARGET}: a player looked at. */
    LivingEntity lookTarget;
    /** The tick the armadillo's own {@code DANGER_DETECTED_RECENTLY} runs out, or {@code Long.MIN_VALUE}. */
    long dangerUntil = Long.MIN_VALUE;
    /** The last {@code NEAREST_VISIBLE_LIVING_ENTITIES} reading and its tick. */
    List<LivingEntity> seen = List.of();
    long seenAt = Long.MIN_VALUE;

    PreyBrain(Mob mob, String species, WildPrey.Kind kind, Avoidance avoidance, float fleeSpeed) {
        this.mob = mob;
        this.species = species;
        this.kind = kind;
        this.avoidance = avoidance;
        this.fleeSpeed = fleeSpeed;
    }

    static boolean alive(LivingEntity e) {
        return e != null && e.isValid() && !e.isDead();
    }

    /** The {@code AVOID} activity is active: the memory is present and its entity still exists. */
    boolean fleeing(long now) {
        return flee.isActive(now) && alive(threat);
    }

    boolean panicking(long now) {
        return now < panicUntil;
    }

    /** {@code HURT_BY_ENTITY} at {@code now}. */
    LivingEntity attackerAt(long now) {
        return alive(attacker) && now - hurtAt <= HURT_BY_MEMORY_TICKS ? attacker : null;
    }

    void setThreat(LivingEntity by, FleeState state) {
        threat = by;
        flee = state;
    }

    /**
     * TFC {@code Prey.hurt}: the hurt-by memory, the {@code AnimalPanic} start, and {@code wasHurtBy}, which switches the
     * threat only while already fleeing from a different type.
     *
     * @return {@code true} if the threat switched to {@code by} (the caller then broadcasts to friends)
     */
    boolean hurt(LivingEntity by, long now, boolean panicCause, RandomGenerator random) {
        if (by != null) {
            attacker = by;
            hurtAt = now;
        }
        if (kind == null || !kind.panics()) {
            return false;
        }
        if (panicCause && !panicking(now)) {
            panicUntil = now + dev.tfp.core.prey.PreyIdle.panicTicks(random);
        }
        if (by == null || !fleeing(now)) {
            return false;
        }
        FleeState next = PreyFlee.hurt(flee, now, PreySenses.species(by), PreyFlee.retreatTicks(random));
        if (next.equals(flee)) {
            return false;
        }
        setThreat(by, next);
        return true;
    }
}
