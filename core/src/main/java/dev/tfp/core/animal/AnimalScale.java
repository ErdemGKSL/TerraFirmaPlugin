/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536
 * (AGE_SCALES, setGeneticSize, getAgeScale) and src/main/java/net/dries007/tfc/client/render/entity/AnimalRenderer.java
 * (scale), same commit
 */
package dev.tfp.core.animal;

import dev.tfp.core.livestock.LivestockAge;

/**
 * TFC's size of a livestock animal from its genetic size (1..32) and age, in float32 like TFC.
 *
 * <p>TFC draws an animal at {@code SCALE attribute x vanilla age scale x render scale}: the SCALE attribute base is
 * {@link #ageScale} (set in {@code setGeneticSize}), vanilla halves a baby ({@code TFCAnimal.isBaby} is
 * {@code age == CHILD}), and {@code AnimalRenderer.scale} multiplies by {@link #renderScale}. {@link #modelScale} is
 * that product. We set the carrier's SCALE attribute to {@link #ageScale} as TFC does, and BetterModel's entity scaler
 * reads the attribute, so the model gets the remaining factor, {@link #modelMultiplier}.
 *
 * <p>Size {@link #NO_SIZE} marks an animal without a genetic size (TFC's wild animals): every scale is 1.
 */
public final class AnimalScale {
    /** TFC {@code geneticSize} bounds ({@code Mth.clamp(size, 1, 32)}). */
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 32;
    /** Not a TFC-sized animal: scale 1. */
    public static final int NO_SIZE = 0;
    /** Vanilla's baby scale, applied by TFC through {@code isBaby()}. */
    public static final float CHILD_SCALE = 0.5f;

    /** TFC {@code AGE_SCALES[i] = Mth.map(i + 1, 1, 32, 0.8f, 1.2f)}. */
    private static final float[] AGE_SCALES = new float[MAX_SIZE];

    static {
        for (int i = 0; i < AGE_SCALES.length; i++) {
            AGE_SCALES[i] = map(i + 1, 1, 32, 0.8f, 1.2f);
        }
    }

    private AnimalScale() {
    }

    /** TFC {@code Mth.clamp(size, 1, 32)}. */
    public static int clampSize(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    /** TFC {@code getAgeScale()}: the SCALE attribute base, {@code AGE_SCALES[size - 1]}. */
    public static float ageScale(int size) {
        return size == NO_SIZE ? 1f : AGE_SCALES[clampSize(size) - 1];
    }

    /** TFC {@code AnimalRenderer.scale}: {@code Mth.clampedMap(size, 1, 32, 0.9, 1.1)}. */
    public static float renderScale(int size) {
        return size == NO_SIZE ? 1f : clampedMap(size, 1f, 32f, 0.9f, 1.1f);
    }

    /** The drawn size TFC gives the animal: {@code ageScale x renderScale x (CHILD ? 0.5 : 1)}. */
    public static float modelScale(int size, LivestockAge age) {
        return ageScale(size) * renderScale(size) * (age == LivestockAge.CHILD ? CHILD_SCALE : 1f);
    }

    /**
     * The factor the model scaler multiplies the carrier's SCALE attribute ({@link #ageScale}) by:
     * {@code renderScale x (CHILD ? 0.5 : 1)}. The vanilla baby flag does not shrink a BetterModel entity scaler
     * (M0-BM-01), so the child factor is here.
     */
    public static float modelMultiplier(int size, LivestockAge age) {
        return renderScale(size) * (age == LivestockAge.CHILD ? CHILD_SCALE : 1f);
    }

    // Minecraft Mth float overloads, same operation order.
    private static float inverseLerp(float value, float start, float end) {
        return (value - start) / (end - start);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float map(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    private static float clampedMap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        float delta = inverseLerp(value, fromMin, fromMax);
        return delta < 0f ? toMin : delta > 1f ? toMax : lerp(delta, toMin, toMax);
    }
}
