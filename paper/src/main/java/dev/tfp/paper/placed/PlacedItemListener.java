/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/network/PlaceBlockSpecialPacket.java @ c3b4d9536 (enablePlacingItems, mayBuild, top
 *       face only, existing placed item first),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (useItemOn -> onRightClick:
 *       empty main hand or sneaking takes back)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.placed.Gesture;
import dev.tfp.core.placed.HoldTracker;
import dev.tfp.core.placed.PlacePolicy;
import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.SneakDoubleTap;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The placement gesture (PAR-POT-01, NP-CLIENT-01: TFC's key V has no vanilla-client equivalent).
 *
 * <ul>
 *   <li><b>Take back</b> (TFC {@code onRightClick}): a right-click on a placed item block with an empty main hand, or while
 *       sneaking, returns the item of the quadrant under the cursor at once.</li>
 *   <li><b>Action first</b>: when the held item or the target has a right-click action ({@link UseClassifier},
 *       {@link PlacePolicy}), the click is left alone and no hold starts. Use-duration items are exempt (the client stops
 *       resending while it uses them).</li>
 *   <li><b>Hold</b>: otherwise, on the top face of a block with an empty space above (or on a placed item block, to add to
 *       it), holding right-click resends the use packet every 4 ticks; {@link HoldTracker} completes after 1 s and the
 *       item is placed ({@link PlacedItems}). Every event of a running hold is denied for block and item, so nothing
 *       else happens while holding; an action-bar bar shows the progress. The first event of a hold is denied at once
 *       only for items whose tap opens a screen ({@code deferredTap}: vessels, molds), whose tap then runs on the release
 *       (a synthetic right-click, as the client sent it); for any other item the first event goes through and the hold
 *       starts only if no other listener acted on it (sticks placing twigs, hides on logs, ...).</li>
 *   <li><b>Sneak double tap</b> (config {@code placed-items.gesture}): two sneak starts within 400 ms arm the player; the
 *       next right-click that would start a hold places at once.</li>
 * </ul>
 * Survival, adventure-excluded (TFC {@code mayBuild}), creative keeps the stack (TFC copies it). Threads: the events and
 * the release check run on the player's thread (Folia: its region / entity scheduler), which owns the per-player state;
 * placement needs that thread to own the clicked block too (the normal case within reach), else nothing is placed.
 */
public final class PlacedItemListener implements Listener {
    /** Ticks between two release checks of a running hold. */
    static final long RELEASE_CHECK_TICKS = 5;
    private static final int BAR_CELLS = 10;

    /** What is held where; a change of any part restarts the hold. */
    record HoldKey(UUID world, int x, int y, int z, BlockFace face, EquipmentSlot hand, int hotbarSlot, int item) {}

    /**
     * The hit on the clicked block: world x/z (TFC's {@code getSlotSelected} input) and the block-relative position. Read
     * from {@code getClickedPosition}, not {@code getInteractionPoint} (no Location arithmetic on the block's own location).
     */
    record Hit(double x, double z, Vector relative) {
        static Hit of(Block block, Vector relative) {
            return new Hit(block.getX() + relative.getX(), block.getZ() + relative.getZ(), relative.clone());
        }
    }

    /** The click a deferred tap replays on release. */
    record Tap(Block block, BlockFace face, EquipmentSlot hand, Vector position, int item) {}

    /** The state of one player; touched only on that player's thread. */
    static final class PlayerHold {
        final HoldTracker<HoldKey> tracker;
        final SneakDoubleTap doubleTap;
        HoldKey firstPending;
        Tap tap;
        Scheduler.TaskHandle check;

        PlayerHold(LongSupplier millis) {
            tracker = new HoldTracker<>(millis);
            doubleTap = new SneakDoubleTap(millis);
        }
    }

    private final PlacedItems placed;
    private final UseClassifier classifier;
    private final Predicate<ItemStack> deferredTap;
    private final Scheduler scheduler;
    private final Gesture gesture;
    private final LongSupplier millis;
    private final ToLongFunction<Player> tick;
    private final Map<UUID, PlayerHold> holds = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> replaying = ThreadLocal.withInitial(() -> false);

    /**
     * @param millis a monotonic millisecond clock
     * @param tick   the tick of a player's current event (its {@code getTicksLived}: two packets handled in one tick
     *               read the same value; region-safe on Folia, unlike a server-wide tick counter)
     */
    public PlacedItemListener(PlacedItems placed, UseClassifier classifier, Predicate<ItemStack> deferredTap,
            Scheduler scheduler, Gesture gesture, LongSupplier millis, ToLongFunction<Player> tick) {
        this.placed = Objects.requireNonNull(placed, "placed");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.deferredTap = Objects.requireNonNull(deferredTap, "deferredTap");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.gesture = Objects.requireNonNull(gesture, "gesture");
        this.millis = Objects.requireNonNull(millis, "millis");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    PlayerHold state(Player player) {
        return holds.computeIfAbsent(player.getUniqueId(), u -> new PlayerHold(millis));
    }

    boolean holding(Player player) {
        PlayerHold h = holds.get(player.getUniqueId());
        return h != null && h.tracker.active();
    }

    // ---- events ---------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (replaying.get() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            if (holding(player)) {
                deny(event); // the off-hand retry of a click whose main hand is holding
            }
            return;
        }
        Block clicked = event.getClickedBlock();
        Vector relative = event.getClickedPosition();
        GameMode mode = player.getGameMode();
        if (clicked == null || relative == null || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE
                || !owns(clicked)) {
            return;
        }
        Hit point = Hit.of(clicked, relative);
        ItemStack held = event.getItem();
        boolean onPlaced = placed.isPlaced(clicked);
        if (onPlaced && PlacedRules.takes(StackCodec.isEmpty(held), player.isSneaking())) {
            if (placed.take(player, clicked, point.x(), point.z())) {
                deny(event);
            }
            release(player, false);
            return;
        }
        if (StackCodec.isEmpty(held)) {
            release(player, false);
            return;
        }
        if (!onPlaced && (event.getBlockFace() != BlockFace.UP || !owns(clicked.getRelative(BlockFace.UP))
                || !placed.canPlace(clicked, point.x(), point.z(), held, player.getYaw()))) {
            release(player, false);
            return;
        }
        PlacePolicy.Use use = PlacePolicy.decide(classifier.classify(held, onPlaced ? null : clicked));
        if (use != PlacePolicy.Use.HOLD) {
            release(player, false);
            return;
        }
        PlayerHold h = state(player);
        if (gesture.doubleTap() && h.doubleTap.consume()) {
            deny(event);
            complete(player, h, event.getHand(), clicked, onPlaced, point);
            h.tracker.placedNow();
            return;
        }
        if (!gesture.hold()) {
            return;
        }
        HoldKey key = key(player, event, clicked, held);
        boolean continuing = h.tracker.active() && !h.tracker.expired()
            && (key.equals(h.tracker.key().orElse(null)) || h.tracker.completed());
        if (!continuing && !deferredTap.test(held)) {
            h.firstPending = key; // decided after the other listeners (onInteractDone)
            return;
        }
        deny(event);
        step(player, h, key, event, clicked, onPlaced, point, held);
    }

    /** The first event of a hold with an item that has no deferred tap: the hold starts only if nobody acted on it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractDone(PlayerInteractEvent event) {
        if (replaying.get() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        PlayerHold h = holds.get(event.getPlayer().getUniqueId());
        if (h == null || h.firstPending == null) {
            return;
        }
        HoldKey key = h.firstPending;
        h.firstPending = null;
        if (event.useItemInHand() == Event.Result.DENY || event.useInteractedBlock() == Event.Result.DENY) {
            release(event.getPlayer(), false); // another listener acted: action first
            return;
        }
        Block clicked = event.getClickedBlock();
        Vector relative = event.getClickedPosition();
        if (clicked == null || relative == null || !key.equals(key(event.getPlayer(), event, clicked, event.getItem()))) {
            return;
        }
        step(event.getPlayer(), h, key, event, clicked, placed.isPlaced(clicked), Hit.of(clicked, relative), event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && gesture.doubleTap()) {
            state(event.getPlayer()).doubleTap.sneakStarted();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlot(PlayerItemHeldEvent event) {
        release(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        drop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        drop(event.getPlayer());
    }

    // ---- the hold -------------------------------------------------------------------------------------

    private void step(Player player, PlayerHold h, HoldKey key, PlayerInteractEvent event, Block clicked, boolean onPlaced,
            Hit point, ItemStack held) {
        HoldTracker.Step s = h.tracker.event(key, tick.applyAsLong(player));
        switch (s.phase()) {
            case STARTED -> {
                h.tap = deferredTap.test(held) ? new Tap(clicked, event.getBlockFace(), event.getHand(), point.relative(),
                    key.item()) : null;
                watch(player, h);
                bar(player, 0);
            }
            case PROGRESS -> bar(player, s.progress());
            case COMPLETE -> {
                h.tap = null;
                player.sendActionBar(Component.empty());
                complete(player, h, event.getHand(), clicked, onPlaced, point);
            }
            case DONE -> {
            }
        }
    }

    private void complete(Player player, PlayerHold h, EquipmentSlot hand, Block clicked, boolean onPlaced, Hit point) {
        if (onPlaced) {
            placed.insert(player, hand, clicked, point.x(), point.z());
        } else {
            placed.place(player, hand, clicked, point.x(), point.z());
        }
    }

    /** Starts the release check of a hold (every {@link #RELEASE_CHECK_TICKS} on the player's entity scheduler). */
    private void watch(Player player, PlayerHold h) {
        if (h.check != null && !h.check.isCancelled()) {
            return;
        }
        h.check = scheduler.entityRepeating(player, RELEASE_CHECK_TICKS, RELEASE_CHECK_TICKS, () -> checkRelease(player),
            () -> holds.remove(player.getUniqueId()));
    }

    /** The release check: a hold without events for 300 ms has ended; a deferred tap runs now. */
    void checkRelease(Player player) {
        PlayerHold h = holds.get(player.getUniqueId());
        if (h == null) {
            return;
        }
        if (h.tracker.expired()) {
            release(player, true);
        }
        if (!h.tracker.active() && h.check != null) {
            h.check.cancel();
            h.check = null;
        }
    }

    /** Ends the player's hold; with {@code replay}, an unfinished hold of a deferred-tap item runs its tap. */
    void release(Player player, boolean replay) {
        PlayerHold h = holds.get(player.getUniqueId());
        if (h == null) {
            return;
        }
        h.firstPending = null;
        HoldTracker.Released<HoldKey> r = h.tracker.release().orElse(null);
        Tap tap = h.tap;
        h.tap = null;
        if (r == null) {
            return;
        }
        player.sendActionBar(Component.empty());
        if (replay && !r.completed() && tap != null) {
            replayTap(player, tap);
        }
    }

    private void drop(Player player) {
        PlayerHold h = holds.remove(player.getUniqueId());
        if (h != null && h.check != null) {
            h.check.cancel();
        }
    }

    /** The tap of a deferred-tap item, as the client sent it: a right-click on the block, for the other listeners. */
    private void replayTap(Player player, Tap tap) {
        ItemStack now = player.getInventory().getItem(tap.hand());
        if (StackCodec.isEmpty(now) || fingerprint(now) != tap.item() || !owns(tap.block())) {
            return;
        }
        PlayerInteractEvent synthetic = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, now, tap.block(),
            tap.face(), tap.hand(), tap.position().clone());
        replaying.set(true);
        try {
            Bukkit.getPluginManager().callEvent(synthetic);
        } finally {
            replaying.set(false);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private boolean owns(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }


    static HoldKey key(Player player, PlayerInteractEvent event, Block clicked, ItemStack held) {
        return new HoldKey(clicked.getWorld().getUID(), clicked.getX(), clicked.getY(), clicked.getZ(), event.getBlockFace(),
            event.getHand(), player.getInventory().getHeldItemSlot(), StackCodec.isEmpty(held) ? 0 : fingerprint(held));
    }

    static int fingerprint(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one.hashCode();
    }

    private static void deny(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /** The progress bar on the action bar. */
    static void bar(Player player, double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * BAR_CELLS);
        player.sendActionBar(Component.text()
            .append(Component.text("■".repeat(filled), NamedTextColor.WHITE))
            .append(Component.text("■".repeat(BAR_CELLS - filled), NamedTextColor.DARK_GRAY))
            .build());
    }
}
