/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536
 */
package dev.tfp.paper.player;

import java.util.List;
import java.util.Objects;

/**
 * Our per-player state (ARCHITECTURE.md §7 "Player state"): the fields TFC's {@code PlayerInfo} saves under
 * {@code tfc:food} (thirst, last drink, intoxication, chisel mode, nutrition records). Vanilla hunger and
 * saturation stay in the vanilla player file. Immutable: change it with the {@code with*} methods and
 * {@link PlayerDataStore#update}. Features add fields here together with a codec version bump and a
 * migration ({@link PlayerDataCodec}).
 *
 * <p>Only base values and timestamps are stored (ARCHITECTURE.md §4.1); TFC's derived values (average
 * nutrition, per-nutrient totals) are recomputed from {@link #nutrition} by the food system (M3).
 *
 * @param thirst           0..{@link #MAX_THIRST}; TFC starts at full
 * @param lastDrinkTick    player tick of the last drink; {@link Long#MIN_VALUE} = never
 * @param intoxicationTick player tick the player is intoxicated until; {@link Long#MIN_VALUE} = sober
 * @param chiselMode       chisel mode id ({@code tfp:smooth|slab|stair}; TFC's registry ids renamed)
 * @param nutrition        TFC's nutrition records, newest first (TFC {@code NutritionData.records})
 * @param thirstTick       player tick the thirst was last evaluated at (M3-SURV-03); {@link Long#MIN_VALUE} = not yet
 * @param passiveExhaustion the passive exhaustion units left over after the last evaluation
 *                         ({@code HungerState.exhaustion}); the points it completes drive the thirst loss, so it is
 *                         kept between the 20-tick refreshes or the points would be lost
 */
public record PlayerData(float thirst, long lastDrinkTick, long intoxicationTick, String chiselMode,
        List<FoodRecord> nutrition, long thirstTick, long passiveExhaustion) {
    /** TFC {@code PlayerInfo.MAX_THIRST}. */
    public static final float MAX_THIRST = 100f;
    /** TFC's default chisel mode ({@code ChiselMode.SMOOTH}). */
    public static final String DEFAULT_CHISEL_MODE = "tfp:smooth";

    /** A player who never joined before: TFC's field initialisers. */
    public static final PlayerData NEW = new PlayerData(MAX_THIRST, Long.MIN_VALUE, Long.MIN_VALUE,
            DEFAULT_CHISEL_MODE, List.of(), Long.MIN_VALUE, 0L);

    public PlayerData {
        Objects.requireNonNull(chiselMode, "chiselMode");
        nutrition = List.copyOf(nutrition);
        if (Float.isNaN(thirst)) {
            throw new IllegalArgumentException("thirst is NaN");
        }
    }

    public PlayerData withThirst(float value) {
        return new PlayerData(Math.clamp(value, 0f, MAX_THIRST), lastDrinkTick, intoxicationTick, chiselMode, nutrition,
                thirstTick, passiveExhaustion);
    }

    public PlayerData withLastDrinkTick(long tick) {
        return new PlayerData(thirst, tick, intoxicationTick, chiselMode, nutrition, thirstTick, passiveExhaustion);
    }

    public PlayerData withIntoxicationTick(long tick) {
        return new PlayerData(thirst, lastDrinkTick, tick, chiselMode, nutrition, thirstTick, passiveExhaustion);
    }

    public PlayerData withChiselMode(String mode) {
        return new PlayerData(thirst, lastDrinkTick, intoxicationTick, mode, nutrition, thirstTick, passiveExhaustion);
    }

    public PlayerData withNutrition(List<FoodRecord> records) {
        return new PlayerData(thirst, lastDrinkTick, intoxicationTick, chiselMode, records, thirstTick, passiveExhaustion);
    }

    /** The thirst clock: the thirst level, the tick it is valid at and the passive exhaustion left over. */
    public PlayerData withThirstClock(float thirstValue, long tick, long exhaustionUnits) {
        return new PlayerData(Math.clamp(thirstValue, 0f, MAX_THIRST), lastDrinkTick, intoxicationTick, chiselMode,
                nutrition, tick, exhaustionUnits);
    }

    /**
     * One eaten food in TFC's nutrition window (TFC {@code FoodData}: hunger, water, saturation, intoxication,
     * the five nutrients, decay modifier).
     */
    public record FoodRecord(int hunger, float water, float saturation, int intoxication, Nutrients nutrients,
            float decayModifier) {
        public FoodRecord {
            Objects.requireNonNull(nutrients, "nutrients");
        }
    }

    /** TFC {@code Nutrient}: grain, fruit, vegetables, protein, dairy. */
    public record Nutrients(float grain, float fruit, float vegetables, float protein, float dairy) {
        public static final Nutrients NONE = new Nutrients(0, 0, 0, 0, 0);
    }
}
