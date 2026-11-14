/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SaladContainer.java @ c3b4d9536 (the salad's dynamic FoodData,
 *       INGREDIENTS and BOWL components),
 *       src/main/java/net/dries007/tfc/common/component/Bowl.java @ c3b4d9536 (one bowl, count 1)
 */
package dev.tfp.paper.salad;

import dev.tfp.core.recipe.food.FoodData;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of what TFC stores on a salad besides its creation date ({@code tfp:food}, {@code FoodPdc}):
 * <pre>
 * tfp:dynamic_food = { tfp:v: int, tfp:hunger: int, tfp:water: float, tfp:saturation: float, tfp:intoxication: int,
 *                      tfp:grain/fruit/vegetables/protein/dairy: float, tfp:decay: float }   (TFC FOOD's FoodData)
 * tfp:ingredients  = [string item ids, in slot order]                                       (TFC INGREDIENTS)
 * tfp:bowl         = string item id of the bowl the salad was made in                       (TFC BOWL)
 * </pre>
 * The format is meant for every dynamic food (cooking pot soups use the same keys). Absent, malformed or newer-version
 * entries read as empty. Threads: the thread that owns the stack.
 */
public final class SaladPdc {
    public static final int VERSION = 1;

    static final NamespacedKey FOOD = new NamespacedKey("tfp", "dynamic_food");
    static final NamespacedKey INGREDIENTS = new NamespacedKey("tfp", "ingredients");
    static final NamespacedKey BOWL = new NamespacedKey("tfp", "bowl");

    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey HUNGER = new NamespacedKey("tfp", "hunger");
    private static final NamespacedKey WATER = new NamespacedKey("tfp", "water");
    private static final NamespacedKey SATURATION = new NamespacedKey("tfp", "saturation");
    private static final NamespacedKey INTOXICATION = new NamespacedKey("tfp", "intoxication");
    private static final NamespacedKey GRAIN = new NamespacedKey("tfp", "grain");
    private static final NamespacedKey FRUIT = new NamespacedKey("tfp", "fruit");
    private static final NamespacedKey VEGETABLES = new NamespacedKey("tfp", "vegetables");
    private static final NamespacedKey PROTEIN = new NamespacedKey("tfp", "protein");
    private static final NamespacedKey DAIRY = new NamespacedKey("tfp", "dairy");
    private static final NamespacedKey DECAY = new NamespacedKey("tfp", "decay");

    private SaladPdc() {
    }

    /** Writes the salad's food, ingredients and bowl; false when the stack has no item meta. */
    public static boolean write(ItemStack stack, FoodData food, List<String> ingredients, String bowl) {
        Objects.requireNonNull(food, "food");
        Objects.requireNonNull(ingredients, "ingredients");
        Objects.requireNonNull(bowl, "bowl");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer f = pdc.getAdapterContext().newPersistentDataContainer();
        f.set(V, PersistentDataType.INTEGER, VERSION);
        f.set(HUNGER, PersistentDataType.INTEGER, food.hunger());
        f.set(WATER, PersistentDataType.FLOAT, food.water());
        f.set(SATURATION, PersistentDataType.FLOAT, food.saturation());
        f.set(INTOXICATION, PersistentDataType.INTEGER, food.intoxication());
        f.set(GRAIN, PersistentDataType.FLOAT, food.grain());
        f.set(FRUIT, PersistentDataType.FLOAT, food.fruit());
        f.set(VEGETABLES, PersistentDataType.FLOAT, food.vegetables());
        f.set(PROTEIN, PersistentDataType.FLOAT, food.protein());
        f.set(DAIRY, PersistentDataType.FLOAT, food.dairy());
        f.set(DECAY, PersistentDataType.FLOAT, food.decayModifier());
        pdc.set(FOOD, PersistentDataType.TAG_CONTAINER, f);
        pdc.set(INGREDIENTS, PersistentDataType.LIST.strings(), List.copyOf(ingredients));
        pdc.set(BOWL, PersistentDataType.STRING, bowl);
        stack.setItemMeta(meta);
        return true;
    }

    /** The stack's dynamic food data (TFC {@code FoodComponent.getData()} of a dynamic food). */
    public static Optional<FoodData> food(ItemStack stack) {
        PersistentDataContainer pdc = pdc(stack);
        PersistentDataContainer f = pdc == null ? null : pdc.get(FOOD, PersistentDataType.TAG_CONTAINER);
        if (f == null) {
            return Optional.empty();
        }
        Integer v = f.get(V, PersistentDataType.INTEGER);
        if (v == null || v > VERSION) {
            return Optional.empty();
        }
        return Optional.of(new FoodData(
            f.getOrDefault(HUNGER, PersistentDataType.INTEGER, 0),
            f.getOrDefault(WATER, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(SATURATION, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(INTOXICATION, PersistentDataType.INTEGER, 0),
            f.getOrDefault(GRAIN, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(FRUIT, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(VEGETABLES, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(PROTEIN, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(DAIRY, PersistentDataType.FLOAT, 0f),
            f.getOrDefault(DECAY, PersistentDataType.FLOAT, 0f)));
    }

    /** The ingredient ids (TFC {@code INGREDIENTS}); empty when none are stored. */
    public static List<String> ingredients(ItemStack stack) {
        PersistentDataContainer pdc = pdc(stack);
        return pdc == null ? List.of() : List.copyOf(pdc.getOrDefault(INGREDIENTS, PersistentDataType.LIST.strings(), List.of()));
    }

    /** The id of the stored bowl (TFC {@code BOWL}), or empty. */
    public static Optional<String> bowl(ItemStack stack) {
        PersistentDataContainer pdc = pdc(stack);
        return pdc == null ? Optional.empty() : Optional.ofNullable(pdc.get(BOWL, PersistentDataType.STRING));
    }

    private static PersistentDataContainer pdc(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer();
    }
}
