/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

/** PAR-HUD-12: the pure game-mode rule (TFC {@code canHurtPlayer}) and the thirst clock at a switch. */
class GameModeNeedsTest {
    private static final float TEMP = 15f;

    @Test
    void survivalAndAdventureHaveNeedsCreativeAndSpectatorDoNot() {
        assertThat(GameModeNeeds.needsActive(GameMode.SURVIVAL)).isTrue();
        assertThat(GameModeNeeds.needsActive(GameMode.ADVENTURE)).isTrue();
        assertThat(GameModeNeeds.needsActive(GameMode.CREATIVE)).isFalse();
        assertThat(GameModeNeeds.needsActive(GameMode.SPECTATOR)).isFalse();
    }

    @Test
    void withNeedsTheThirstDecaysAsBefore() {
        PlayerData started = ThirstDecay.evaluate(PlayerData.NEW.withThirst(50f), 0L, TEMP);
        assertThat(GameModeNeeds.evaluate(started, true, 5_000L, TEMP))
            .isEqualTo(ThirstDecay.evaluate(started, 5_000L, TEMP));
        assertThat(GameModeNeeds.evaluate(started, true, 5_000L, TEMP).thirst()).isLessThan(50f);
    }

    @Test
    void withoutNeedsTheClockIsRestampedAndTheThirstKept() {
        PlayerData started = ThirstDecay.evaluate(PlayerData.NEW.withThirst(50f), 0L, TEMP);
        PlayerData paused = GameModeNeeds.evaluate(started, false, 5_000L, TEMP);
        assertThat(paused.thirst()).isEqualTo(50f);
        assertThat(paused.thirstTick()).isEqualTo(5_000L);
        assertThat(paused.passiveExhaustion()).isEqualTo(started.passiveExhaustion());
    }

    /**
     * Survival 0..5k, creative 5k..15k, survival 15k..20k: the thirst lost is that of 10k survival ticks, the
     * same as a player who never left survival and skipped the creative stretch. Nothing of the creative time is caught up.
     */
    @Test
    void aCreativeStretchIsNotCaughtUp() {
        PlayerData d = ThirstDecay.evaluate(PlayerData.NEW.withThirst(80f), 0L, TEMP);
        d = GameModeNeeds.atSwitch(d, true, 5_000L, TEMP); // survival -> creative
        float atSwitch = d.thirst();
        assertThat(atSwitch).isLessThan(80f);
        d = GameModeNeeds.evaluate(d, false, 10_000L, TEMP); // throttled readings in creative
        assertThat(d.thirst()).isEqualTo(atSwitch);
        d = GameModeNeeds.atSwitch(d, false, 15_000L, TEMP); // creative -> survival
        assertThat(d.thirst()).isEqualTo(atSwitch);
        assertThat(d.thirstTick()).isEqualTo(15_000L);
        d = GameModeNeeds.evaluate(d, true, 20_000L, TEMP);

        PlayerData never = ThirstDecay.evaluate(PlayerData.NEW.withThirst(80f), 0L, TEMP);
        never = ThirstDecay.evaluate(never, 5_000L, TEMP);
        never = ThirstDecay.restart(never, 15_000L);
        never = ThirstDecay.evaluate(never, 20_000L, TEMP);
        assertThat(d.thirst()).isEqualTo(never.thirst());
        assertThat(d.thirst()).isLessThan(atSwitch);
    }
}
