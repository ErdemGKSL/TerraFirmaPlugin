/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/Nutrient.java @ c3b4d9536
 */
package dev.tfp.core.food;

import dev.tfp.core.recipe.food.FoodData;
import java.util.List;
import java.util.Locale;

/**
 * The five TFC nutrient groups, in TFC's order (the ordinal indexes every nutrient array, as in TFC).
 * The serialized name is the lower-case enum name, as TFC's data keys ({@code grain}, {@code fruit},
 * {@code vegetables}, {@code protein}, {@code dairy}).
 */
public enum Nutrient {
    GRAIN,
    FRUIT,
    VEGETABLES,
    PROTEIN,
    DAIRY;

    /** TFC {@code Nutrient.TOTAL}. */
    public static final int TOTAL = 5;
    /** TFC {@code Nutrient.VALUES}, as an immutable list. */
    public static final List<Nutrient> VALUES = List.of(values());

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public String serializedName() {
        return serializedName;
    }

    /** This nutrient's value in {@code food} (TFC {@code FoodData.nutrient}). */
    public float of(FoodData food) {
        return switch (this) {
            case GRAIN -> food.grain();
            case FRUIT -> food.fruit();
            case VEGETABLES -> food.vegetables();
            case PROTEIN -> food.protein();
            case DAIRY -> food.dairy();
        };
    }
}
