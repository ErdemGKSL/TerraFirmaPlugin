/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java @ c3b4d9536 (command, flags, owner,
 * receiveCommand, tick), src/main/java/net/dries007/tfc/common/entities/ai/pet/TamableAi.java (memories)
 */
package dev.tfp.paper.pet;

import dev.tfp.core.pet.PetActivity;
import dev.tfp.core.pet.PetRules;
import dev.tfp.core.pet.PetSpecies;
import dev.tfp.core.pet.PetState;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * The brain memories of one pet that its goal and the module share: the saved {@link PetState} with its owner, plus the
 * transient memories (attack target, avoid target, hurt-by, look target, sleep position, timers). Owned by the pet's thread;
 * nothing ticks it. Timestamps are player ticks, the clock the livestock records use.
 */
final class PetBrain {
    /** Vanilla {@code LivingEntity}: the last attacker is remembered for 100 ticks ({@code HURT_BY_ENTITY}). */
    static final int HURT_BY_MEMORY_TICKS = 100;

    final Mob mob;
    final PetSpecies species;
    private final PetBody body;

    PetState state = PetState.fresh();
    UUID ownerId;
    PetActivity activity = PetActivity.IDLE;
    boolean needsCommandUpdate;
    long lastSlept = PetState.NO_SIT_TICK;

    LivingEntity target;
    LivingEntity attacker;
    long hurtAt = Long.MIN_VALUE;
    LivingEntity avoid;
    long avoidUntil = Long.MIN_VALUE;
    LivingEntity lookTarget;
    long lookUntil = Long.MIN_VALUE;
    long nextLook = Long.MIN_VALUE;
    long waitUntil = Long.MIN_VALUE;
    long coolingUntil = Long.MIN_VALUE;
    Location sleepPos;
    long sleepWalkUntil = Long.MIN_VALUE;

    PetBrain(Mob mob, PetSpecies species, PetBody body) {
        this.mob = mob;
        this.species = species;
        this.body = body;
    }

    static boolean alive(LivingEntity e) {
        return e != null && e.isValid() && !e.isDead();
    }

    /**
     * Loads the saved state (add-to-world). A pet that was saved sitting or asleep is posed again; the stored command's
     * activity is restored on the next think ({@code refreshCommandOnNextTick}, as TFC's {@code readAdditionalSaveData}).
     */
    void load() {
        var pdc = mob.getPersistentDataContainer();
        ownerId = PetPdc.owner(pdc);
        state = PetPdc.read(pdc);
        lastSlept = PetPdc.lastSlept(pdc);
        needsCommandUpdate = true;
        if (state.sitting() || state.sleeping()) {
            body.pose(mob, state.sitting(), state.sleeping());
        }
    }

    void save() {
        var pdc = mob.getPersistentDataContainer();
        PetPdc.setOwner(pdc, ownerId);
        PetPdc.write(pdc, state);
        PetPdc.setLastSlept(pdc, lastSlept);
    }

    boolean tamed() {
        return ownerId != null;
    }

    /** The owner when online (TFC {@code getOwner}: {@code level.getPlayerByUUID}), else null. */
    Player owner() {
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    /** The owner when online and in this pet's region and world, else null: the only owner a goal may touch. */
    Player ownerHere() {
        Player p = owner();
        return p != null && p.isValid() && p.getWorld().equals(mob.getWorld()) && Bukkit.isOwnedByCurrentRegion(p) ? p : null;
    }

    boolean isOwnedBy(org.bukkit.entity.Entity e) {
        return e != null && ownerId != null && e.getUniqueId().equals(ownerId);
    }

    /** Stores a new state, mirrors the pose and saves. Sitting and sleeping set the carrier and the model. */
    void setState(PetState next) {
        boolean poseChanged = next.sitting() != state.sitting() || next.sleeping() != state.sleeping();
        state = next;
        if (poseChanged) {
            body.pose(mob, next.sitting(), next.sleeping());
        }
        save();
    }

    void setActivity(PetActivity next) {
        if (next == null || next == activity) {
            return;
        }
        // addActivityAndRemoveMemoryWhenStopped: leaving AVOID forgets the avoid target, leaving FIGHT the attack target
        if (activity == PetActivity.AVOID) {
            avoid = null;
            avoidUntil = Long.MIN_VALUE;
        }
        if (activity == PetActivity.FIGHT) {
            target = null;
        }
        activity = next;
        body.stop(mob);
        sleepPos = null;
        waitUntil = Long.MIN_VALUE;
    }

    boolean hasAvoid(long now) {
        return alive(avoid) && now < avoidUntil && avoid.getWorld().equals(mob.getWorld());
    }

    boolean hurtRecently(long now) {
        return alive(attacker) && now - hurtAt < HURT_BY_MEMORY_TICKS && now >= hurtAt;
    }

    /** Applies the outcome of {@link PetRules#handle}: the new state and the activity it selects. */
    void apply(PetRules.Outcome outcome, long now) {
        if (outcome.feedback() != PetRules.Feedback.NONE) {
            return;
        }
        PetState before = state;
        setState(outcome.state());
        if (outcome.state().sitting() && !before.sitting()) {
            // SIT: erase WALK_TARGET and CANT_REACH_WALK_TARGET_SINCE
            body.stop(mob);
        }
        if (outcome.activity() != null) {
            setActivity(outcome.activity());
        }
    }
}
