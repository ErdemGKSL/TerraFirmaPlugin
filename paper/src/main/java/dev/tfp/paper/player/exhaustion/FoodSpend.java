/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (addExhaustion, tick)
 */
package dev.tfp.paper.player.exhaustion;

import dev.tfp.core.food.NutritionState;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataStore;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * PAR-SURV-15: takes the exhaustion points the lazy clock has completed ({@link PlayerData#pendingFoodPoints}) off the
 * vanilla food bar in vanilla {@code FoodData.tick}'s order: saturation first (one point each, floored at 0), then one
 * food level per remaining point.
 */
public final class FoodSpend {
    private FoodSpend() {
    }

    /** The bar after {@code points}: vanilla's order, saturation first. */
    public record Bar(int food, float saturation) {
    }

    /** Pure: {@code points} spent on a bar of {@code food} and {@code saturation}. */
    public static Bar spend(int food, float saturation, long points) {
        float sat = saturation;
        long left = Math.max(points, 0);
        while (left > 0 && sat > 0) {
            sat = Math.max(sat - 1.0f, 0f);
            left--;
        }
        int f = food;
        if (left > 0) {
            f = (int) Math.max(food - Math.min(left, NutritionState.MAX_HUNGER), 0);
        }
        return new Bar(f, sat);
    }

    /**
     * Player thread: drains the pending points of {@code player} into the vanilla bar. {@code active} false (creative,
     * spectator) drops them without cost. Returns whether the bar changed.
     */
    public static boolean drain(Player player, PlayerDataStore data, boolean active) {
        UUID id = player.getUniqueId();
        var stored = data.get(id);
        if (stored.isEmpty() || stored.get().pendingFoodPoints() == 0) {
            return false;
        }
        long points = stored.get().pendingFoodPoints();
        data.update(id, d -> d.withPendingFoodPoints(d.pendingFoodPoints() - points));
        if (!active) {
            return false;
        }
        Bar bar = spend(player.getFoodLevel(), player.getSaturation(), points);
        boolean changed = bar.food() != player.getFoodLevel() || bar.saturation() != player.getSaturation();
        if (changed) {
            player.setSaturation(bar.saturation());
            player.setFoodLevel(bar.food());
        }
        return changed;
    }
}
