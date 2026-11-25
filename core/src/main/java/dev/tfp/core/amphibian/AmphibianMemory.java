/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java @ c3b4d9536 (customServerAiStep, hurt,
 * doHurtTarget), ai/amphibian/AmphibianAi.java (makeBrain, initPlayDeadActivity, initFightActivity, updateActivity,
 * getAttackTarget), ai/amphibian/AmphibianPlayDeadBehavior.java (update), ai/amphibian/PinnipedAI.java (makeBrain,
 * initRetreatActivity, updateActivity), aquatic/LeopardSeal.java (customServerAiStep), same commit
 */
package dev.tfp.core.amphibian;

import dev.tfp.core.aquatic.PlayDeadTimer;

/**
 * The brain state of one amphibian that its rules read and write, as an immutable value: the active activity, the play-dead
 * timer and flag, whether {@code HURT_BY_ENTITY} and {@code ATTACK_TARGET} are present and when {@code HUNTED_RECENTLY}
 * expires. The entities behind the two memories live in the Paper glue; only their presence matters here.
 *
 * <p>Each server tick runs, in TFC's order, the behaviours of the current activity (Paper glue) and then {@link #tick}: the
 * play-dead countdown, the activity choice, the playing-dead flag.
 *
 * @param activity            the active non-core activity
 * @param timer               {@code PLAY_DEAD_TICKS} and the synced playing-dead flag
 * @param hurtBy              {@code HURT_BY_ENTITY} is present
 * @param attackTarget        {@code ATTACK_TARGET} is present
 * @param huntedRecentlyUntil the first tick at which {@code HUNTED_RECENTLY} is gone ({@link Long#MIN_VALUE} if never set)
 */
public record AmphibianMemory(AmphibianActivity activity, PlayDeadTimer timer, boolean hurtBy, boolean attackTarget,
        long huntedRecentlyUntil) {

    /** {@code AmphibiousAnimal.doHurtTarget}: {@code HUNTED_RECENTLY} for 1000 ticks after a kill. */
    public static final int HUNTED_RECENTLY_TICKS = 1000;

    /** A fresh brain: {@code useDefaultActivity()}, no memories. */
    public static final AmphibianMemory FRESH = new AmphibianMemory(AmphibianActivity.IDLE, PlayDeadTimer.NONE, false, false,
            Long.MIN_VALUE);

    public boolean playingDead() {
        return timer.playingDead();
    }

    /** {@code HUNTED_RECENTLY} is present at {@code now}. */
    public boolean huntedRecently(long now) {
        return huntedRecentlyUntil != Long.MIN_VALUE && now < huntedRecentlyUntil;
    }

    /**
     * {@code AmphibiousAnimal.hurt} and the {@code HURT_BY} sensor. A hit by a living entity remembers it (both brains). For the
     * {@code AmphibianAi} brain, a hit above half a point on a mob that is alive and not already playing dead sets the countdown
     * to 200 ticks; the seal's brain drops that write.
     *
     * @param species        the species
     * @param amount         the damage amount
     * @param alive          health above zero after the hit
     * @param byLivingEntity the damage came from a living entity (the sensor's {@code HURT_BY_ENTITY})
     * @return the new state
     */
    public AmphibianMemory hurt(AmphibianSpecies species, float amount, boolean alive, boolean byLivingEntity) {
        PlayDeadTimer t = species.playsDead() ? timer.onHurt(amount, alive) : timer;
        return new AmphibianMemory(activity, t, hurtBy || byLivingEntity, attackTarget, huntedRecentlyUntil);
    }

    /** The {@code HURT_BY} sensor drops {@code HURT_BY_ENTITY} once the attacker is dead or in another level. */
    public AmphibianMemory attackerGone() {
        return hurtBy ? new AmphibianMemory(activity, timer, false, attackTarget, huntedRecentlyUntil) : this;
    }

    /** {@code StartAttacking}: a small fish became the attack target. */
    public AmphibianMemory startAttacking() {
        return new AmphibianMemory(activity, timer, hurtBy, true, huntedRecentlyUntil);
    }

    /** {@code StopAttackingIfTargetInvalid} or {@code EraseMemoryIf(isTempted)}: the attack target is dropped. */
    public AmphibianMemory stopAttacking() {
        return attackTarget ? new AmphibianMemory(activity, timer, hurtBy, false, huntedRecentlyUntil) : this;
    }

    /** {@code doHurtTarget} killed the target: {@code HUNTED_RECENTLY} for {@value #HUNTED_RECENTLY_TICKS} ticks. */
    public AmphibianMemory killed(long now) {
        return new AmphibianMemory(activity, timer, hurtBy, attackTarget, now + HUNTED_RECENTLY_TICKS);
    }

    /**
     * {@code updateActivity} and the flag of {@code customServerAiStep}, after the behaviours of this tick.
     *
     * <p>{@code AmphibianAi}: the countdown runs ({@code AmphibianPlayDeadBehavior.update}); a countdown at zero is erased with
     * {@code HURT_BY_ENTITY} and the brain goes back to {@code IDLE}. Unless the activity at the start was {@code PLAY_DEAD},
     * the first valid of {@code PLAY_DEAD} (countdown present), {@code FIGHT} (attack target), {@code IDLE} becomes active.
     * Out of water, the flag is set from the countdown. {@code PinnipedAI}: unless already in {@code AVOID}, the first valid of
     * {@code AVOID} (attacker remembered), {@code FIGHT}, {@code IDLE}; no countdown, no flag. Stopping {@code FIGHT} erases
     * the attack target, stopping {@code AVOID} the attacker.
     *
     * @param species         the species
     * @param inWaterOrBubble the mob is in water or a bubble column
     * @return the new state
     */
    public AmphibianMemory tick(AmphibianSpecies species, boolean inWaterOrBubble) {
        AmphibianActivity current = activity;
        if (species.brain() == AmphibianSpecies.Brain.PINNIPED) {
            if (current == AmphibianActivity.AVOID) {
                return this;
            }
            AmphibianActivity next = hurtBy ? AmphibianActivity.AVOID
                    : attackTarget ? AmphibianActivity.FIGHT : AmphibianActivity.IDLE;
            return switchTo(next, timer, hurtBy);
        }
        PlayDeadTimer t = timer.tick(inWaterOrBubble);
        boolean hurt = hurtBy;
        AmphibianActivity act = current;
        AmphibianMemory m = this;
        if (timer.hasTimer() && !t.hasTimer()) {
            // AmphibianPlayDeadBehavior.update: erase PLAY_DEAD_TICKS and HURT_BY_ENTITY, useDefaultActivity
            hurt = false;
            m = new AmphibianMemory(act, t, hurt, attackTarget, huntedRecentlyUntil).switchTo(AmphibianActivity.IDLE, t, hurt);
            act = AmphibianActivity.IDLE;
        } else {
            m = new AmphibianMemory(act, t, hurt, attackTarget, huntedRecentlyUntil);
        }
        if (current != AmphibianActivity.PLAY_DEAD) {
            AmphibianActivity next = m.timer.hasTimer() ? AmphibianActivity.PLAY_DEAD
                    : m.attackTarget ? AmphibianActivity.FIGHT : AmphibianActivity.IDLE;
            m = m.switchTo(next, m.timer, m.hurtBy);
        }
        return m;
    }

    /** {@code setActiveActivity}: stopping an activity erases the memories it was registered to remove. */
    private AmphibianMemory switchTo(AmphibianActivity next, PlayDeadTimer t, boolean hurt) {
        if (next == activity) {
            return new AmphibianMemory(activity, t, hurt, attackTarget, huntedRecentlyUntil);
        }
        boolean target = attackTarget && activity != AmphibianActivity.FIGHT;
        boolean by = hurt && activity != AmphibianActivity.AVOID;
        PlayDeadTimer tt = activity == AmphibianActivity.PLAY_DEAD
                ? new PlayDeadTimer(PlayDeadTimer.NO_TIMER, t.playingDead()) : t;
        return new AmphibianMemory(next, tt, by, target, huntedRecentlyUntil);
    }
}
