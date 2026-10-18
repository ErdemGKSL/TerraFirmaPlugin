/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java @ c3b4d9536 (the 29 constants: nutrient demands, stage counts, shape),
 *       src/generated/resources/data/tfc/tfc/climate_range/crop/*.json @ c3b4d9536 (hydration and temperature windows)
 */
package dev.tfp.core.crop;

import dev.tfp.core.climate.ClimateRange;
import java.util.Locale;

/**
 * The 29 TFC crops with their nutrient demands, growth stage counts and climate windows. The constants are TFC's, read from
 * {@code Crop.java} and the crop climate ranges at c3b4d9536 (see {@code crop-golden.txt} for the cross-check). Pure data.
 *
 * <p>Stage counts follow TFC's constructors: a single-block crop with {@code s} stages has {@code maxAge = s - 1}; a two-block crop with
 * {@code b} bottom and {@code t} top stages has {@code maxSingleAge = b - 1} and {@code maxAge = b + t - 1}.
 */
public enum CropType {
    CASSAVA(-0.5f, 0.4f, 0.2f, Shape.SINGLE, 5, 5, 45, 100, 10.0f, 31.0f),
    GREEN_BEAN(-0.8f, 0.5f, 0.4f, Shape.CLIMBING, 7, 3, 25, 90, -2.0f, 25.0f),
    LENTIL(-0.8f, 0.2f, 0.2f, Shape.SINGLE, 5, 5, 15, 50, -5.0f, 25.0f),
    PEANUT(-0.9f, 0.5f, 0.5f, Shape.SINGLE, 5, 5, 20, 80, 11.0f, 31.0f),
    SOYBEAN(-0.8f, 0.6f, 0.3f, Shape.SINGLE, 6, 6, 25, 90, -7.0f, 22.0f),
    BARLEY(0.75f, -0.2f, -0.2f, Shape.SINGLE, 7, 7, 10, 70, -7.0f, 23.0f),
    OAT(1.0f, -0.4f, -0.3f, Shape.SINGLE, 7, 7, 25, 85, -7.0f, 22.0f),
    RYE(1.0f, -0.2f, -0.4f, Shape.SINGLE, 7, 7, 15, 80, -7.0f, 18.0f),
    MAIZE(0.9f, -0.25f, -0.25f, Shape.DOUBLE, 5, 2, 50, 100, -7.0f, 27.0f),
    WHEAT(1.0f, -0.3f, -0.3f, Shape.SINGLE, 7, 7, 15, 85, -7.0f, 22.0f),
    RICE(0.4f, 0.3f, 0.3f, Shape.FLOODED, 7, 7, 35, 100, 8.0f, 31.0f),
    BEET(0.4f, 0.3f, 0.5f, Shape.SINGLE, 5, 5, 10, 70, -10.0f, 27.0f),
    CABBAGE(0.5f, 0.2f, 0.4f, Shape.SINGLE, 5, 5, 10, 65, -10.0f, 27.0f),
    CARROT(0.5f, 0.3f, 0.4f, Shape.SINGLE, 4, 4, 15, 85, -10.0f, 27.0f),
    GARLIC(0.4f, 0.2f, 0.5f, Shape.SINGLE, 4, 4, 10, 70, -3.0f, 22.0f),
    ONION(0.4f, 0.4f, 0.4f, Shape.SINGLE, 6, 6, 15, 85, -5.0f, 26.0f),
    POTATO(0.4f, 0.2f, 0.6f, Shape.SINGLE, 6, 6, 35, 90, -7.0f, 22.0f),
    SQUASH(0.25f, 0.45f, 0.5f, Shape.SINGLE, 7, 7, 15, 85, -7.0f, 25.0f),
    TOMATO(0.4f, 0.5f, 0.6f, Shape.PICKABLE_CLIMBING, 7, 3, 20, 85, 2.0f, 31.0f),
    RED_BELL_PEPPER(0.3f, 0.4f, 0.5f, Shape.PICKABLE, 6, 6, 30, 95, 11.0f, 31.0f),
    YELLOW_BELL_PEPPER(0.3f, 0.4f, 0.5f, Shape.PICKABLE, 6, 6, 30, 95, 11.0f, 31.0f),
    PUMPKIN(0.4f, 0.3f, 0.6f, Shape.SPREADING, 7, 7, 20, 85, -7.0f, 27.0f),
    MELON(0.3f, 0.4f, 0.65f, Shape.SPREADING, 7, 7, 35, 100, 5.0f, 31.0f),
    CANOLA(-0.3f, -0.6f, -1.0f, Shape.SINGLE, 5, 5, 20, 75, -30.0f, 6.0f),
    RADISH(-0.5f, -1.0f, -0.6f, Shape.SINGLE, 5, 5, 30, 90, -28.0f, 10.0f),
    ALFALFA(-0.8f, -0.5f, -0.6f, Shape.SINGLE, 5, 5, 40, 100, -32.0f, 8.0f),
    JUTE(0.6f, 0.4f, -0.4f, Shape.DOUBLE, 5, 2, 15, 90, 2.0f, 25.0f),
    PAPYRUS(0.6f, -0.4f, 0.4f, Shape.DOUBLE, 5, 2, 50, 100, 11.0f, 31.0f),
    SUGARCANE(0.5f, 0.5f, 0.5f, Shape.DOUBLE, 7, 3, 25, 100, 16.0f, 31.0f);

    /** The block shape TFC gives the crop. The Paper glue picks the block class from it. */
    public enum Shape {
        /** One block, growth through {@code maxAge} ({@code DefaultCropBlock}). */
        SINGLE,
        /** One block that spreads a fruit block on the ground once grown ({@code SpreadingCropBlock}). */
        SPREADING,
        /** One block that yields fruit when picked ({@code PickableCropBlock}). */
        PICKABLE,
        /** One block that needs water in the cell ({@code FloodedCropBlock}). */
        FLOODED,
        /** Two blocks, no stick ({@code DoubleCropBlock}). */
        DOUBLE,
        /** Two blocks on a stick ({@code ClimbingCropBlock}). */
        CLIMBING,
        /** Two blocks on a stick, pickable ({@code PickableClimbingCropBlock}). */
        PICKABLE_CLIMBING
    }

    private final float nitrogen;
    private final float phosphorus;
    private final float potassium;
    private final Shape shape;
    private final CropStages stages;
    private final ClimateRange climate;

    CropType(float nitrogen, float phosphorus, float potassium, Shape shape, int maxAge, int maxSingleAge,
             int minHydration, int maxHydration, float minTemperature, float maxTemperature) {
        this.nitrogen = nitrogen;
        this.phosphorus = phosphorus;
        this.potassium = potassium;
        this.shape = shape;
        this.stages = new CropStages(maxAge, maxSingleAge);
        this.climate = new ClimateRange(minHydration, maxHydration, 0, minTemperature, maxTemperature, 0f);
    }

    /** The name TFC uses in data and in the serialized form. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public float nitrogen() {
        return nitrogen;
    }

    public float phosphorus() {
        return phosphorus;
    }

    public float potassium() {
        return potassium;
    }

    public Shape shape() {
        return shape;
    }

    public CropStages stages() {
        return stages;
    }

    public ClimateRange climate() {
        return climate;
    }

    /**
     * The growth rules' input for this crop. The growth limit is 1 under valid light (and water for the flooded crop), else 0; the
     * caller decides it from the world at the moment of the step.
     */
    public CropRules.Spec spec(float growthModifier, float expiryModifier, float growthLimit) {
        return new CropRules.Spec(climate, growthModifier, expiryModifier, growthLimit);
    }
}
