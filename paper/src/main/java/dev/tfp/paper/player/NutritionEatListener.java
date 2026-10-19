/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Eating writes the player's nutrition records (M3-SURV-02). A consumed custom food whose id has a food definition
 * is recorded with the core rule ({@link NutritionEating}); anything else (vanilla food, unknown custom items) changes
 * nothing. A rotten known food records nothing and gives TFC's rotten-food effects (Hunger II, sometimes Poison I).
 *
 * <p>Runs on the player's thread (the consume event is fired there). Vanilla's food level is read before the food is
 * applied: the event fires before vanilla changes it, so the hunger is the one TFC records at.
 */
public final class NutritionEatListener implements Listener {
    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final CustomItems items;
    private final Supplier<FoodDefinitions> definitions;
    private final ItemFoodHooks hooks;

    /**
     * @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread
     * @param definitions the food definitions of the current registry snapshot
     * @param hooks       the food queries (rotten check) at the current tick
     */
    public NutritionEatListener(PlayerDataStore data, LongSupplier playerTicks, CustomItems items,
            Supplier<FoodDefinitions> definitions, ItemFoodHooks hooks) {
        this.data = data;
        this.playerTicks = playerTicks;
        this.items = items;
        this.definitions = definitions;
        this.hooks = hooks;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return;
        }
        ItemStack stack = event.getItem();
        Optional<String> itemId = items.idOf(stack);
        if (itemId.isEmpty()) {
            return; // vanilla food: not part of TFC's nutrition yet
        }
        Optional<FoodData> food = definitions.get().get(itemId.get());
        if (food.isEmpty()) {
            return; // a custom item without a food definition
        }
        boolean rotten = hooks.isRotten(stack);
        long now = playerTicks.getAsLong();
        int vanillaFood = player.getFoodLevel();
        float vanillaSaturation = player.getSaturation();
        data.update(id, d -> NutritionEating.eat(d, food, rotten, vanillaFood, vanillaSaturation, now));
        if (rotten) {
            // TFC PlayerInfo#eat(IFood): the rotten branch gives effects instead of nutrition
            for (NutritionEating.RottenEffect effect
                    : NutritionEating.rottenEffects(ThreadLocalRandom.current()::nextDouble)) {
                player.addPotionEffect(new PotionEffect(potionType(effect.kind()), effect.durationTicks(),
                        effect.amplifier()));
            }
        }
    }

    private static PotionEffectType potionType(NutritionEating.RottenEffect.Kind kind) {
        return switch (kind) {
            case HUNGER -> PotionEffectType.HUNGER;
            case POISON -> PotionEffectType.POISON;
        };
    }
}
