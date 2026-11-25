/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/predator/Predator.java @ c3b4d9536 (sleeping, home, hurt,
 * addAdditionalSaveData), ai/predator/PackPredator.java (respect, saved data), ai/predator/PredatorAi.java
 * (getHomePos, updateActivity), same commit
 */
package dev.tfp.paper.predator;

import dev.tfp.core.predator.PredatorActivity;
import dev.tfp.core.predator.PredatorIdle;
import dev.tfp.core.predator.PredatorMemory;
import dev.tfp.core.predator.PredatorRules;
import dev.tfp.core.predator.PredatorSchedule;
import dev.tfp.core.predator.PredatorSpecies;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The brain memories of one predator that its goals share: the {@link PredatorMemory} record with the entities it refers
 * to ({@code ATTACK_TARGET}, {@code HURT_BY_ENTITY}, {@code LOOK_TARGET}, {@code ALPHA}), the home, the respect and the
 * timers. Owned by the predator's thread; nothing ticks it. Timestamps are player ticks. The home, the respect and the
 * sleeping flag are saved in the entity's PDC, as TFC saves them.
 */
final class PredatorBrain {
    static final NamespacedKey HOME = new NamespacedKey("tfp", "predator_home");
    static final NamespacedKey RESPECT = new NamespacedKey("tfp", "predator_respect");
    static final NamespacedKey SLEEPING = new NamespacedKey("tfp", "predator_sleeping");
    /** Vanilla {@code LivingEntity}: the last attacker is remembered for 100 ticks ({@code HURT_BY_ENTITY}). */
    static final int HURT_BY_MEMORY_TICKS = 100;

    final Mob mob;
    final PredatorSpecies.Profile profile;
    private final PredatorBody body;

    PredatorMemory memory = PredatorMemory.EMPTY;
    LivingEntity target;
    LivingEntity attacker;
    long hurtAt = Long.MIN_VALUE;
    LivingEntity lookTarget;
    long lookUntil = Long.MIN_VALUE;
    long nextLook = Long.MIN_VALUE;
    long coolingUntil = Long.MIN_VALUE;
    long nextPoi = Long.MIN_VALUE;
    long nextAround = Long.MIN_VALUE;
    long waitUntil = Long.MIN_VALUE;
    long packScanAt = Long.MIN_VALUE;
    PredatorBrain alpha;
    boolean howled;
    PredatorSchedule.Activity lastScheduled;
    List<LivingEntity> seen = List.of();
    long seenAt = Long.MIN_VALUE;
    private int[] home;
    private int respect;

    PredatorBrain(Mob mob, PredatorSpecies.Profile profile, PredatorBody body) {
        this.mob = mob;
        this.profile = profile;
        this.body = body;
    }

    static boolean alive(LivingEntity e) {
        return e != null && e.isValid() && !e.isDead();
    }

    /** Loads the saved home, respect and sleep flag; a missing home is the current block, a missing respect is drawn. */
    void load(java.util.function.IntSupplier respectDraw) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        int[] saved = pdc.get(HOME, PersistentDataType.INTEGER_ARRAY);
        if (saved != null && saved.length == 3) {
            home = saved.clone();
        } else {
            // PredatorAi.getHomePos / Predator.finalizeSpawn: the block the predator stands on
            setHome(mob.getLocation().getBlockX(), mob.getLocation().getBlockY(), mob.getLocation().getBlockZ());
        }
        if (profile.pack()) {
            Integer r = pdc.get(RESPECT, PersistentDataType.INTEGER);
            setRespect(r != null ? r : respectDraw.getAsInt());
        }
        Byte asleep = pdc.get(SLEEPING, PersistentDataType.BYTE);
        if (asleep != null && asleep != 0) {
            memory = with(memory, memory.attacking(), true);
            body.sleepPose(mob, true);
        }
    }

    int[] home() {
        return home.clone();
    }

    void setHome(int x, int y, int z) {
        home = new int[] {x, y, z};
        mob.getPersistentDataContainer().set(HOME, PersistentDataType.INTEGER_ARRAY, home.clone());
    }

    Location homeLocation() {
        return new Location(mob.getWorld(), home[0] + 0.5, home[1], home[2] + 0.5);
    }

    double homeDistanceSq() {
        Location l = mob.getLocation();
        return PredatorIdle.homeDistanceSq(l.getBlockX(), l.getBlockY(), l.getBlockZ(), home[0], home[1], home[2]);
    }

    boolean homeWithin(double distance) {
        Location l = mob.getLocation();
        return PredatorIdle.homeWithin(home[0], home[1], home[2], l.getX(), l.getY(), l.getZ(), distance);
    }

    int respect() {
        return respect;
    }

    void setRespect(int value) {
        respect = value;
        mob.getPersistentDataContainer().set(RESPECT, PersistentDataType.INTEGER, value);
    }

    /** The alpha ({@code ALPHA} memory), or this predator. */
    PredatorBrain alphaOrSelf() {
        PredatorBrain a = alpha;
        return a != null && alive(a.mob) && Bukkit.isOwnedByCurrentRegion(a.mob) ? a : this;
    }

    boolean isAlpha() {
        return alphaOrSelf() == this;
    }

    /** {@code HURT_BY_ENTITY} at {@code now}. */
    LivingEntity attackerAt(long now) {
        return alive(attacker) && now - hurtAt <= HURT_BY_MEMORY_TICKS ? attacker : null;
    }

    PredatorSchedule.Activity scheduled() {
        return PredatorSchedule.activityAt(profile.diurnal(), body.dayTime(mob));
    }

    /** {@code updateActivity}: the active activity now. A target that no longer exists is no target. */
    PredatorActivity activity(long now) {
        if (memory.attacking() && target == null) {
            update(with(memory, false, memory.sleeping()));
        }
        return PredatorActivity.resolve(memory, now, scheduled());
    }

    /** {@code tickScheduleAndWake}: a sleeper whose schedule turns from rest to hunt wakes. */
    void tickSchedule() {
        PredatorSchedule.Activity now = scheduled();
        if (lastScheduled != null) {
            update(PredatorRules.wakeAtScheduleChange(memory, lastScheduled, now));
        }
        lastScheduled = now;
    }

    void setTarget(LivingEntity t) {
        target = t;
        update(with(memory, t != null, memory.sleeping()));
    }

    /**
     * Stores a new memory and applies what changed: the sleeping pose (saved), the dropped target and the aggressive
     * flag ({@code setAggressive(hasMemoryValue(ATTACK_TARGET))}).
     */
    void update(PredatorMemory next) {
        PredatorMemory before = memory;
        memory = next;
        if (!next.attacking()) {
            target = null;
        }
        if (before.sleeping() != next.sleeping()) {
            body.sleepPose(mob, next.sleeping());
            if (next.sleeping()) {
                mob.getPersistentDataContainer().set(SLEEPING, PersistentDataType.BYTE, (byte) 1);
            } else {
                mob.getPersistentDataContainer().remove(SLEEPING);
            }
        }
        body.aggressive(mob, next.attacking());
    }

    static PredatorMemory with(PredatorMemory m, boolean attacking, boolean sleeping) {
        return new PredatorMemory(m.pacifiedUntil(), m.huntedRecentlyUntil(), m.wakeupUntil(), m.cantReachSince(), attacking,
                sleeping);
    }

    static PredatorMemory withCantReach(PredatorMemory m, long since) {
        return new PredatorMemory(m.pacifiedUntil(), m.huntedRecentlyUntil(), m.wakeupUntil(), since, m.attacking(),
                m.sleeping());
    }
}
