/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater.hot;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * PAR-FLUID-04: hot spring healing and TFC's bubble columns (NP-FLUID-01). Needs {@link WorldWaterService} (module
 * {@code worldwater}) to know TFC worlds and their water; without it the module stays off. Provides {@link SpringWater}.
 *
 * <p>Threads: the global scheduler hands each player of a TFC world one {@link HotSpringTracker#tick} on the player's thread
 * every {@link HotSpringTracker#PERIOD} ticks; the air listener runs on the entity's thread.
 */
public final class HotSpringModule implements FeatureModule, Listener {
    public static final String ID = "hotspring";

    /** Vanilla {@code minecraft:drown} damage with no attacker. */
    static final HotSpringTracker.Hurt DROWN = (player, amount) -> player.damage(amount,
            DamageSource.builder(DamageType.DROWN).build());

    /**
     * Health regained through {@code EntityRegainHealthEvent} with reason {@code CUSTOM}: TFC's {@code living.heal} fires the
     * heal event with no cause, and none of Bukkit's named reasons (food, regeneration, potions) fits a hot spring.
     */
    static final HotSpringTracker.Heal HEAL = (player, amount) -> player.heal(amount,
            EntityRegainHealthEvent.RegainReason.CUSTOM);

    private volatile Scheduler scheduler;
    private volatile WorldWaterService water;
    private volatile HotSpringTracker tracker;
    private volatile Scheduler.TaskHandle task;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Optional<WorldWaterService> service = context.services().get(WorldWaterService.class);
        if (service.isEmpty()) {
            throw new IllegalStateException("hotspring needs the worldwater module");
        }
        scheduler = context.scheduler();
        water = service.get();
        SpringWater springs = new SpringWater(water::chunk);
        context.services().provide(SpringWater.class, springs);
        tracker = new HotSpringTracker(springs, DROWN, HEAL,HotSpringRules.HOT_WATER_HEAL_AMOUNT,
                HotSpringRules.BUBBLE_COLUMN_PROVIDES_AIR, ThreadLocalRandom::current);
        context.listen(this);
        task = scheduler.globalRepeating(HotSpringTracker.PERIOD, HotSpringTracker.PERIOD, this::tickAll);
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = task;
        if (t != null) {
            t.cancel();
        }
    }

    /** Global thread: hands each player's period to the player's thread. */
    private void tickAll() {
        HotSpringTracker t = tracker;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (water.isTfcWorld(player.getWorld())) {
                scheduler.entity(player, () -> t.tick(player), null);
            }
        }
    }

    /** Entity thread: vanilla's air gain in a drag-down bubble column is cancelled when those give no air. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAirChange(EntityAirChangeEvent event) {
        if (event.getEntity() instanceof Player player && water.isTfcWorld(player.getWorld())) {
            cancelGain(event, player, tracker);
        }
    }

    static void cancelGain(EntityAirChangeEvent event, Player player, HotSpringTracker tracker) {
        if (event.getAmount() > player.getRemainingAir() && tracker.holdsBreath(player)) {
            event.setCancelled(true);
        }
    }
}
