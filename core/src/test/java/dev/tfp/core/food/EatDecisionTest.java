/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.food.EatDecision.Action;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-SURV-03: which consumes are TFC eating, which are replaced and which are refused. */
class EatDecisionTest {
    private static final FoodData MUTTON = new FoodData(4, 0f, 0f, 0, 0f, 0f, 0f, 1.5f, 0f, 3f);
    private static final FoodData PIE = new FoodData(4, 5f, 2f, 0, 1f, 0f, 2f, 1f, 0.3f, 2.5f);
    private static final FoodData FLESH = new FoodData(0, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f, Float.POSITIVE_INFINITY);
    private static final FoodData MELON = new FoodData(0, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f, 1f);
    private static final FoodDefinitions DEFS = new FoodDefinitions(Map.of(
            "tfp:food/mutton", MUTTON, "minecraft:pumpkin_pie", PIE, "minecraft:rotten_flesh", FLESH,
            "tfp:melon", MELON, "minecraft:egg", MELON), Set.of("tfp:melon", "minecraft:egg"));

    @Test
    void aCustomFoodWithAnEdibleDefinitionIsEatenByVanillaWithTfcValues() {
        EatDecision d = EatDecision.decide(Optional.of("tfp:food/mutton"), "minecraft:mutton", true, DEFS);
        assertThat(d.action()).isEqualTo(Action.EAT);
        assertThat(d.itemId()).isEqualTo("tfp:food/mutton");
        assertThat(d.food()).contains(MUTTON);
        assertThat(d.remainder()).isEmpty();
    }

    @Test
    void aCustomItemWithoutADefinitionIsLeftAloneEvenOnAVanillaFoodBase() {
        assertThat(EatDecision.decide(Optional.of("tfp:jug"), "minecraft:bread", true, DEFS).action())
                .isEqualTo(Action.IGNORE);
    }

    /** TFC {@code FoodDefinition.edible = false} (melon, pumpkin, egg, preserves): no food component, not eaten. */
    @Test
    void anInedibleDefinitionIsRefused() {
        assertThat(EatDecision.decide(Optional.of("tfp:melon"), "minecraft:paper", false, DEFS).action())
                .isEqualTo(Action.BLOCK);
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:egg", false, DEFS).action())
                .isEqualTo(Action.BLOCK);
    }

    /** TFC removes {@code minecraft:food} from every item without an edible definition: vanilla bread is not food. */
    @Test
    void aVanillaFoodWithoutADefinitionIsRefused() {
        for (String id : List.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:golden_apple",
                "minecraft:mushroom_stew", "minecraft:suspicious_stew", "minecraft:chorus_fruit")) {
            EatDecision d = EatDecision.decide(Optional.empty(), id, true, DEFS);
            assertThat(d.action()).as(id).isEqualTo(Action.BLOCK);
            assertThat(d.food()).isEmpty();
        }
    }

    /**
     * Vanilla 1.21.1 {@code HoneyBottleItem} overrides {@code use()}, so TFC's food strip does not stop it: it is
     * drunk (Poison removed, glass bottle left) without any eating.
     */
    @Test
    void theHoneyBottleIsDrunkWithoutEating() {
        EatDecision honey = EatDecision.decide(Optional.empty(), "minecraft:honey_bottle", true, DEFS);
        assertThat(honey.action()).isEqualTo(Action.USE_INSTEAD);
        assertThat(honey.food()).isEmpty();
        assertThat(honey.remainder()).contains("minecraft:glass_bottle");
        assertThat(honey.alwaysRotten()).isFalse();
    }

    /** TFC data {@code food/rotten_flesh.json}: {@code decay_modifier} infinity, so it is rotten even unstamped. */
    @Test
    void aFoodWithAnInfiniteDecayModifierIsAlwaysRotten() {
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:rotten_flesh", true, DEFS).alwaysRotten()).isTrue();
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:pumpkin_pie", true, DEFS).alwaysRotten()).isFalse();
        assertThat(EatDecision.decide(Optional.of("tfp:food/mutton"), "minecraft:mutton", true, DEFS).alwaysRotten())
                .isFalse();
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:milk_bucket", false, DEFS).alwaysRotten())
                .as("milk's decay modifier 0 never decays").isFalse();
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:bread", true, DEFS).alwaysRotten()).isFalse();
    }

    @Test
    void aVanillaConsumableThatIsNotFoodIsLeftAlone() {
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:potion", false, DEFS).action())
                .isEqualTo(Action.IGNORE);
    }

    /** TFC data {@code food/pumpkin_pie.json} and {@code food/rotten_flesh.json} name vanilla items. */
    @Test
    void aVanillaItemWithATfcDefinitionIsEatenInsteadOfVanilla() {
        EatDecision pie = EatDecision.decide(Optional.empty(), "minecraft:pumpkin_pie", true, DEFS);
        assertThat(pie.action()).isEqualTo(Action.EAT_INSTEAD);
        assertThat(pie.food()).contains(PIE);
        assertThat(pie.remainder()).isEmpty();
        EatDecision flesh = EatDecision.decide(Optional.empty(), "minecraft:rotten_flesh", true, DEFS);
        assertThat(flesh.action()).isEqualTo(Action.EAT_INSTEAD);
        assertThat(flesh.food()).contains(FLESH);
    }

    /** TFC {@code MilkBucketItemMixin}: milk eats {@code FoodData.MILK} (2 dairy) and leaves an empty bucket. */
    @Test
    void milkIsTfcMilkWithABucketLeft() {
        EatDecision milk = EatDecision.decide(Optional.empty(), "minecraft:milk_bucket", false, FoodDefinitions.EMPTY);
        assertThat(milk.action()).isEqualTo(Action.EAT_INSTEAD);
        assertThat(milk.food()).contains(EatDecision.MILK);
        assertThat(milk.remainder()).contains("minecraft:bucket");
        FoodData m = EatDecision.MILK;
        assertThat(m.hunger()).isZero();
        assertThat(m.saturation()).isZero();
        assertThat(m.water()).isZero();
        assertThat(m.dairy()).isEqualTo(2f);
        assertThat(m.grain() + m.fruit() + m.vegetables() + m.protein()).isZero();
    }

    @Test
    void generatedContentMarksTfcInedibleFoods() throws java.io.IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        FoodDefinitions defs = FoodDefinitions.fromRegistry(snapshot);
        // TFC data/tfc/tfc/food/*.json @ c3b4d9536: egg, melon and pumpkin say "edible": false
        assertThat(defs.isEdible("minecraft:egg")).isFalse();
        assertThat(defs.isEdible("tfp:melon")).isFalse();
        assertThat(defs.isEdible("tfp:pumpkin")).isFalse();
        assertThat(defs.isEdible("tfp:food/mutton")).isTrue();
        assertThat(defs.isEdible("minecraft:pumpkin_pie")).isTrue();
        assertThat(defs.isEdible("minecraft:bread")).isFalse();
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:pumpkin_pie", true, defs).action())
                .isEqualTo(Action.EAT_INSTEAD);
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:bread", true, defs).action())
                .isEqualTo(Action.BLOCK);
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:rotten_flesh", true, defs).alwaysRotten())
                .as("TFC rotten_flesh.json: decay_modifier infinity").isTrue();
        assertThat(EatDecision.decide(Optional.empty(), "minecraft:honey_bottle", true, defs).action())
                .isEqualTo(Action.USE_INSTEAD);
    }
}
