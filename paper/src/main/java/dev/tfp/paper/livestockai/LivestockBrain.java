/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/LivestockAi.java @ c3b4d9536 (MEMORY_TYPES),
 * livestock/TFCAnimal.java (hurt), ai/prey/PreyAi.java (wasHurtBy)
 */
package dev.tfp.paper.livestockai;

import dev.tfp.core.livestockai.LivestockRoster;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.PreyIdle;
import dev.tfp.paper.prey.Reading;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The brain memories of one domestic animal that its goals share: {@code AVOID_TARGET} (with expiry),
 * {@code IS_PANICKING}, {@code TEMPTING_PLAYER} and {@code TEMPTATION_COOLDOWN_TICKS}, {@code BREED_TARGET},
 * {@code LOOK_TARGET} and the last {@code NEAREST_VISIBLE_LIVING_ENTITIES} reading. Owned by the animal's thread; nothing
 * ticks it. Timestamps are player ticks.
 */
final class LivestockBrain {
    final Mob mob;
    final String species;
    /** The literals; null for a horse, which only runs the tempt goal. */
    final LivestockRoster.Profile profile;
    /** {@code isFood}: the species' food tag. */
    final Predicate<ItemStack> food;

    FleeState flee = FleeState.IDLE;
    LivingEntity threat;
    long panicUntil = Long.MIN_VALUE;
    Player tempting;
    long temptCooldownUntil = Long.MIN_VALUE;
    LivingEntity breedPartner;
    long pairAt;
    /** {@code BreedBehavior.nextAttemptTime}, starting at -1 as in TFC. */
    long nextBreedAttempt = -1L;
    LivingEntity lookTarget;
    Reading seen = Reading.NONE;
    /** The kind bits of a sighted entity ({@code SpeciesTags#kind}); set by the module. */
    java.util.function.ToIntFunction<LivingEntity> kinds = e -> 0;
    long seenAt = Long.MIN_VALUE;
    /** The animal's sex from its record, read once (a record's sex never changes; PAR-FAUNA-33). */
    private Boolean male;

    LivestockBrain(Mob mob, String species, LivestockRoster.Profile profile, Predicate<ItemStack> food) {
        this.mob = mob;
        this.species = species;
        this.profile = profile;
        this.food = food;
    }

    static boolean alive(LivingEntity e) {
        return e != null && e.isValid() && !e.isDead();
    }

    boolean fleeing(long now) {
        return flee.isActive(now) && alive(threat);
    }

    boolean panicking(long now) {
        return now < panicUntil;
    }

    /** Whether the animal's record says male (false without a record); a record is read only until one is found. */
    boolean male() {
        Boolean m = male;
        if (m == null) {
            java.util.Optional<dev.tfp.core.animal.AnimalRecord> r = LivestockSenses.record(mob);
            if (r.isEmpty()) {
                return false;
            }
            m = r.get().state().male();
            male = m;
        }
        return m;
    }

    boolean breeding() {
        return alive(breedPartner);
    }

    void setThreat(LivingEntity by, FleeState state) {
        threat = by;
        flee = state;
    }

    /**
     * TFC {@code TFCAnimal.hurt}: vanilla's {@code AnimalPanic} starts on a panic cause, and {@code PreyAi.wasHurtBy}
     * switches the threat only while already fleeing from a different type.
     *
     * @return {@code true} if the threat switched to {@code by} (the caller then warns the herd)
     */
    boolean hurt(LivingEntity by, String byType, long now, boolean panicCause, RandomGenerator random) {
        if (panicCause && !panicking(now)) {
            panicUntil = now + PreyIdle.panicTicks(random);
        }
        if (by == null || !fleeing(now)) {
            return false;
        }
        FleeState next = PreyFlee.hurt(flee, now, byType, PreyFlee.retreatTicks(random));
        if (next.equals(flee)) {
            return false;
        }
        setThreat(by, next);
        return true;
    }
}
