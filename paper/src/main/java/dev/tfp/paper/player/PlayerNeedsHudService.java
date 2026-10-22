/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionLevels;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthDisplay;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Health, max health, thirst, hunger, air and armour for the HUD (M3-SURV-04, M3-SURV-06, M3-SURV-03). The thirst is evaluated lazily here
 * ({@link ThirstDecay#evaluate}, the stored clock plus the player ticks since): nothing ticks for it.
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, for each online player, a task goes to
 *       that player's own thread (the same period and mechanism as {@code PlayerClimateService}).</li>
 *   <li><b>Evaluate and publish (player thread).</b> The stored data is evaluated at the current player tick with the
 *       ambient temperature, the new thirst clock is written back to {@link PlayerDataStore}, and the thirst and the
 *       vanilla food level go into the player's {@link HudSnapshot} in the {@link HudSnapshotCache}. The health and max
 *       health are the vanilla values read here, shown on TFC's scale ({@link HealthDisplay}).</li>
 *   <li><b>Join (player thread).</b> The thirst clock restarts at the join, so the time the player was offline (while
 *       other players were online and the calendar ran) is not decayed.</li>
 * </ol>
 * Health uses the health modifier of the player's stored nutrition records ({@link PlayerData#nutrition()}) at the vanilla
 * food level ({@link #healthModifier}). Without stored data the modifier of TFC's average nutrition is used (850 max with
 * nothing eaten at full food). A player with nothing eaten at full food has the same modifier, so a new player sees no change.
 */
public final class PlayerNeedsHudService implements Listener {
    /** The throttle: one reading per player per second. */
    public static final long PERIOD_TICKS = 20;

    private final PlayerDataStore data;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    private final LongSupplier playerTicks;

    /** @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread */
    public PlayerNeedsHudService(PlayerDataStore data, Scheduler scheduler, HudSnapshotCache hud,
            LongSupplier playerTicks) {
        this.data = data;
        this.scheduler = scheduler;
        this.hud = hud;
        this.playerTicks = playerTicks;
    }

    /**
     * The pure part: the snapshot with the stored thirst and the vanilla food level in place of the old ones.
     * Thirst is {@link PlayerData#thirst()} (already 0..{@link PlayerData#MAX_THIRST}); the food level is clamped to
     * vanilla's 0..20.
     */
    static HudSnapshot withNeeds(HudSnapshot base, PlayerData stored, int foodLevel) {
        double thirst = Math.clamp(stored.thirst(), 0f, PlayerData.MAX_THIRST);
        double hunger = Math.clamp(foodLevel, 0, 20);
        return base.withThirst(thirst).withHunger(hunger);
    }

    /**
     * The stored nutrition records as the core state. The records are TFC's {@code NutritionData} records; the hunger
     * the values are read at is the vanilla food level, passed to {@link #healthModifier}.
     */
    static NutritionState nutritionOf(PlayerData stored) {
        List<FoodData> foods = new ArrayList<>(stored.nutrition().size());
        for (PlayerData.FoodRecord r : stored.nutrition()) {
            PlayerData.Nutrients n = r.nutrients();
            foods.add(new FoodData(r.hunger(), r.water(), r.saturation(), r.intoxication(), n.grain(), n.fruit(),
                    n.vegetables(), n.protein(), n.dairy(), r.decayModifier()));
        }
        return new NutritionState(foods);
    }

    /**
     * The health modifier (TFC {@code getHealthModifier}) of the stored nutrition at the vanilla food level
     * ({@code 0..20}). Without stored data (not loaded) it is the modifier of TFC's average nutrition.
     */
    static float healthModifier(Optional<PlayerData> stored, int foodLevel) {
        NutritionConfig config = NutritionConfig.DEFAULT;
        if (stored.isEmpty()) {
            return NutritionLevels.healthModifier(NutritionLevels.DEFAULT_AVERAGE_NUTRITION, config);
        }
        int hunger = Math.clamp(foodLevel, 0, NutritionState.MAX_HUNGER);
        return nutritionOf(stored.get()).levels(hunger, config).healthModifier(config);
    }

    /** Starts the throttle on the global thread. Call once, after the scheduler exists. */
    public void start(Supplier<Collection<? extends Player>> online) {
        scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            if (!hud.isActive()) {
                return; // nothing renders the values: no work
            }
            for (Player player : online.get()) {
                refresh(player);
            }
        });
    }

    /**
     * Any thread: hops to the player's thread to read and publish now. Call it after any change to a value the HUD shows
     * (thirst, food level, a debug command), so the bars follow at once instead of at the next {@value #PERIOD_TICKS}-tick
     * throttle. Publishing runs on the player's thread, so it is ordered with the throttled publishes.
     */
    public void refresh(Player player) {
        scheduler.entity(player, () -> publish(player), null);
    }

    /** Player thread only: evaluates the thirst lazily, writes its clock back, then publishes. */
    void publish(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        Optional<PlayerData> stored = data.get(id);
        if (stored.isEmpty()) {
            return; // not loaded yet (or already unloaded): the last snapshot stays
        }
        HudSnapshot base = hud.snapshot(id);
        long now = playerTicks.getAsLong();
        float temperature = (float) base.ambientTemperature();
        PlayerData evaluated = data.update(id, d -> ThirstDecay.evaluate(d, now, temperature));
        float modifier = healthModifier(Optional.of(evaluated), player.getFoodLevel());
        HealthDisplay health = HealthDisplay.of((float) player.getHealth(), (float) player.getMaxHealth(), modifier,
                HealthConfig.DEFAULT);
        hud.publish(player, withNeeds(base, evaluated, player.getFoodLevel()).withHealth(health.health(), health.maxHealth())
                .withAir(player.getRemainingAir()).withArmor(armor(player)));
    }

    /** The player's armour points (vanilla {@code Attribute.ARMOR}, equipment and effects included); 0 without one. */
    static double armor(Player player) {
        AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
        return armor == null ? 0 : armor.getValue();
    }

    /** Restarts the thirst clock when the player joins (see the class comment). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (data.isLoaded(id)) {
            long now = playerTicks.getAsLong();
            data.update(id, d -> ThirstDecay.restart(d, now));
        }
    }
}
