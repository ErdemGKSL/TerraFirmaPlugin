/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import org.bukkit.GameMode;

/**
 * PAR-HUD-12 (PROTOCOL section 4 rule 7): which game modes have TFC's needs, and what a game-mode switch does to the lazy
 * thirst clock. Pure.
 *
 * <p>TFC (like vanilla) draws its health, food and thirst overlays only when {@code gameMode.canHurtPlayer()} holds
 * ({@code IngameOverlays.setupForSurvival}), which is survival and adventure. Its thirst only drops when the vanilla food
 * exhaustion reaches 4 ({@code PlayerInfo.tick}), and {@code Player.causeFoodExhaustion} adds nothing while the player's
 * abilities are invulnerable (creative, spectator), so no thirst, hunger or saturation is lost there. Our thirst is
 * lazy (a stored value and the tick it was stamped at), so the pause is a re-stamp: while the needs are off the clock
 * keeps being moved to now with the value kept, and on a switch back the clock starts at the switch time, so no time
 * spent in creative or spectator is caught up.
 */
public final class GameModeNeeds {
    private GameModeNeeds() {
    }

    /** True in survival and adventure (TFC {@code canHurtPlayer}); false in creative and spectator. Null reads as true. */
    public static boolean needsActive(GameMode mode) {
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /**
     * The stored data at player tick {@code now} for a player in a mode with ({@code active}) or without needs. With
     * needs it is the normal lazy decay ({@link ThirstDecay#evaluate}); without, the clock is re-stamped at {@code now}
     * with the thirst kept ({@link ThirstDecay#restart}).
     */
    public static PlayerData evaluate(PlayerData stored, boolean active, long now, float temperature) {
        return active ? ThirstDecay.evaluate(stored, now, temperature) : ThirstDecay.restart(stored, now);
    }

    /**
     * The stored data at a switch at player tick {@code now} from a mode with ({@code wasActive}) or without needs. Leaving
     * a mode with needs decays the thirst up to the switch (the time in survival counts); leaving a mode without needs
     * re-stamps the clock at the switch (the time in creative or spectator is not caught up). Either way the clock is at
     * {@code now} afterwards.
     */
    public static PlayerData atSwitch(PlayerData stored, boolean wasActive, long now, float temperature) {
        return evaluate(stored, wasActive, now, temperature);
    }
}
