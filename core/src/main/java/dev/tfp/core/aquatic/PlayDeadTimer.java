/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java (PLAY_DEAD_TIME, customServerAiStep, hurt),
 * src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianPlayDeadBehavior.java (update, start),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/Penguin.java and LeopardSeal.java (isPlayingDeadEffective) @ c3b4d9536
 */
package dev.tfp.core.aquatic;

import java.util.List;

/**
 * The play-dead state of a TFC amphibious mob (turtle, penguin, leopard seal): the countdown memory ({@code PLAY_DEAD_TICKS}) and
 * the synced playing-dead flag. An amphibian plays dead for {@link #PLAY_DEAD_TIME} ticks after a hit, but only out of water.
 *
 * <p>Each server tick, in TFC's order, the countdown runs first, then the flag is set from the countdown, and only when the mob is
 * out of water. In water the flag keeps its last value. A hit arms the countdown only when the mob is alive, the hit is more than
 * half a point, and the mob is not already playing dead. The brain's activity and the walk stops are Minecraft glue.
 *
 * @param ticks       the countdown, or {@link #NO_TIMER} when the memory is absent; a present countdown is at least 0
 * @param playingDead the synced flag
 */
public record PlayDeadTimer(int ticks, boolean playingDead) {

    /** TFC {@code AmphibiousAnimal.PLAY_DEAD_TIME}: the countdown set by a hit. */
    public static final int PLAY_DEAD_TIME = 200;
    /** TFC {@code AmphibiousAnimal.hurt}: a hit arms the countdown only if its amount is strictly above this. */
    public static final float HURT_THRESHOLD = 0.5f;
    /** The countdown value when the memory is absent. */
    public static final int NO_TIMER = -1;

    /** The state of a mob that has never been hit: no countdown, not playing dead. */
    public static final PlayDeadTimer NONE = new PlayDeadTimer(NO_TIMER, false);

    /**
     * A status effect the play-dead behaviour gives when it starts.
     *
     * @param id            the effect id, such as {@code minecraft:regeneration}
     * @param durationTicks the duration in ticks
     * @param amplifier     the amplifier, where 0 is level I
     */
    public record StatusEffect(String id, int durationTicks, int amplifier) {
    }

    /**
     * The hit of the mob. TFC arms the countdown when the mob is still alive after the hit, the amount is above the threshold and
     * the flag is clear. Otherwise the state is unchanged. The countdown is set whether the mob is in water or not.
     *
     * @param amount the damage amount of the hit
     * @param alive  whether the mob's health is above zero after the hit
     * @return the new state
     */
    public PlayDeadTimer onHurt(float amount, boolean alive) {
        if (alive && amount > HURT_THRESHOLD && !playingDead) {
            return new PlayDeadTimer(PLAY_DEAD_TIME, playingDead);
        }
        return this;
    }

    /**
     * One server tick of the mob's AI. The countdown runs first: a present countdown above zero goes down by one, one at zero or
     * below is removed. Then, only out of water, the flag is set to whether the countdown is present and above zero.
     *
     * @param inWaterOrBubble whether the mob is in water or a bubble column
     * @return the new state
     */
    public PlayDeadTimer tick(boolean inWaterOrBubble) {
        int next = ticks;
        if (next != NO_TIMER) {
            next = next <= 0 ? NO_TIMER : next - 1;
        }
        boolean dead = playingDead;
        if (!inWaterOrBubble) {
            dead = next > 0;
        }
        return new PlayDeadTimer(next, dead);
    }

    /** Whether the countdown is present. */
    public boolean hasTimer() {
        return ticks != NO_TIMER;
    }

    /**
     * The effects the play-dead behaviour gives when it starts: regeneration I and damage resistance III, each for
     * {@link #PLAY_DEAD_TIME} ticks, if the species is effective while playing dead. Penguins and leopard seals are not, so they get none.
     *
     * @param playingDeadEffective the species' {@code isPlayingDeadEffective}
     * @return the effects, empty if the species is not effective
     */
    public static List<StatusEffect> startEffects(boolean playingDeadEffective) {
        if (!playingDeadEffective) {
            return List.of();
        }
        return List.of(
                new StatusEffect("minecraft:regeneration", PLAY_DEAD_TIME, 0),
                new StatusEffect("minecraft:damage_resistance", PLAY_DEAD_TIME, 2));
    }
}
