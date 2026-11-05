/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * A player ate a TFC food (PAR-SURV-03). Fired on the player's thread after the meal was recorded, for listeners that
 * react to eating without editing the eating code; PAR-DATA-03's advancement criteria listen to it:
 *
 * <ul>
 *   <li>{@link #rotten()} is TFC's {@code TFCAdvancements.EAT_ROTTEN_FOOD.trigger} ({@code PlayerInfo.java} line 190);</li>
 *   <li>{@link #averageNutrition()} {@code >= 0.999} is TFC's {@code FULL_NUTRITION} check (lines 219-222), NaN for a
 *       rotten food (TFC makes that check only after a non-rotten meal).</li>
 * </ul>
 *
 * <p>Not cancellable: the meal has already happened.
 */
public final class NutritionFoodEatenEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String itemId;
    private final boolean rotten;
    private final float averageNutrition;

    public NutritionFoodEatenEvent(Player player, String itemId, boolean rotten, float averageNutrition) {
        super(player);
        this.itemId = itemId;
        this.rotten = rotten;
        this.averageNutrition = averageNutrition;
    }

    /** The eaten item's id ({@code tfp:food/mutton}, {@code minecraft:pumpkin_pie}, {@code minecraft:milk_bucket}). */
    public String itemId() {
        return itemId;
    }

    /** Whether the food was rotten (no nutrition, rotten-food effects instead). */
    public boolean rotten() {
        return rotten;
    }

    /** TFC's average nutrition after the meal, at the food level after it; NaN for a rotten food. */
    public float averageNutrition() {
        return averageNutrition;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
