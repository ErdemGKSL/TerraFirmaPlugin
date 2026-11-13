/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerDeath: PlayerEvent.Clone),
 * src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (copyOnDeath; tick: extra-peaceful branch),
 * src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (keepNutritionAfterDeath,
 * enablePeacefulDifficultyPassiveRegeneration)
 */
package dev.tfp.paper.player;

import java.util.function.Function;

/**
 * PAR-SURV-09: TFC's death and peaceful-regeneration rules, pure.
 *
 * <p>A death gives TFC's player a fresh {@code PlayerInfo} (full thirst, sober, smooth chisel mode, no drink stamp, no
 * nutrition records); {@code PlayerEvent.Clone} then copies only the nutrition back when {@code keepNutritionAfterDeath}
 * is on. Vanilla resets hunger and saturation itself.
 *
 * <p>With {@code enablePeacefulDifficultyPassiveRegeneration} on, TFC's tick in peaceful heals 1 HP every 20 ticks
 * ({@code tickCount % 20 == 0}) and every 10 ticks adds 1 hunger when below 20 and 5 thirst when below full.
 *
 * @param keepNutritionAfterDeath                     TFC default {@code true}
 * @param enablePeacefulDifficultyPassiveRegeneration TFC default {@code false}
 */
public record RespawnRules(boolean keepNutritionAfterDeath, boolean enablePeacefulDifficultyPassiveRegeneration) {
    /** TFC's server config defaults. */
    public static final RespawnRules DEFAULT = new RespawnRules(true, false);
    /** JVM property overriding {@link #keepNutritionAfterDeath} until the config file carries it. */
    public static final String KEEP_NUTRITION_PROPERTY = "tfp.keepNutritionAfterDeath";
    /** JVM property overriding {@link #enablePeacefulDifficultyPassiveRegeneration}. */
    public static final String PEACEFUL_REGEN_PROPERTY = "tfp.enablePeacefulDifficultyPassiveRegeneration";

    /** TFC's peaceful step period ({@code tickCount % 10 == 0}). */
    public static final long PEACEFUL_PERIOD_TICKS = 10;
    /** TFC: {@code player.heal(1.0F)} on {@code tickCount % 20 == 0}, every other peaceful step. */
    public static final float PEACEFUL_HEAL = 1f;
    /** TFC: {@code addThirst(5f)} per peaceful step. */
    public static final float PEACEFUL_THIRST = 5f;
    /** Vanilla {@code FoodData.needsFood}: below 20. */
    public static final int MAX_FOOD = 20;

    /** The rules with the JVM properties applied over {@link #DEFAULT} (absent = TFC default). */
    public static RespawnRules fromProperties(Function<String, String> properties) {
        return new RespawnRules(bool(properties.apply(KEEP_NUTRITION_PROPERTY), DEFAULT.keepNutritionAfterDeath),
                bool(properties.apply(PEACEFUL_REGEN_PROPERTY), DEFAULT.enablePeacefulDifficultyPassiveRegeneration));
    }

    private static boolean bool(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    /**
     * The data of the player respawning after a death at player tick {@code now}: TFC's fresh {@code PlayerInfo} (the
     * thirst clock started at {@code now}), with the dead player's nutrition records copied when
     * {@link #keepNutritionAfterDeath} is on.
     */
    public PlayerData afterDeath(PlayerData dead, long now) {
        PlayerData fresh = PlayerData.NEW.withThirstClock(PlayerData.MAX_THIRST, now, 0L);
        return keepNutritionAfterDeath ? fresh.withNutrition(dead.nutrition()) : fresh;
    }

    /** What one peaceful step does: HP to heal, hunger to add, thirst to add. */
    public record PeacefulStep(float heal, int food, float thirst) {
        public static final PeacefulStep NONE = new PeacefulStep(0f, 0, 0f);

        public boolean isNone() {
            return heal == 0f && food == 0 && thirst == 0f;
        }
    }

    /**
     * One {@value #PEACEFUL_PERIOD_TICKS}-tick step of TFC's extra-peaceful branch.
     *
     * @param peaceful    the world's difficulty is peaceful
     * @param needsActive survival or adventure (needs are paused in creative and spectator)
     * @param healTick    this step falls on TFC's {@code tickCount % 20 == 0}
     * @param hurt        health below max
     * @param food        the vanilla food level
     * @param thirst      the evaluated thirst
     */
    public PeacefulStep peacefulStep(boolean peaceful, boolean needsActive, boolean healTick, boolean hurt, int food,
            float thirst) {
        if (!enablePeacefulDifficultyPassiveRegeneration || !peaceful || !needsActive) {
            return PeacefulStep.NONE;
        }
        float heal = healTick && hurt ? PEACEFUL_HEAL : 0f;
        int addFood = food < MAX_FOOD ? 1 : 0;
        float addThirst = thirst < PlayerData.MAX_THIRST ? PEACEFUL_THIRST : 0f;
        return new PeacefulStep(heal, addFood, addThirst);
    }
}
