/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionLevels;
import dev.tfp.core.persist.DataMap;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The nutrition health modifier of {@link PlayerNeedsHudService}: from the stored records, average without them. */
class PlayerNutritionHealthTest {
    private static final NutritionConfig CFG = NutritionConfig.DEFAULT;
    private static final float AVERAGE_MODIFIER = NutritionLevels.healthModifier(NutritionLevels.DEFAULT_AVERAGE_NUTRITION, CFG);

    /** One full-hunger meal of every group at food level 20: the average is 0.55 (0.25 eaten + 0.3 default fill). */
    private static final PlayerData FED = PlayerData.NEW.withNutrition(List.of(new PlayerData.FoodRecord(20, 0f, 0f, 0,
            new PlayerData.Nutrients(1, 1, 1, 1, 1), 0f)));

    @Test
    void noStoredDataGivesTheAverageModifier() {
        assertThat(PlayerNeedsHudService.healthModifier(Optional.empty(), 20)).isEqualTo(AVERAGE_MODIFIER);
        assertThat(AVERAGE_MODIFIER).isEqualTo(0.85f);
    }

    @Test
    void newPlayerAtFullFoodHasTheAverageModifier() {
        assertThat(PlayerNeedsHudService.healthModifier(Optional.of(PlayerData.NEW), 20)).isCloseTo(AVERAGE_MODIFIER, within6());
    }

    @Test
    void storedRecordsChangeTheModifier() {
        float fed = PlayerNeedsHudService.healthModifier(Optional.of(FED), 20);
        // average 0.55 -> 0.85 + (0.15 / 0.55) * (3.0 - 0.85) = 1.4364
        assertThat(fed).isCloseTo(1.4364f, Offset.offset(0.001f));
        assertThat(fed).isGreaterThan(AVERAGE_MODIFIER);
    }

    @Test
    void theVanillaFoodLevelIsTheHungerTheRecordsAreReadAt() {
        // Lower food level spends the window: the same records give a lower average than at full food.
        float full = PlayerNeedsHudService.healthModifier(Optional.of(FED), 20);
        float empty = PlayerNeedsHudService.healthModifier(Optional.of(FED), 0);
        assertThat(empty).isLessThan(full);
        assertThat(PlayerNeedsHudService.healthModifier(Optional.of(FED), 99)).isEqualTo(full);
    }

    @Test
    void recordsMapToTheCoreFoodData() {
        var foods = PlayerNeedsHudService.nutritionOf(FED).records();
        assertThat(foods).hasSize(1);
        assertThat(foods.getFirst().hunger()).isEqualTo(20);
        assertThat(foods.getFirst().dairy()).isEqualTo(1f);
    }

    @Test
    void migratedVersionOneDataHasNoRecordsAndTheAverageModifier() {
        // A v1 map: no thirstTick or passiveExhaustion (added by the v1 -> v2 migration), no eaten food.
        DataMap v1 = PlayerDataCodec.CODEC.encode(PlayerData.NEW);
        v1.remove("thirstTick");
        v1.remove("passiveExhaustion");
        v1.putInt("v", 1);
        PlayerData migrated = PlayerDataCodec.CODEC.decode(v1);
        assertThat(migrated.nutrition()).isEmpty();
        assertThat(PlayerNeedsHudService.healthModifier(Optional.of(migrated), 20)).isCloseTo(AVERAGE_MODIFIER, within6());
    }

    private static Offset<Float> within6() {
        return Offset.offset(1e-6f);
    }
}
