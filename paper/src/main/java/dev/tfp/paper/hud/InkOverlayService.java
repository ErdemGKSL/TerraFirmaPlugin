/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/IngameOverlays.java @ c3b4d9536 (renderInk)
 */
package dev.tfp.paper.hud;

import dev.tfp.paper.effect.InkListener;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The ink splatter overlay (PAR-HUD-20, TFC {@code IngameOverlays.INK} / {@code renderInk}): while a player has the ink
 * effect TFC stretches {@code ink_splatter.png} over the screen, with glow ink {@code glow_ink_splatter.png} (ink wins when
 * a player has both). The effects ride vanilla carriers (PAR-DATA-07, {@link InkListener}), so this service publishes
 * {@code tfp_ink} (0 none, 1 ink, 2 glow ink) per player for BetterHud ({@code HudEffectValues} in
 * {@code integration/betterhud}).
 *
 * <p>Only the effects {@link InkListener} applies count (the marker): a carrier added by a plugin ({@code Cause.PLUGIN})
 * with exactly {@link InkListener#INK_TICKS} ticks and amplifier 0. A Warden's Darkness, a command, a potion, a beacon or
 * the 200-tick glow arrow do not draw the splatter. The marker lives in memory (a restart forgets a running ink, at most
 * 5 s). It is dropped when the carrier is removed, expires or is cleared (milk, {@code /effect clear}, death), and a
 * once-a-second check on the player's own thread drops a mark whose carrier is gone.
 *
 * <p>Threading: marks change inside {@link EntityPotionEffectEvent} on the player's thread; the check hops to the
 * player's thread through the scheduler. The published values are an immutable int per player in a
 * {@link ConcurrentHashMap} that BetterHud's async placeholders read by uuid. The map is static because the BetterHud
 * adapter registers its placeholders before the modules are enabled; it is cleared on quit and on {@link #disable()}.
 *
 * <p>Game modes: spectator reads 0 (hidden, like vanilla's HUD there); survival, adventure and creative draw it, as TFC
 * does (it checks only the effect). The game-mode change publishes inside the event with the new mode.
 */
public final class InkOverlayService implements FeatureModule, Listener {
    /** The check period for marks whose carrier may have gone without an event: one second. */
    public static final long PERIOD_TICKS = 20;

    /** {@code tfp_ink}: nothing drawn. */
    public static final int NONE = 0;
    /** {@code tfp_ink}: TFC's ink splatter. */
    public static final int INK = 1;
    /** {@code tfp_ink}: TFC's glow ink splatter. */
    public static final int GLOW_INK = 2;

    private static final int MARK_INK = 1;
    private static final int MARK_GLOW = 2;

    /** Published values; absent means {@link #NONE}. */
    private static final Map<UUID, Integer> READINGS = new ConcurrentHashMap<>();
    /** The marks, a bit set of {@link #MARK_INK} and {@link #MARK_GLOW}; touched on the player's thread only. */
    private static final Map<UUID, Integer> MARKS = new ConcurrentHashMap<>();

    /** Any thread: {@code tfp_ink} of {@code player}. */
    public static int reading(UUID player) {
        return READINGS.getOrDefault(player, NONE);
    }

    private volatile Scheduler scheduler;
    private volatile Scheduler.TaskHandle throttle;

    /** For the service file (the feature module). */
    public InkOverlayService() {
    }

    /** With a scheduler and no throttle (tests). */
    InkOverlayService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return "ink_overlay";
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        context.listen(this);
        start(() -> context.plugin().getServer().getOnlinePlayers());
    }

    void start(Supplier<Collection<? extends Player>> online) {
        throttle = scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            for (Player player : online.get()) {
                if (MARKS.containsKey(player.getUniqueId())) {
                    refresh(player);
                }
            }
        });
    }

    @Override
    public void disable() {
        READINGS.clear();
        MARKS.clear();
        Scheduler.TaskHandle t = throttle;
        throttle = null;
        if (t != null) {
            t.cancel();
        }
    }

    /** The carrier type's mark bit, 0 for any other effect. */
    static int markOf(PotionEffectType type) {
        if (InkListener.INK_CARRIER.equals(type)) {
            return MARK_INK;
        }
        return InkListener.GLOW_INK_CARRIER.equals(type) ? MARK_GLOW : 0;
    }

    /** TFC's rule as a value: ink first, else glow ink; nothing in spectator. */
    static int value(int marks, GameMode mode) {
        if (mode == GameMode.SPECTATOR) {
            return NONE;
        }
        if ((marks & MARK_INK) != 0) {
            return INK;
        }
        return (marks & MARK_GLOW) != 0 ? GLOW_INK : NONE;
    }

    /** Whether an effect added with {@code cause} is the one {@link InkListener} applies. */
    static boolean isOurs(PotionEffect effect, EntityPotionEffectEvent.Cause cause) {
        return effect != null && markOf(effect.getType()) != 0 && cause == EntityPotionEffectEvent.Cause.PLUGIN
            && effect.getAmplifier() == 0 && effect.getDuration() == InkListener.INK_TICKS;
    }

    /** Player's thread: publishes {@code marks} for a player in {@code mode}. */
    private static void publish(UUID player, int marks, GameMode mode) {
        if (marks == 0) {
            MARKS.remove(player);
        } else {
            MARKS.put(player, marks);
        }
        int v = value(marks, mode);
        if (v == NONE) {
            READINGS.remove(player);
        } else {
            READINGS.put(player, v);
        }
    }

    private static int marks(UUID player) {
        return MARKS.getOrDefault(player, 0);
    }

    /** Any thread: re-reads the player's marks against their real effects on the player's thread. */
    public void refresh(Player player) {
        Scheduler s = scheduler;
        if (s != null) {
            s.entity(player, () -> reconcile(player), () -> clear(player.getUniqueId()));
        }
    }

    /** Player thread only: drops a mark whose carrier is gone and republishes. */
    void reconcile(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        int marks = marks(id);
        if (!player.isOnline()) {
            clear(id);
            return;
        }
        if ((marks & MARK_INK) != 0 && !player.hasPotionEffect(InkListener.INK_CARRIER)) {
            marks &= ~MARK_INK;
        }
        if ((marks & MARK_GLOW) != 0 && !player.hasPotionEffect(InkListener.GLOW_INK_CARRIER)) {
            marks &= ~MARK_GLOW;
        }
        publish(id, marks, player.getGameMode());
    }

    private static void clear(UUID id) {
        MARKS.remove(id);
        READINGS.remove(id);
    }

    /** An effect is added, changed, removed (also expiry) or cleared; the event runs on the player's thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        int marks = marks(id);
        switch (event.getAction()) {
            case ADDED, CHANGED -> {
                PotionEffect added = event.getNewEffect();
                if (isOurs(added, event.getCause())) {
                    marks |= markOf(added.getType());
                } else if (added != null) {
                    // Another source replaces the effect: it is not ours any more.
                    marks &= ~markOf(added.getType());
                }
            }
            case REMOVED -> {
                PotionEffect old = event.getOldEffect();
                if (old != null) {
                    marks &= ~markOf(old.getType());
                }
            }
            case CLEARED -> marks = 0;
        }
        if (marks != marks(id) || READINGS.containsKey(id)) {
            publish(id, marks, player.getGameMode());
        }
    }

    /** Switching game mode: the new mode's value at once (the event runs before the mode is applied). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        publish(id, marks(id), event.getNewGameMode());
    }

    /** Death clears every effect. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    /** A world change keeps effects: republish for the current mode on the player's next tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }
}
