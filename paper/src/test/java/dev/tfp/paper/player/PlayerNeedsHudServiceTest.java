/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

/** The pure part of {@link PlayerNeedsHudService}: stored thirst and food level into the snapshot, the rest untouched. */
class PlayerNeedsHudServiceTest {
    private static final HudSnapshot BASE = new HudSnapshot(50, 50, 100, 20, 3.5, 12.0, PrecipitationType.RAIN);

    @Test
    void storedThirstAndFoodLevelReplaceTheOldOnes() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(BASE, withThirst(37.5f), 13);
        assertThat(out.thirst()).isEqualTo(37.5);
        assertThat(out.hunger()).isEqualTo(13);
    }

    @Test
    void otherFieldsAreKept() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(BASE, withThirst(10f), 4);
        assertThat(out.health()).isEqualTo(50);
        assertThat(out.maxHealth()).isEqualTo(50);
        assertThat(out.heldTemperature()).isEqualTo(3.5);
        assertThat(out.ambientTemperature()).isEqualTo(12.0);
        assertThat(out.precipitation()).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    void foodLevelIsClampedToVanillaRange() {
        assertThat(PlayerNeedsHudService.withNeeds(BASE, withThirst(0f), 25).hunger()).isEqualTo(20);
        assertThat(PlayerNeedsHudService.withNeeds(BASE, withThirst(0f), -1).hunger()).isEqualTo(0);
    }

    @Test
    void newPlayerMatchesTheDefaultSnapshotNeeds() {
        HudSnapshot out = PlayerNeedsHudService.withNeeds(HudSnapshot.DEFAULT, PlayerData.NEW, 20);
        assertThat(out.thirst()).isEqualTo(HudSnapshot.DEFAULT.thirst());
        assertThat(out.hunger()).isEqualTo(HudSnapshot.DEFAULT.hunger());
    }

    private static PlayerData withThirst(float thirst) {
        return PlayerData.NEW.withThirst(thirst);
    }
}
