/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the time-dependent tooltip lines a client shows current (PAR-INFRA-05). TFC draws a heated item's level, stars
 * and work/weld suffix, and a food's expiry text, at every frame; our lines live in the lore of the stack the server
 * last sent, so a cooling ingot would keep the text of the moment it was sent. Every {@value #PERIOD_TICKS} ticks, per
 * player, "decide async, apply sync":
 *
 * <ol>
 *   <li>snapshot (the player's entity thread): the stacks of the open view's top and bottom inventories (read
 *       through {@link Inventory#getContents()}, never by raw view slot: {@link InventoryView#countSlots()} is
 *       top size + bottom size, and the player inventory's size counts equipment slots that the 46-slot crafting menu
 *       does not expose, so raw slot {@code countSlots() - 1} does not exist there) and the cursor that carry a heat or
 *       food record, as immutable {@link TooltipItem}s, and one clock reading. A failing snapshot releases the
 *       in-flight flag and is logged once per watch;
 *   <li>decide (CPU pool): each such stack's lines ({@link TooltipPipeline#lines}) are its key; the view needs a resend
 *       when a key differs from the one of the previous run, or, for a stack first seen (just sent), from its lines one
 *       period earlier;
 *   <li>apply (the player's entity thread): if the same view is still open, {@link Player#updateInventory()} resends
 *       it once (the packet handler decorates the new copies). At most one resend per player per period.
 * </ol>
 *
 * <p>So the hovered text trails the real temperature by at most one period (one second) plus the round trip. Stacks
 * without a heat or food record are never looked at.
 */
public final class TooltipRefresher implements Listener {
    /** How often a player's stacks are checked, and the smallest gap between two resends to one player. */
    public static final long PERIOD_TICKS = 20;
    /** The language the keys are computed in; a level, star or expiry change shows in every language at once. */
    static final String KEY_LOCALE = "en_us";
    /** The cursor's key in a snapshot. */
    static final int CURSOR = -1;
    /** The key of bottom-inventory slot {@code i} in a snapshot is {@code BOTTOM + i}; top slot {@code i} is {@code i}. */
    static final int BOTTOM = 1000;

    private final Scheduler scheduler;
    private final TooltipPipeline pipeline;
    private final Supplier<ClockSnapshot> clock;
    private final Function<ItemStack, Optional<String>> ids;
    private final Function<String, FoodTrait> traits;
    private final Logger log;
    private final Map<UUID, Watch> watches = new ConcurrentHashMap<>();

    /**
     * @param ids    the CraftEngine id of a stack (the integration adapter's {@code idOf}); owner thread
     * @param traits food trait ids to traits (as the food contributor resolves them)
     */
    public TooltipRefresher(Scheduler scheduler, TooltipPipeline pipeline, Supplier<ClockSnapshot> clock,
            Function<ItemStack, Optional<String>> ids, Function<String, FoodTrait> traits, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.traits = Objects.requireNonNull(traits, "traits");
        this.log = log;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        watch(event.getPlayer());
    }

    /**
     * Re-arms the watch after a respawn. Paper's entity scheduler ends a repeating task (and runs its retired callback)
     * when it fires while its entity is removed from the world, and a dead player is removed from the world from the
     * end of the death animation until the respawn: a player who stayed on the death screen for more than about a
     * second lost the refresher for the rest of the session (in-game check 2026-10-22: a cooling ingot kept its 900 C
     * text after a death). The player itself is not retired, so a new task on it runs once it is back in a world.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        watch(event.getPlayer());
    }

    /** Same as {@link #onRespawn}: a dimension change also removes the player from a world for a moment. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        watch(event.getPlayer());
    }

    /** Whether {@code player} has a live watch (tests and debug). */
    boolean watching(UUID player) {
        return watches.containsKey(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Watch w = watches.remove(event.getPlayer().getUniqueId());
        if (w != null && w.task != null) {
            w.task.cancel();
        }
    }

    /** Starts watching {@code player} (join, or every online player when the module is enabled). Any thread. */
    public void watch(Player player) {
        UUID id = player.getUniqueId();
        Watch w = new Watch();
        Watch old = watches.put(id, w);
        if (old != null && old.task != null) {
            old.task.cancel();
        }
        w.task = scheduler.entityRepeating(player, PERIOD_TICKS, PERIOD_TICKS, () -> run(player, w),
                () -> watches.remove(id, w));
    }

    /** Stops every watch (module disable). */
    public void stop() {
        for (Watch w : watches.values()) {
            if (w.task != null) {
                w.task.cancel();
            }
        }
        watches.clear();
    }

    /** One period for one player: the player's entity thread. */
    private void run(Player player, Watch w) {
        if (pipeline.isEmpty() || !w.busy.compareAndSet(false, true)) {
            return;
        }
        InventoryView view = player.getOpenInventory();
        int token = System.identityHashCode(view);
        Map<Integer, TooltipItem> items = capture(w, player.getName(), () -> snapshot(view, ids, traits));
        if (items == null) {
            return;
        }
        ClockSnapshot now;
        try {
            now = clock.get();
        } catch (RuntimeException e) {
            // Same as a failing snapshot: release the flag so the next period tries again.
            capture(w, player.getName(), () -> {
                throw e;
            });
            return;
        }
        CompletableFuture.supplyAsync(() -> w.state.observe(token, items, now, pipeline), scheduler.cpuExecutor())
                .whenComplete((resend, error) -> {
                    if (error != null) {
                        w.busy.set(false);
                        if (log != null) {
                            log.log(Level.WARNING, "Tooltip refresh failed for " + player.getName(), error);
                        }
                        return;
                    }
                    if (!resend) {
                        w.busy.set(false);
                        return;
                    }
                    boolean scheduled = scheduler.entity(player, () -> {
                        try {
                            // Re-validate: a different view was sent in full when it opened.
                            if (player.isOnline() && System.identityHashCode(player.getOpenInventory()) == token) {
                                player.updateInventory();
                            }
                        } finally {
                            w.busy.set(false);
                        }
                    }, () -> w.busy.set(false));
                    if (!scheduled) {
                        w.busy.set(false);
                    }
                });
    }

    /**
     * Runs {@code snapshot} while holding {@code w}'s in-flight flag (already taken). On a failure the flag is released,
     * so the next period tries again, and the first failure of the watch is logged; returns null then. Owner thread.
     */
    Map<Integer, TooltipItem> capture(Watch w, String who, Supplier<Map<Integer, TooltipItem>> snapshot) {
        try {
            return snapshot.get();
        } catch (RuntimeException e) {
            w.busy.set(false);
            if (w.warned.compareAndSet(false, true) && log != null) {
                log.log(Level.WARNING, "Tooltip refresh failed for " + who
                        + " (snapshot; further failures for this player are not logged)", e);
            }
            return null;
        }
    }

    /**
     * The stacks of {@code view} and its cursor that carry a time-dependent record: top slot {@code i} under key
     * {@code i}, bottom slot {@code i} (the player inventory, off-hand and armour included) under {@code BOTTOM + i},
     * the cursor under {@link #CURSOR}. The keys are stable within one view, which is all the decision compares. Owner
     * thread.
     */
    static Map<Integer, TooltipItem> snapshot(InventoryView view, Function<ItemStack, Optional<String>> ids,
            Function<String, FoodTrait> traits) {
        Map<Integer, TooltipItem> out = new LinkedHashMap<>();
        add(out, 0, view.getTopInventory(), ids, traits);
        add(out, BOTTOM, view.getBottomInventory(), ids, traits);
        TooltipItem cursor = item(view.getCursor(), ids, traits);
        if (cursor != null) {
            out.put(CURSOR, cursor);
        }
        return out;
    }

    private static void add(Map<Integer, TooltipItem> out, int base, Inventory inventory,
            Function<ItemStack, Optional<String>> ids, Function<String, FoodTrait> traits) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            TooltipItem item = item(contents[i], ids, traits);
            if (item != null) {
                out.put(base + i, item);
            }
        }
    }

    /**
     * An immutable {@link TooltipItem} of a Bukkit stack with its heat and food records in the layout the packet sees
     * ({@link HeatPdc}, {@link FoodPdc}), or null when it carries neither. Owner thread.
     */
    static TooltipItem item(ItemStack stack, Function<ItemStack, Optional<String>> ids,
            Function<String, FoodTrait> traits) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        Optional<HeatState> heat = HeatPdc.read(stack);
        Optional<FoodState> food = FoodPdc.read(stack, traits);
        if (heat.isEmpty() && food.isEmpty()) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        heat.ifPresent(h -> values.put(HeatPdc.KEY, Map.of("tfp:v", HeatPdc.VERSION,
                "tfp:temperature", h.temperature(), "tfp:tick", h.lastTick())));
        food.ifPresent(f -> {
            List<String> traitIds = new ArrayList<>(f.traits().size());
            for (FoodTrait t : f.traits()) {
                traitIds.add(t.id());
            }
            values.put(FoodPdc.KEY, Map.of("tfp:v", FoodPdc.VERSION, "tfp:created", f.creationDate(),
                    "tfp:traits", List.copyOf(traitIds)));
        });
        return new TooltipItem(ids.apply(stack).orElse(null), stack.getType().getKey().toString(), stack.getAmount(),
                TooltipData.of(Map.copyOf(values)));
    }

    /** One player's watch: the task, the in-flight flag, the logged-once flag and the keys of the previous run. */
    static final class Watch {
        final AtomicBoolean busy = new AtomicBoolean();
        final AtomicBoolean warned = new AtomicBoolean();
        final RefreshState state = new RefreshState(PERIOD_TICKS);
        volatile Scheduler.TaskHandle task;
    }

    /**
     * The decision of the refresher, pure: the line keys of the previous run and whether the new run needs a resend.
     * Confined to one run at a time (the watch's in-flight flag orders the runs).
     */
    static final class RefreshState {
        private final long periodTicks;
        private boolean seen;
        private int token;
        private Map<Integer, List<Component>> keys = Map.of();

        RefreshState(long periodTicks) {
            this.periodTicks = periodTicks;
        }

        /**
         * Records the keys of {@code items} at {@code now} in view {@code token}.
         *
         * @return whether the view must be resent: a stack seen in the previous run (same view, same slot) whose lines
         *         changed, or a stack first seen whose lines differ from its lines one period earlier (it was sent at
         *         most one period ago, possibly with those)
         */
        boolean observe(int token, Map<Integer, TooltipItem> items, ClockSnapshot now, TooltipPipeline pipeline) {
            TooltipContext current = new TooltipContext(KEY_LOCALE, now);
            TooltipContext earlier = new TooltipContext(KEY_LOCALE, shifted(now, -periodTicks));
            boolean sameView = seen && token == this.token;
            Map<Integer, List<Component>> next = new HashMap<>();
            boolean resend = false;
            for (Map.Entry<Integer, TooltipItem> e : items.entrySet()) {
                List<Component> lines = pipeline.lines(e.getValue(), current);
                next.put(e.getKey(), lines);
                List<Component> before = sameView ? keys.get(e.getKey()) : null;
                if (before == null) {
                    before = pipeline.lines(e.getValue(), earlier);
                }
                resend |= !before.equals(lines);
            }
            this.seen = true;
            this.token = token;
            this.keys = next;
            return resend;
        }

        /** {@code clock} moved by {@code ticks} on both clocks (never below zero). */
        static ClockSnapshot shifted(ClockSnapshot clock, long ticks) {
            return new ClockSnapshot(clock.sequence(), Math.max(0L, clock.playerTicks() + ticks),
                    Math.max(0L, clock.calendarTicks() + ticks), clock.daysInMonth(), clock.running());
        }
    }
}
