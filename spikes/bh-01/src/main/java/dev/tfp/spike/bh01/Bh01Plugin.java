package dev.tfp.spike.bh01;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.listener.HudListener;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-BH-01 throwaway spike. Placeholders and listeners run ASYNC on BetterHud's timer, so they only
 * read {@link #SNAPSHOTS}. Only the per-player task (player's own scheduler, every 20 ticks) writes it.
 */
public final class Bh01Plugin extends JavaPlugin implements Listener {

    /** Stand-in for TFC health (fixed for the spike). */
    static final int HEALTH = 45;
    static final int MAX_HEALTH = 50;

    /** Immutable per-player values. Written on the player's region/entity thread, read anywhere. */
    public record Snapshot(int health, int maxHealth, int thirst) {}

    static final Snapshot DEFAULT = new Snapshot(HEALTH, MAX_HEALTH, 100);
    static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("BetterHud") == null) {
            getLogger().severe("BetterHud not installed; BH-01 spike disabled.");
            return;
        }
        registerBetterHud();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : getServer().getOnlinePlayers()) {
            start(p);
        }
        getLogger().info("BH-01: placeholders tfp_health, tfp_max_health, tfp_thirst and listener tfp_thirst registered.");
    }

    @Override
    public void onDisable() {
        tasks.values().forEach(ScheduledTask::cancel);
        tasks.clear();
        SNAPSHOTS.clear();
    }

    private void registerBetterHud() {
        var api = BetterHudAPI.inst();
        var pm = api.getPlaceholderManager();
        // Placeholder functions: only O(1) reads of the snapshot. No Bukkit world/entity access.
        pm.getNumberContainer().addPlaceholder("tfp_health",
                HudPlaceholder.<Number>of((args, reason) -> p -> Integer.valueOf(snap(p.uuid()).health())));
        pm.getNumberContainer().addPlaceholder("tfp_max_health",
                HudPlaceholder.<Number>of((args, reason) -> p -> Integer.valueOf(snap(p.uuid()).maxHealth())));
        pm.getNumberContainer().addPlaceholder("tfp_thirst",
                HudPlaceholder.<Number>of((args, reason) -> p -> Integer.valueOf(snap(p.uuid()).thirst())));
        // 0..1 listener used by a BetterHud image with `class: tfp_thirst`.
        HudListener thirstBar = p -> snap(p.uuid()).thirst() / 100.0;
        api.getListenerManager().addListener("tfp_thirst", yaml -> event -> thirstBar);
    }

    private static Snapshot snap(UUID uuid) {
        return SNAPSHOTS.getOrDefault(uuid, DEFAULT);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ScheduledTask t = tasks.remove(id);
        if (t != null) t.cancel();
        SNAPSHOTS.remove(id);
    }

    /** Runs on the player's own scheduler (region/entity thread) every 20 ticks = 1 s. */
    private void start(Player player) {
        UUID id = player.getUniqueId();
        long[] updates = {0L};
        ScheduledTask task = player.getScheduler().runAtFixedRate(this, t -> {
            long n = updates[0]++;
            // Thirst goes 100, 99, ... 0, then wraps to 100 (one step per second).
            int thirst = 100 - (int) (n % 101);
            SNAPSHOTS.put(id, new Snapshot(HEALTH, MAX_HEALTH, thirst));
            if (n % 10 == 0) {
                getLogger().info("BH-01 snapshot " + player.getName() + " thirst=" + thirst
                        + " thread=" + Thread.currentThread().getName());
            }
        }, () -> SNAPSHOTS.remove(id), 1L, 20L);
        if (task != null) {
            ScheduledTask old = tasks.put(id, task);
            if (old != null) old.cancel();
        }
    }
}
