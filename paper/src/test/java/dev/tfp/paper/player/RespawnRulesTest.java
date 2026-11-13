/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-SURV-09: TFC's death copy and extra-peaceful step, pure. */
class RespawnRulesTest {
    static final PlayerData.FoodRecord BREAD = new PlayerData.FoodRecord(4, 0f, 1f, 0,
            new PlayerData.Nutrients(1.5f, 0, 0, 0, 0), 1f);
    static final PlayerData DEAD = new PlayerData(3f, 1_000L, 9_000L, "tfp:slab", List.of(BREAD), 2_000L, 7L);

    @Test
    void defaultsAreTfcServerConfigDefaults() {
        assertThat(RespawnRules.DEFAULT.keepNutritionAfterDeath()).isTrue();
        assertThat(RespawnRules.DEFAULT.enablePeacefulDifficultyPassiveRegeneration()).isFalse();
        assertThat(RespawnRules.fromProperties(k -> null)).isEqualTo(RespawnRules.DEFAULT);
        assertThat(RespawnRules.fromProperties(Map.of(RespawnRules.KEEP_NUTRITION_PROPERTY, "false",
                RespawnRules.PEACEFUL_REGEN_PROPERTY, " TRUE ")::get)).isEqualTo(new RespawnRules(false, true));
    }

    @Test
    void deathGivesAFreshPlayerInfoWithTheNutritionCopied() {
        PlayerData after = RespawnRules.DEFAULT.afterDeath(DEAD, 50_000L);
        assertThat(after.thirst()).isEqualTo(PlayerData.MAX_THIRST);
        assertThat(after.thirstTick()).isEqualTo(50_000L);
        assertThat(after.passiveExhaustion()).isZero();
        assertThat(after.lastDrinkTick()).isEqualTo(PlayerData.NEW.lastDrinkTick());
        assertThat(after.intoxicationTick()).isEqualTo(PlayerData.NEW.intoxicationTick());
        assertThat(after.chiselMode()).isEqualTo(PlayerData.DEFAULT_CHISEL_MODE);
        assertThat(after.nutrition()).containsExactly(BREAD);
    }

    @Test
    void withoutKeepNutritionTheNutritionIsDefault() {
        PlayerData after = new RespawnRules(false, false).afterDeath(DEAD, 50_000L);
        assertThat(after.nutrition()).isEmpty();
        assertThat(after).isEqualTo(PlayerData.NEW.withThirstClock(PlayerData.MAX_THIRST, 50_000L, 0L));
    }

    @Test
    void peacefulStepOnlyWhenEnabledPeacefulAndNeedsActive() {
        RespawnRules on = new RespawnRules(true, true);
        assertThat(RespawnRules.DEFAULT.peacefulStep(true, true, true, true, 5, 10f).isNone()).isTrue();
        assertThat(on.peacefulStep(false, true, true, true, 5, 10f).isNone()).as("not peaceful").isTrue();
        assertThat(on.peacefulStep(true, false, true, true, 5, 10f).isNone()).as("creative").isTrue();
        assertThat(on.peacefulStep(true, true, true, true, 5, 10f))
                .isEqualTo(new RespawnRules.PeacefulStep(1f, 1, 5f));
        assertThat(on.peacefulStep(true, true, false, true, 5, 10f))
                .as("heal only on tickCount % 20").isEqualTo(new RespawnRules.PeacefulStep(0f, 1, 5f));
        assertThat(on.peacefulStep(true, true, true, false, 20, 100f).isNone()).as("full everything").isTrue();
    }
}
