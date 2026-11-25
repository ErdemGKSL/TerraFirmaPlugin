/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/EmptyPanItem.java @ c3b4d9536 (useOn),
 *       src/main/java/net/dries007/tfc/common/items/PanItem.java @ c3b4d9536 (use, onUseTick, finishUsingItem)
 */
package dev.tfp.paper.pan;

import dev.tfp.core.pan.PanDeposits;
import dev.tfp.core.pan.PanRules;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-PROC-12: panning on vanilla clients.
 *
 * <p><b>Empty pan</b> ({@code tfp:pan/empty}): a right click on a deposit block removes the block without drops, takes one
 * pan (not in creative) and hands out a filled pan that remembers the deposit item (PDC {@link #DEPOSIT_KEY} and
 * {@code custom_model_data} string 0, the model key). <b>Filled pan</b> ({@code tfp:pan/filled}): {@link PanRules#start}
 * decides the right click (off hand passes, no water refuses with the hint); the {@code consumable} component runs the
 * {@link PanRules#USE_TICKS} use, a repeating entity task plays the every-16-ticks sound while the hand stays raised, and
 * the finish ({@link PlayerItemConsumeEvent}, cancelled) puts an empty pan in the hand and rolls the deposit's loot table
 * (pure, off the region) and gives the stacks back on the player's thread.
 */
public final class PanListener implements Listener {
    public static final String EMPTY = "tfp:pan/empty";
    public static final String FILLED = "tfp:pan/filled";
    public static final NamespacedKey DEPOSIT_KEY = new NamespacedKey("tfp", "pan_deposit");
    public static final String WATER_MESSAGE = "pan.water";

    /** What the listener needs from the server, so tests can fake it. */
    public interface Env {
        Optional<String> itemId(ItemStack stack);

        Optional<ItemStack> create(String id);

        Optional<String> blockId(Block block);

        void removeBlock(Block block);

        boolean inInfiniteWater(Player player);

        void sound(Player player);

        void message(Player player, String key);

        /** Rolls {@code tableId} off the region thread and completes with the stacks. */
        CompletableFuture<List<ItemStack>> roll(Player player, String tableId, ItemStack pan);
    }

    private final Env env;
    private final Supplier<PanDeposits> deposits;
    private final Scheduler scheduler;

    public PanListener(Env env, Supplier<PanDeposits> deposits, Scheduler scheduler) {
        this.env = Objects.requireNonNull(env, "env");
        this.deposits = Objects.requireNonNull(deposits, "deposits");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private boolean is(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && env.itemId(stack).filter(id::equals).isPresent();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY || event.getHand() == null) {
            return;
        }
        Action action = event.getAction();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (action == Action.RIGHT_CLICK_BLOCK && is(item, EMPTY)) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && pickUp(player, event.getHand(), clicked, item)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && is(item, FILLED)) {
            switch (PanRules.start(event.getHand() == EquipmentSlot.OFF_HAND, env.inInfiniteWater(player))) {
                case PASS -> event.setUseItemInHand(Event.Result.DENY);
                case NEEDS_WATER -> {
                    event.setUseItemInHand(Event.Result.DENY);
                    env.message(player, WATER_MESSAGE);
                }
                case STARTED -> startSounds(player);
            }
        }
    }

    /** TFC {@code EmptyPanItem.useOn}. Block's region thread. */
    boolean pickUp(Player player, EquipmentSlot hand, Block clicked, ItemStack pan) {
        Optional<String> id = env.blockId(clicked);
        if (id.isEmpty() || !PanRules.canFill(deposits.get().isDeposit(id.get()))) {
            return false;
        }
        Optional<ItemStack> filled = env.create(FILLED);
        if (filled.isEmpty()) {
            return false;
        }
        env.removeBlock(clicked);
        if (player.getGameMode() != GameMode.CREATIVE) {
            pan.setAmount(pan.getAmount() - 1);
            player.getInventory().setItem(hand, pan.getAmount() <= 0 ? null : pan);
        }
        give(player, fill(filled.get(), id.get()));
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    /** The filled pan holding {@code depositItemId}: PDC and the model key in {@code custom_model_data} string 0. */
    static ItemStack fill(ItemStack pan, String depositItemId) {
        ItemMeta meta = pan.getItemMeta();
        meta.getPersistentDataContainer().set(DEPOSIT_KEY, PersistentDataType.STRING, depositItemId);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(PanDeposits.modelKey(depositItemId)));
        meta.setCustomModelDataComponent(cmd);
        pan.setItemMeta(meta);
        return pan;
    }

    /** The deposit item id a filled pan holds. */
    public static Optional<String> deposit(ItemStack pan) {
        if (pan == null || !pan.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pan.getItemMeta().getPersistentDataContainer().get(DEPOSIT_KEY, PersistentDataType.STRING));
    }

    /** TFC {@code onUseTick}: the sound every 16 remaining ticks while the hand stays raised. */
    private void startSounds(Player player) {
        int[] elapsed = {0};
        Scheduler.TaskHandle[] handle = new Scheduler.TaskHandle[1];
        handle[0] = scheduler.entityRepeating(player, 1, 1, () -> {
            elapsed[0]++;
            if (elapsed[0] >= PanRules.USE_TICKS || !player.isOnline() || !player.isHandRaised()
                    || !is(player.getInventory().getItemInMainHand(), FILLED)) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            if (PanDeposits.soundDue(elapsed[0])) {
                env.sound(player);
            }
        }, () -> { });
    }

    /** TFC {@code finishUsingItem}: roll the deposit's table, hand the pan back empty. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!is(item, FILLED)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<String> table = deposit(item).flatMap(deposits.get()::lootTable);
        if (!PanRules.yieldsLoot(table.isPresent())) {
            return;
        }
        Optional<ItemStack> empty = env.create(EMPTY);
        if (empty.isEmpty()) {
            return;
        }
        player.getInventory().setItem(event.getHand(), empty.get());
        env.roll(player, table.get(), item).thenAccept(stacks -> scheduler.entity(player, () -> stacks.forEach(s -> give(player, s)),
            () -> { }));
    }
}
