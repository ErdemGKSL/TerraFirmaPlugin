/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/IngameOverlays.java @ c3b4d9536 (renderMountHealth, renderHealthBar values)
 */
package dev.tfp.paper.player;

import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthScale;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The ridden animal's health for the HUD (PAR-HUD-09, TFC {@code IngameOverlays.MOUNT_HEALTH}): while a player rides a
 * {@link LivingEntity} (horse, donkey, camel, pig, strider, ...), its health and max health are published per player on
 * TFC's text scale ({@code renderHealthBar} with a health modifier of 1: {@code (health + absorption) x 50} over
 * {@code max x 50}); 0 and 0 while not riding a living entity (a boat or minecart has no health), which hides the bar.
 *
 * <p>Threading ("decide async, apply sync" has nothing to apply here: the service only reads). Every reading is taken on
 * the rider's own thread; on Folia a vehicle and its passengers are always in the same region, so the vehicle is
 * readable there. The readings live in a {@link ConcurrentHashMap} of immutable {@link MountReading}s that BetterHud's
 * async placeholders read by uuid ({@code HudMountValues} in {@code integration/betterhud}). The map is static because
 * the BetterHud adapter registers its placeholders before the feature modules are enabled and reaches no module
 * service; it holds only riders, is cleared on quit and on {@link #disable()}.
 *
 * <p>When it publishes: on mount and dismount (inside the event, on the rider's thread), on the rider's next tick after
 * the vehicle is damaged or heals, and every {@value #PERIOD_TICKS} ticks for the riders only (max health attribute
 * changes, a vehicle removed without a dismount event).
 */
public final class MountHudService implements FeatureModule, Listener {
    /** The throttle for riders: one reading per second. */
    public static final long PERIOD_TICKS = 20;

    /** One rider's mount reading on TFC's text scale; {@link #NONE} while not riding a living entity. */
    public record MountReading(double health, double maxHealth) {
        public static final MountReading NONE = new MountReading(0, 0);
    }

    private static final Map<UUID, MountReading> READINGS = new ConcurrentHashMap<>();

    /** Any thread: the latest reading of {@code player}, {@link MountReading#NONE} when not riding. */
    public static MountReading reading(UUID player) {
        return READINGS.getOrDefault(player, MountReading.NONE);
    }

    private volatile Scheduler scheduler;
    private volatile Scheduler.TaskHandle throttle;

    /** For the service file (the feature module). */
    public MountHudService() {
    }

    /** With a scheduler and no throttle (tests). */
    MountHudService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return "mount_hud";
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        context.listen(this);
        start(() -> context.plugin().getServer().getOnlinePlayers());
    }

    /** Starts the rider throttle on the global thread, and reads every online player once (a player already riding). */
    void start(Supplier<Collection<? extends Player>> online) {
        for (Player player : online.get()) {
            refresh(player);
        }
        throttle = scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            for (Player player : online.get()) {
                if (READINGS.containsKey(player.getUniqueId())) {
                    refresh(player);
                }
            }
        });
    }

    @Override
    public void disable() {
        READINGS.clear();
        Scheduler.TaskHandle t = throttle;
        throttle = null;
        if (t != null) {
            t.cancel();
        }
    }

    /**
     * TFC {@code renderHealthBar} for a non-player entity (health modifier 1): the shown health is {@code (health +
     * absorption)} (TFC: {@code percentHealth x maxHealth}, not capped at the max) and the shown max the max, both
     * {@code x 50} ({@code HealthDisplayStyle.TFC}). A NaN absorption reads as 0, as in TFC; a max that is not positive
     * reads as {@link MountReading#NONE}.
     */
    static MountReading of(double health, double absorption, double maxHealth) {
        if (!(maxHealth > 0) || Double.isInfinite(maxHealth)) {
            return MountReading.NONE;
        }
        double abs = Double.isNaN(absorption) ? 0 : absorption;
        HealthConfig config = HealthConfig.DEFAULT;
        double shown = HealthScale.shownHealth((float) Math.max(0, health + abs), 1f, config);
        return new MountReading(shown, HealthScale.shownHealth((float) maxHealth, 1f, config));
    }

    /** Owner thread of {@code vehicle}: its reading, {@link MountReading#NONE} unless it is a living entity. */
    static MountReading of(Entity vehicle) {
        if (!(vehicle instanceof LivingEntity living) || living.isDead()) {
            return MountReading.NONE;
        }
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 0 : max.getValue();
        return of(living.getHealth(), living.getAbsorptionAmount(), maxHealth);
    }

    /** Any thread: hops to the player's thread and publishes the reading of its current vehicle on its next tick. */
    public void refresh(Player player) {
        Scheduler s = scheduler;
        if (s != null) {
            s.entity(player, () -> publish(player), () -> READINGS.remove(player.getUniqueId()));
        }
    }

    /** Player thread only: the reading of the player's current vehicle. */
    void publish(Player player) {
        ThreadAsserts.owned(player);
        put(player.getUniqueId(), player.isOnline() ? of(player.getVehicle()) : MountReading.NONE);
    }

    private static void put(UUID player, MountReading reading) {
        if (reading.equals(MountReading.NONE)) {
            READINGS.remove(player);
        } else {
            READINGS.put(player, reading);
        }
    }

    /** A player gets on a vehicle: its reading at once (the event runs on the player's thread), and again next tick. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            put(player.getUniqueId(), of(event.getMount()));
            refresh(player);
        }
    }

    /** A player gets off (also on death, teleport and world change): the bar hides at once. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            READINGS.remove(player.getUniqueId());
            refresh(player); // a remount in the same tick
        }
    }

    /** The vehicle is hurt: its riders read the new health on their next tick (the damage is applied after the event). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        refreshRiders(event.getEntity());
    }

    /** The vehicle heals: as {@link #onDamage}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        refreshRiders(event.getEntity());
    }

    private void refreshRiders(Entity vehicle) {
        if (!(vehicle instanceof LivingEntity)) {
            return;
        }
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                refresh(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        READINGS.remove(event.getPlayer().getUniqueId());
    }
}
