/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/TFCComponents.java @ c3b4d9536
 * (onModifyDefaultComponentsAfterResourceReload: the food component follows the food definition),
 * src/main/java/net/dries007/tfc/common/component/food/FoodData.java @ c3b4d9536 (MILK),
 * src/main/java/net/dries007/tfc/mixin/MilkBucketItemMixin.java @ c3b4d9536 (drinking milk eats FoodData.MILK)
 */
package dev.tfp.core.food;

import dev.tfp.core.recipe.food.FoodData;
import java.util.Objects;
import java.util.Optional;

/**
 * What eating (consuming) an item does under TFC's rules (PAR-SURV-03). Pure and thread-safe.
 *
 * <p>TFC gives an item the vanilla {@code minecraft:food} component exactly when it has an edible food definition, and
 * removes it from every other item (vanilla bread, beef, apples, golden apples, stews, honey bottles...): those can no
 * longer be eaten at all. Vanilla clients keep the vanilla components, so the plugin decides at the consume instead:
 *
 * <ul>
 *   <li>{@link Action#EAT}: a custom item with an edible definition. Vanilla consumes it; TFC's values are applied.</li>
 *   <li>{@link Action#EAT_INSTEAD}: a vanilla item with an edible TFC definition (TFC data: {@code minecraft:pumpkin_pie},
 *       {@code minecraft:rotten_flesh}) and the milk bucket (TFC drinks it as {@link #MILK}, without clearing effects,
 *       {@code enableVanillaDrinkingMilkClearsPotionEffects = false}). Vanilla's own consume (its food, its consume
 *       effects such as rotten flesh's Hunger, milk's effect clearing) is cancelled and TFC's eat is applied
 *       instead; {@link #remainder} is what the emptied stack becomes.</li>
 *   <li>{@link Action#BLOCK}: a vanilla food without an edible TFC definition, or any item whose definition says
 *       {@code "edible": false}: not edible in TFC, so the consume is cancelled.</li>
 *   <li>{@link Action#IGNORE}: everything else (potions, custom items without a definition): not TFC's eating.</li>
 * </ul>
 *
 * @param itemId    the item id the decision is about ({@code tfp:food/mutton}, {@code minecraft:bread})
 * @param food      the food data eaten for {@code EAT} and {@code EAT_INSTEAD}, empty otherwise
 * @param remainder for {@code EAT_INSTEAD}: the vanilla item the stack becomes when it is used up, or empty
 */
public record EatDecision(Action action, String itemId, Optional<FoodData> food, Optional<String> remainder) {
    /** The kind of consume. */
    public enum Action { IGNORE, BLOCK, EAT, EAT_INSTEAD }

    /** The vanilla milk bucket. */
    public static final String MILK_BUCKET = "minecraft:milk_bucket";
    /** What an emptied milk bucket becomes (TFC {@code MilkBucketItemMixin}: {@code new ItemStack(Items.BUCKET)}). */
    public static final String BUCKET = "minecraft:bucket";
    /** TFC {@code FoodData.MILK = of(0, 0).dairy(2f)}: no hunger, saturation, water or decay, 2 dairy. */
    public static final FoodData MILK = new FoodData(0, 0f, 0f, 0, 0f, 0f, 0f, 0f, 2f, 0f);

    public EatDecision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(food, "food");
        Objects.requireNonNull(remainder, "remainder");
    }

    /**
     * The decision for consuming one item.
     *
     * @param customId    the item's custom id, or empty for a vanilla stack
     * @param vanillaId   the stack's vanilla item id ({@code minecraft:bread}); for a custom item its base item
     * @param vanillaFood whether the vanilla item is food (has vanilla's {@code minecraft:food} component)
     */
    public static EatDecision decide(Optional<String> customId, String vanillaId, boolean vanillaFood,
            FoodDefinitions definitions) {
        Objects.requireNonNull(vanillaId, "vanillaId");
        if (customId.isPresent()) {
            String id = customId.get();
            Optional<FoodData> food = definitions.get(id);
            if (food.isEmpty()) {
                return of(Action.IGNORE, id);
            }
            return definitions.isEdible(id)
                    ? new EatDecision(Action.EAT, id, food, Optional.empty())
                    : of(Action.BLOCK, id);
        }
        if (MILK_BUCKET.equals(vanillaId)) {
            return new EatDecision(Action.EAT_INSTEAD, vanillaId, Optional.of(MILK), Optional.of(BUCKET));
        }
        Optional<FoodData> food = definitions.get(vanillaId);
        if (food.isPresent()) {
            return definitions.isEdible(vanillaId)
                    ? new EatDecision(Action.EAT_INSTEAD, vanillaId, food, Optional.empty())
                    : of(Action.BLOCK, vanillaId);
        }
        return of(vanillaFood ? Action.BLOCK : Action.IGNORE, vanillaId);
    }

    private static EatDecision of(Action action, String id) {
        return new EatDecision(action, id, Optional.empty(), Optional.empty());
    }
}
