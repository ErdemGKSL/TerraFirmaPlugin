/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodData.java @ c3b4d9536
 */
package dev.tfp.core.recipe.food;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.json.ObjectSchema;
import java.util.List;

/**
 * TFC's base food values ({@code FoodData.MAP_CODEC}), shared by food definitions, drinkables and
 * the {@code tfc:meal} modifier. Every field is optional and defaults to 0. Nutrients are in
 * TFC's {@code Nutrient} order: grain, fruit, vegetables, protein, dairy. {@code decay_modifier}
 * accepts a number or the string {@code "infinity"}.
 *
 * <p>The food system (M3) will own this record; it lives here because recipes need it first.
 */
public record FoodData(int hunger, float water, float saturation, int intoxication,
                       float grain, float fruit, float vegetables, float protein, float dairy,
                       float decayModifier) {
    public static final String INFINITY = "infinity";

    /** {@code decay_modifier}: a number, or {@code "infinity"} for never decaying. */
    public static final Codec<Float> DECAY_MODIFIER = (json, ctx) -> {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            if (INFINITY.equals(json.getAsString())) {
                return Float.POSITIVE_INFINITY;
            }
            throw ctx.error("Expected either a number or 'infinity' for decay_modifier");
        }
        return Codecs.FLOAT.decode(json, ctx);
    };

    /** The flattened fields, for schemas that embed food data (food, drinkable). */
    public static final List<ObjectSchema.Field> FIELDS = List.of(
            ObjectSchema.optional("hunger", Codecs.INT),
            ObjectSchema.optional("water", Codecs.FLOAT),
            ObjectSchema.optional("saturation", Codecs.FLOAT),
            ObjectSchema.optional("intoxication", Codecs.INT),
            ObjectSchema.optional("grain", Codecs.FLOAT),
            ObjectSchema.optional("fruit", Codecs.FLOAT),
            ObjectSchema.optional("vegetables", Codecs.FLOAT),
            ObjectSchema.optional("protein", Codecs.FLOAT),
            ObjectSchema.optional("dairy", Codecs.FLOAT),
            ObjectSchema.optional("decay_modifier", DECAY_MODIFIER));

    public static final MapCodec<FoodData> MAP_CODEC = r -> new FoodData(
            r.optional("hunger", Codecs.INT, 0),
            r.optional("water", Codecs.FLOAT, 0f),
            r.optional("saturation", Codecs.FLOAT, 0f),
            r.optional("intoxication", Codecs.INT, 0),
            r.optional("grain", Codecs.FLOAT, 0f),
            r.optional("fruit", Codecs.FLOAT, 0f),
            r.optional("vegetables", Codecs.FLOAT, 0f),
            r.optional("protein", Codecs.FLOAT, 0f),
            r.optional("dairy", Codecs.FLOAT, 0f),
            r.optional("decay_modifier", DECAY_MODIFIER, 0f));

    public static final Codec<FoodData> CODEC = MAP_CODEC.codec();
}
