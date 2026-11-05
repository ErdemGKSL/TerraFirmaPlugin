/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.food.EatDecision;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Eating under TFC's rules (M3-SURV-02, PAR-SURV-03). What a consume does is {@link EatDecision#decide}:
 *
 * <ul>
 *   <li>a custom food with an edible food definition is recorded with the core rule ({@link NutritionEating}); vanilla
 *       consumes the item, and the vanilla food level and saturation are set to TFC's values one tick later, since
 *       vanilla applies the item's own nutrition after the event;</li>
 *   <li>a vanilla item with an edible TFC definition ({@code minecraft:pumpkin_pie}, {@code minecraft:rotten_flesh}) and
 *       the milk bucket: vanilla's consume is cancelled (its food, its consume effects, milk's effect clearing) and
 *       TFC's eat is applied instead, with the stack used up here (TFC {@code MilkBucketItemMixin});</li>
 *   <li>a vanilla food without a TFC definition (bread, beef, golden apples, stews...) and an item whose definition
 *       says {@code "edible": false}: TFC removes their food component, so they cannot be eaten; the consume is
 *       cancelled;</li>
 *   <li>the vanilla honey bottle: not food in TFC, but vanilla 1.21.1's {@code HoneyBottleItem} is still drunk without
 *       the food component; vanilla's consume (its food) is cancelled, Poison is removed and one bottle is used for a
 *       glass bottle, with no nutrition and no eaten hook;</li>
 *   <li>anything else (potions, custom non-food items) is left alone.</li>
 * </ul>
 *
 * <p>A food is rotten when its stack is rotten or its definition always is ({@link EatDecision#alwaysRotten}: vanilla
 * rotten flesh, {@code decay_modifier} infinity, has no stamp). A rotten food records nothing and gives TFC's rotten-food effects (Hunger II, sometimes Poison I). Every eaten
 * food fires a {@link NutritionFoodEatenEvent} (the hook of TFC's {@code EAT_ROTTEN_FOOD} and {@code FULL_NUTRITION}
 * advancements). Difficulty plays no part: TFC's {@code PlayerInfo#eat} is the same in peaceful.
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
    private final Scheduler scheduler;
    private final Consumer<Event> events;
    private final DoubleSupplier random;

    /**
     * @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread
     * @param definitions the food definitions of the current registry snapshot
     * @param hooks       the food queries (rotten check) at the current tick
     * @param scheduler   schedules the vanilla food level write on the player's region, one tick later
     */
    public NutritionEatListener(PlayerDataStore data, LongSupplier playerTicks, CustomItems items,
            Supplier<FoodDefinitions> definitions, ItemFoodHooks hooks, Scheduler scheduler) {
        this(data, playerTicks, items, definitions, hooks, scheduler, e -> Bukkit.getPluginManager().callEvent(e),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * @param events fires the {@link NutritionFoodEatenEvent} (the plugin manager's {@code callEvent})
     * @param random the rotten-effect rolls, uniform in [0, 1)
     */
    public NutritionEatListener(PlayerDataStore data, LongSupplier playerTicks, CustomItems items,
            Supplier<FoodDefinitions> definitions, ItemFoodHooks hooks, Scheduler scheduler, Consumer<Event> events,
            DoubleSupplier random) {
        this.data = data;
        this.playerTicks = playerTicks;
        this.items = items;
        this.definitions = definitions;
        this.hooks = hooks;
        this.scheduler = scheduler;
        this.events = events;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ThreadAsserts.owned(player);
        ItemStack stack = event.getItem();
        Material type = stack.getType();
        EatDecision decision = EatDecision.decide(items.idOf(stack), type.getKey().toString(), type.isEdible(),
                definitions.get());
        switch (decision.action()) {
            case IGNORE -> {
                return;
            }
            case BLOCK -> {
                event.setCancelled(true); // TFC: not edible (no food component), so it is never eaten
                return;
            }
            case USE_INSTEAD -> {
                // Honey bottle (vanilla 1.21.1 HoneyBottleItem#finishUsingItem without the food): only the cure.
                event.setCancelled(true);
                if (useOne(player, event.getHand(), stack, decision.remainder(), true)) {
                    player.removePotionEffect(PotionEffectType.POISON);
                }
                return;
            }
            case EAT, EAT_INSTEAD -> {
                // below
            }
        }
        boolean instead = decision.action() == EatDecision.Action.EAT_INSTEAD;
        if (instead) {
            // Vanilla's consume is replaced: its food, its consume effects and milk's effect clearing do not happen,
            // even when the player's data is not loaded (then nothing is eaten at all).
            event.setCancelled(true);
        }
        if (!data.isLoaded(player.getUniqueId())) {
            return;
        }
        boolean rotten = decision.alwaysRotten() || hooks.isRotten(stack);
        if (instead) {
            if (!useOne(player, event.getHand(), stack, decision.remainder(), false)) {
                return;
            }
            eat(player, decision, rotten, false);
        } else {
            eat(player, decision, rotten, true);
        }
    }

    /**
     * TFC {@code MilkBucketItemMixin}: outside creative the stack shrinks by one, and a used-up stack becomes the
     * remainder (an empty bucket for milk). With {@code remainderEachUse} (vanilla's honey bottle) a stack that is not
     * used up also gives one remainder, added to the inventory or dropped when it is full. False when the hand no
     * longer holds the consumed item (nothing used).
     */
    static boolean useOne(Player player, EquipmentSlot hand, ItemStack consumed, Optional<String> remainder,
            boolean remainderEachUse) {
        EquipmentSlot slot = hand == null ? EquipmentSlot.HAND : hand;
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(slot);
        if (held == null || !held.isSimilar(consumed)) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        Material left = remainder.map(Material::matchMaterial).orElse(Material.AIR);
        boolean hasRemainder = left != null && !left.isAir();
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            inventory.setItem(slot, held);
            if (remainderEachUse && hasRemainder) {
                for (ItemStack overflow : inventory.addItem(new ItemStack(left)).values()) {
                    player.getWorld().dropItem(player.getLocation(), overflow);
                }
            }
            return true;
        }
        inventory.setItem(slot, hasRemainder ? new ItemStack(left) : null);
        return true;
    }

    /**
     * Records the meal and sets the vanilla food level. With {@code vanillaConsumes}, vanilla still adds the item's
     * own food after this event, so TFC's level is written one tick later; otherwise at once.
     */
    private void eat(Player player, EatDecision decision, boolean rotten, boolean vanillaConsumes) {
        UUID id = player.getUniqueId();
        Optional<FoodData> food = decision.food();
        long now = playerTicks.getAsLong();
        int vanillaFood = player.getFoodLevel();
        float vanillaSaturation = player.getSaturation();
        PlayerData after = data.update(id,
                d -> NutritionEating.eat(d, food, rotten, vanillaFood, vanillaSaturation, now));
        // TFC sets the vanilla food level (PlayerInfo.java 208-211) and the rotten branch leaves it alone.
        NutritionEating.VanillaFood vanilla = NutritionEating.vanillaAfterEating(vanillaFood, vanillaSaturation, food,
                rotten);
        Runnable write = () -> {
            player.setFoodLevel(vanilla.food());
            player.setSaturation(vanilla.saturation());
        };
        if (vanillaConsumes) {
            scheduler.entityDelayed(player, 1, write, null);
        } else {
            write.run();
        }
        if (rotten) {
            // TFC PlayerInfo#eat(IFood): the rotten branch gives effects instead of nutrition
            for (NutritionEating.RottenEffect effect : NutritionEating.rottenEffects(random)) {
                player.addPotionEffect(new PotionEffect(potionType(effect.kind()), effect.durationTicks(),
                        effect.amplifier()));
            }
        }
        float average = rotten ? Float.NaN : NutritionEating.averageNutrition(after, vanilla.food());
        events.accept(new NutritionFoodEatenEvent(player, decision.itemId(), rotten, average));
    }

    private static PotionEffectType potionType(NutritionEating.RottenEffect.Kind kind) {
        return switch (kind) {
            case HUNGER -> PotionEffectType.HUNGER;
            case POISON -> PotionEffectType.POISON;
        };
    }
}
