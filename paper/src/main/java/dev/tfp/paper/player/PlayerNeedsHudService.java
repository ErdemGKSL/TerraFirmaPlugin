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
import dev.tfp.core.health.HealthScale;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.hud.HudHemisphere;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

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
 *
 * <p>PAR-HUD-08 / PAR-HUD-11: the main-hand stack's heat record ({@link HeatPdc}, its stored temperature and stamp tick,
 * and the stack's heat capacity from the injected {@link ItemHeatHooks.Capacities}) is published as
 * {@link HudSnapshot#heldHeat()} on the same reading, and on the player's next tick after anything that changes the
 * main-hand stack (hotbar slot, swap hands, inventory click or drag, pickup, drop: {@link #refreshHeld}). Code that
 * writes the main-hand heat itself on the player's thread ({@code /tfp debug heat}) publishes inline instead
 * ({@link #heldHeatWritten}, PAR-HUD-10). A changed record also redraws the HUD at once ({@link HudSnapshotCache#push}),
 * so the popup does not wait for BetterHud's next update. The temperature itself is not precomputed: the placeholders evaluate the record with TFC's
 * cooling rule at the calendar tick of each read ({@link HudSnapshot#heldTemperature()}, the same rule as
 * {@link ItemHeatHooks#temperature}), so the popup follows the cooling with no lag and never reads the ItemStack off
 * the player's thread. The popup shows while it is above 0 °C, the range in which TFC's
 * {@code ItemTemperatureDisplayStyle.format} shows a temperature at all ({@link #heldTemperature(float)}).
 *
 * <p>PAR-HUD-12: in creative and spectator ({@link GameModeNeeds#needsActive}) the snapshot is published with
 * {@link HudSnapshot#needsActive()} false, which hides every bar and the held-item popup, and the thirst clock is
 * re-stamped at each reading instead of decayed, so the needs are paused. A game-mode switch
 * ({@link #onGameModeChange}) settles the clock and publishes the new mode's flag inside the event, on the player's
 * thread, so the snapshot never lags the switch.
 *
 * <p>PAR-HUD-16: each reading also publishes the player's hemisphere ({@link HudHemisphere}, TFC
 * {@code ClientHelpers.inNorthernHemisphere}), read here on the player's thread, so the {@code tfp_season} text follows a
 * player who crosses a hemisphere band (or changes world) within one {@value #PERIOD_TICKS}-tick reading.
 *
 * <p>PAR-HUD-19: each reading also publishes the {@link HudSnapshot.BarStates} of TFC {@code IngameOverlays
 * .renderHealthBar} and {@code renderThirst} ({@link #barStates}): the absorption (fill and text include it), the yellow
 * surplus bar, the steady part of the health glow (hurt with saturation, or Regeneration) and the thirst overheat level
 * ({@link #overheatLevel}). A damage to the player ({@link #onDamage}) stamps the end of TFC's {@code hurtTime} (10 ticks)
 * into the snapshot at once, so the glow shows on BetterHud's next update and ends by itself when the placeholder reads a
 * later clock; it also republishes on the next tick (the health after the hit). A potion effect change republishes too
 * (Regeneration, Absorption).
 */
public final class PlayerNeedsHudService implements Listener {
    /** The throttle: one reading per player per second. */
    public static final long PERIOD_TICKS = 20;

    /**
     * PAR-HUD-19: the number of pre-tinted overheat frames of the thirst bar; TFC's alpha {@code contribution / 0.4} is
     * stepped to {@code 0..OVERHEAT_LEVELS} ({@link #overheatLevel}). Must match {@code HudLayouts.OVERHEAT_FRAMES} in
     * tools/datagen.
     */
    public static final int OVERHEAT_LEVELS = 4;

    /** Vanilla {@code LivingEntity.hurtDuration}: {@code hurtTime} runs 10 ticks after a hit (500 ms at 20 TPS). */
    static final long HURT_NANOS = 10 * 50_000_000L;

    /** TFC's surplus divisor ({@code absorption / 20f}): the absorption that fills the yellow bar once. */
    static final float SURPLUS_ABSORPTION = 20f;

    private final PlayerDataStore data;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    private final LongSupplier playerTicks;
    private final ItemHeatHooks.Capacities heatCapacities;
    private final HeatConfig heatConfig;
    /** The clock of the hurt time ({@link System#nanoTime()}; tests replace it). */
    private LongSupplier nanoClock = System::nanoTime;

    /**
     * @param playerTicks    the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread; also the clock
     *                       the published held heat record is evaluated against at placeholder time
     * @param heatCapacities the heat capacity of a stack, the same one the plugin's {@link ItemHeatHooks} uses
     *                       (injected at wiring time, PAR-HUD-11)
     */
    public PlayerNeedsHudService(PlayerDataStore data, Scheduler scheduler, HudSnapshotCache hud,
            LongSupplier playerTicks, ItemHeatHooks.Capacities heatCapacities) {
        this.data = data;
        this.scheduler = scheduler;
        this.hud = hud;
        this.playerTicks = playerTicks;
        this.heatCapacities = heatCapacities;
        this.heatConfig = HeatConfig.DEFAULT;
    }

    /** Without heat data: no held stack is heatable, the held temperature stays 0 (tests). */
    PlayerNeedsHudService(PlayerDataStore data, Scheduler scheduler, HudSnapshotCache hud, LongSupplier playerTicks) {
        this(data, scheduler, hud, playerTicks, stack -> 0f);
    }

    /**
     * The value published for a held stack at {@code temperature} °C: the temperature itself while it is above 0 (TFC
     * {@code ItemTemperatureDisplayStyle.format} formats only positive temperatures), else 0, so the popup condition
     * {@code tfp_held_temp > 0} hides it. NaN reads as 0.
     */
    static double heldTemperature(float temperature) {
        return temperature > 0f ? temperature : 0;
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

    /** Tests: the clock the hurt time is stamped with. */
    void nanoClock(LongSupplier clock) {
        this.nanoClock = clock;
    }

    /**
     * PAR-HUD-19, TFC {@code IngameOverlays.renderHealthBar} / {@code renderThirst}: the bar states of a player with vanilla
     * {@code health} of {@code maxHealth}, {@code absorption} (vanilla HP, NaN reads 0 as in TFC), the food
     * {@code saturation} and Regeneration, at the health modifier {@code modifier}, in {@code ambientTemperature} °C
     * (NaN: no reading, no tint). The hurt time ({@code hurtUntilNanos}) is carried over from the last snapshot.
     * <ul>
     *   <li>absorption on the shown scale ({@link HealthScale#shownHealth}, the same scale as the health);</li>
     *   <li>surplus {@code clamp(clamp((health + absorption) / max, 0, 1) + absorption / 20 - 1, 0, 1)};</li>
     *   <li>steady glow {@code (saturation > 0 && 0 < health < max) || regeneration} (the {@code hurtTime > 0} part is the
     *       hurt stamp, {@link HudSnapshot#healthGlow(long)});</li>
     *   <li>overheat level {@link #overheatLevel}.</li>
     * </ul>
     */
    static HudSnapshot.BarStates barStates(float health, float maxHealth, float absorption, float saturation,
            boolean regeneration, float modifier, double ambientTemperature, long hurtUntilNanos) {
        float abs = Float.isNaN(absorption) || absorption < 0 ? 0f : absorption;
        double surplus = 0;
        if (maxHealth > 0) {
            float percent = Math.clamp((health + abs) / maxHealth, 0f, 1f);
            surplus = Math.clamp(percent + abs / SURPLUS_ABSORPTION - 1f, 0f, 1f);
        }
        boolean hurt = health > 0f && health < maxHealth;
        boolean steady = (saturation > 0f && hurt) || regeneration;
        double shownAbsorption = HealthScale.shownHealth(abs, modifier, HealthConfig.DEFAULT);
        return new HudSnapshot.BarStates(shownAbsorption, surplus, steady, hurtUntilNanos,
                overheatLevel(ambientTemperature));
    }

    /**
     * PAR-HUD-19: TFC's overheat alpha {@code getThirstContributionFromTemperature() / MAX_TEMPERATURE_THIRST_DECAY}
     * ({@link ThirstState#temperatureContribution}, 0 at 22 °C and below, 1 at 34 °C and above) rounded to the nearest of
     * {@code 0..}{@value #OVERHEAT_LEVELS}; 0 without a reading (NaN).
     */
    static int overheatLevel(double ambientTemperature) {
        if (Double.isNaN(ambientTemperature)) {
            return 0;
        }
        ThirstConfig config = ThirstConfig.DEFAULT;
        float alpha = ThirstState.temperatureContribution((float) ambientTemperature, config) / config.maxTemperatureDecay();
        return Math.clamp(Math.round(alpha * OVERHEAT_LEVELS), 0, OVERHEAT_LEVELS);
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
        publish(player, GameModeNeeds.needsActive(player.getGameMode()));
    }

    /**
     * Player thread only: {@link #publish(Player)} with the needs on ({@code active}) or off as given by the caller,
     * instead of read from the player's current game mode. {@link #onGameModeChange} passes the new mode's flag, because
     * Paper fires the event before the mode is applied.
     */
    void publish(Player player, boolean active) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        Optional<PlayerData> stored = data.get(id);
        if (stored.isEmpty()) {
            return; // not loaded yet (or already unloaded): the last snapshot stays
        }
        HudSnapshot base = hud.snapshot(id);
        long now = playerTicks.getAsLong();
        float temperature = (float) base.ambientTemperature();
        PlayerData evaluated = data.update(id, d -> GameModeNeeds.evaluate(d, active, now, temperature));
        float modifier = healthModifier(Optional.of(evaluated), player.getFoodLevel());
        HealthDisplay health = HealthDisplay.of((float) player.getHealth(), (float) player.getMaxHealth(), modifier,
                HealthConfig.DEFAULT);
        HudSnapshot.BarStates states = barStates((float) player.getHealth(), (float) player.getMaxHealth(),
                (float) player.getAbsorptionAmount(), player.getSaturation(),
                player.hasPotionEffect(PotionEffectType.REGENERATION), modifier, base.ambientTemperature(),
                base.barStates().hurtUntilNanos());
        hud.publish(player, withNeeds(base, evaluated, player.getFoodLevel()).withHealth(health.health(), health.maxHealth())
                .withAir(player.getRemainingAir()).withArmor(armor(player))
                .withHeldHeat(heldHeatOf(player)).withNeedsActive(active)
                .withNorthernHemisphere(HudHemisphere.north(player)).withBarStates(states));
    }

    /**
     * PAR-HUD-19: a hit on the player starts TFC's {@code hurtTime} glow. Paper fires the event on the player's thread
     * before the damage is applied: the hurt stamp goes into the snapshot here (the glow shows on BetterHud's next update
     * and ends {@value #HURT_NANOS} ns later at placeholder time), and a republish on the next tick reads the health after
     * the hit (TFC's {@code hurt with saturation} part of the glow).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && hud.isActive()) {
            hurt(player);
            refresh(player);
        }
    }

    /** Player thread only: stamps the end of the hurt time ({@link #HURT_NANOS} from now) into the player's snapshot. */
    void hurt(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        HudSnapshot before = hud.snapshot(id);
        hud.publish(player, before.withBarStates(before.barStates().withHurtUntil(nanoClock.getAsLong() + HURT_NANOS)));
    }

    /** PAR-HUD-19: Regeneration and Absorption change the glow, the fill and the surplus bar: republish on the next tick. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && hud.isActive()) {
            refresh(player);
        }
    }

    /** The player's armour points (vanilla {@code Attribute.ARMOR}, equipment and effects included); 0 without one. */
    static double armor(Player player) {
        AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
        return armor == null ? 0 : armor.getValue();
    }

    /**
     * Any thread: hops to the player's thread and republishes only the held item temperature (no thirst evaluation).
     * Like {@link #refresh} the task runs on the player's next tick, so it reads the main hand after the change that
     * triggered it has been applied (the hotbar, swap, click, drag, pickup and drop events fire before the change). When
     * the held record changed, the HUD is redrawn at once ({@link #publishHeld}).
     */
    public void refreshHeld(Player player) {
        if (hud.isActive()) {
            scheduler.entity(player, () -> publishHeld(player), null);
        }
    }

    /**
     * PAR-HUD-10: player thread only, for code that has just written the main-hand stack's heat itself
     * ({@code /tfp debug heat}, {@code /tfp debug warm}, any main-hand heat write): publishes the new record inline, with
     * no next-tick hop, and redraws the HUD at once, so the popup shows the new level on the frame after the write.
     * A no-op while no HUD plugin renders the values.
     */
    public void heldHeatWritten(Player player) {
        if (hud.isActive()) {
            publishHeld(player);
        }
    }

    /**
     * Player thread only: the latest snapshot with the main-hand stack's heat record. When the record differs from the
     * published one, the HUD plugin redraws the player's HUD now ({@link HudSnapshotCache#push}) instead of at its next
     * update; an unchanged record (most inventory clicks) costs no redraw.
     *
     * @return true if the published record changed
     */
    boolean publishHeld(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        HudSnapshot before = hud.snapshot(id);
        HudSnapshot.HeldHeat held = heldHeatOf(player);
        if (held.equals(before.heldHeat())) {
            return false;
        }
        hud.publish(player, before.withHeldHeat(held));
        hud.push(id);
        return true;
    }

    /** Player thread only: the main-hand stack's heat record ({@link #heldHeat}). */
    private HudSnapshot.HeldHeat heldHeatOf(Player player) {
        return heldHeat(player.getInventory().getItemInMainHand());
    }

    /**
     * Owner thread of the stack: its stored heat record with its capacity, evaluated later against the calendar clock;
     * {@link HudSnapshot.HeldHeat#NONE} for an empty hand, a stack never heated, or one that cannot be heated.
     */
    HudSnapshot.HeldHeat heldHeat(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return HudSnapshot.HeldHeat.NONE;
        }
        Optional<HeatState> state = HeatPdc.read(stack);
        if (state.isEmpty()) {
            return HudSnapshot.HeldHeat.NONE;
        }
        float capacity = heatCapacities.of(stack);
        return capacity > 0f ? HudSnapshot.HeldHeat.of(state.get(), capacity, heatConfig, playerTicks)
                : HudSnapshot.HeldHeat.NONE;
    }

    /**
     * PAR-HUD-08: a hotbar slot change republishes the held temperature. Paper fires the event before the selected slot
     * changes; the republish is a task on the player's next tick ({@link #refreshHeld}), by when the slot has changed, so
     * it reads the newly selected stack (and, after several quick changes in one tick, the last one).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        refreshHeld(event.getPlayer());
    }

    /** PAR-HUD-08: swapping the hands changes the main-hand stack. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        refreshHeld(event.getPlayer());
    }

    /**
     * PAR-HUD-08: an inventory click can put a stack into, take it out of, or change the held slot (cursor, shift-click,
     * number key, a device's slot when the stack goes back to the hotbar). A republish is one heat read, so any click
     * of the player republishes rather than re-deriving which slots the click touched.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshHeld(player);
        }
    }

    /** PAR-HUD-08: a drag can spread the cursor stack into the held slot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshHeld(player);
        }
    }

    /** PAR-HUD-08: a picked up item can land in the held slot (or merge into the held stack). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshHeld(player);
        }
    }

    /** PAR-HUD-08: dropping from the main hand shrinks or empties the held stack. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        refreshHeld(event.getPlayer());
    }

    /**
     * PAR-HUD-12: a switch between a mode with needs (survival, adventure) and one without (creative, spectator). Paper
     * fires the event on the player's own thread before the mode changes, so the handler does the switch right there: it
     * settles the thirst clock at the switch ({@link GameModeNeeds#atSwitch}: decayed up to it when leaving survival or
     * adventure, re-stamped when leaving creative or spectator) and publishes with the new mode's flag
     * ({@code event.getNewGameMode()}), so the snapshot holds the new state when the handler returns and the bars hide or
     * show on BetterHud's next update. A republish on the player's next tick (after the mode is applied) stays as a
     * safety net. A switch between two modes of the same kind (survival and adventure, creative and spectator) changes
     * nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        boolean wasActive = GameModeNeeds.needsActive(player.getGameMode());
        boolean active = GameModeNeeds.needsActive(event.getNewGameMode());
        if (wasActive == active) {
            return;
        }
        switched(player, wasActive, active);
        refresh(player);
    }

    /** Player thread only: the clock settled at the switch, then a publish in the new mode ({@code active}). */
    void switched(Player player, boolean wasActive, boolean active) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return;
        }
        long now = playerTicks.getAsLong();
        float temperature = (float) hud.snapshot(id).ambientTemperature();
        data.update(id, d -> GameModeNeeds.atSwitch(d, wasActive, now, temperature));
        publish(player, active);
    }

    /**
     * Restarts the thirst clock when the player joins (see the class comment) and publishes on the player's next tick, so
     * a player who joins in creative or spectator does not see the default snapshot's bars until the throttle.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (data.isLoaded(id)) {
            long now = playerTicks.getAsLong();
            data.update(id, d -> ThirstDecay.restart(d, now));
            refresh(player);
        }
    }
}
