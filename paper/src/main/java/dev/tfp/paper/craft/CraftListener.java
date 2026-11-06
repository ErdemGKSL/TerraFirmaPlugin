/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import dev.tfp.paper.integration.CustomItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

/**
 * Crafting grids (the inventory's 2x2 and the crafting table's 3x3) on our recipe runtime: TFC's advanced recipes, food
 * combining, casting crafting and the plain recipes CraftEngine does not register, plus the guard against vanilla
 * recipes that would consume our items or that TFC replaces or removes.
 *
 * <p>The server's own match is kept when it is a CraftEngine (or other plugin) recipe, or a vanilla recipe that TFC keeps
 * and that holds none of our items. Otherwise our runtime decides: the result slot shows our output (or nothing), and a
 * click on it is cancelled and performed here (the server has no recipe for it, so its own take would not consume the
 * grid correctly). Everything runs on the player's thread in the event; nothing touches the world but dropping items.
 */
public final class CraftListener implements Listener {
    /** The server's namespace: its recipes are the ones TFC replaces or removes. */
    static final String MINECRAFT = NamespacedKey.MINECRAFT;

    /** At most this many crafts per shift-click or control-drop (a full stack of single outputs). */
    static final int MAX_REPEAT = 64;

    /**
     * Who handles a grid.
     *
     * @param keepServer the server's match (or no match) stands
     * @param match      our match when we handle it; empty with {@code keepServer == false} means no result
     */
    record Decision(boolean keepServer, Optional<CraftingTable.Match> match) {
        static final Decision SERVER = new Decision(true, Optional.empty());
    }

    private final CraftingTable table;
    private final CustomItems items;
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong crafted = new AtomicLong();

    public CraftListener(CraftingTable table, CustomItems items) {
        this.table = Objects.requireNonNull(table, "table");
        this.items = Objects.requireNonNull(items, "items");
    }

    /** Vanilla matches blanked or replaced so far (leakage, TFC overrides and removals). */
    public long blocked() {
        return blocked.get();
    }

    /** Crafts performed by our runtime so far. */
    public long crafted() {
        return crafted.get();
    }

    static int width(CraftingInventory inv) {
        return inv.getMatrix().length == 4 || inv.getType() == InventoryType.CRAFTING ? 2 : 3;
    }

    static List<ItemStack> grid(CraftingInventory inv) {
        return Arrays.asList(inv.getMatrix());
    }

    /** Who handles {@code grid}, given the server's own match ({@code null} for none). */
    Decision decide(Recipe server, List<ItemStack> grid, int width) {
        if (server instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (!MINECRAFT.equals(key.getNamespace())) {
                return Decision.SERVER;
            }
            String id = key.toString();
            boolean replaced = table.disabled(id) || table.hasRecipe(id) || holdsCustomItem(grid);
            if (!replaced) {
                return Decision.SERVER;
            }
            blocked.incrementAndGet();
        } else if (server != null) {
            return Decision.SERVER;
        }
        return new Decision(false, table.find(grid, width));
    }

    private boolean holdsCustomItem(List<ItemStack> grid) {
        for (ItemStack s : grid) {
            if (!CraftingTable.empty(s) && items.idOf(s).isPresent()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        refresh(event.getInventory(), event.getRecipe());
    }

    /** Sets the result slot from our decision (left alone when the server's match stands). */
    void refresh(CraftingInventory inv, Recipe server) {
        Decision d = decide(server, grid(inv), width(inv));
        if (!d.keepServer()) {
            inv.setResult(d.match().map(table::preview).filter(s -> !CraftingTable.empty(s)).orElse(null));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT
                || !(event.getClickedInventory() instanceof CraftingInventory inv)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Decision d = decide(inv.getRecipe(), grid(inv), width(inv));
        if (d.keepServer()) {
            return;
        }
        event.setCancelled(true);
        if (d.match().isEmpty()) {
            inv.setResult(null);
            return;
        }
        switch (event.getClick()) {
            case LEFT, RIGHT -> toCursor(inv, player, d.match().get());
            case SHIFT_LEFT, SHIFT_RIGHT -> repeat(inv, player, d.match().get(), true);
            case NUMBER_KEY -> toHotbar(inv, player, d.match().get(), event.getHotbarButton());
            case DROP -> drop(inv, player, d.match().get(), false);
            case CONTROL_DROP -> drop(inv, player, d.match().get(), true);
            default -> {
                // other clicks on the result slot do nothing, as in vanilla
            }
        }
        refresh(inv, inv.getRecipe());
    }

    /** Left or right click: the output joins the cursor when it fits there. */
    void toCursor(CraftingInventory inv, Player player, CraftingTable.Match match) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack preview = table.preview(match);
        if (CraftingTable.empty(preview) || !fitsOn(cursor, preview)) {
            return;
        }
        CraftingTable.Craft craft = table.craft(match);
        ItemStack out = craft.output();
        if (CraftingTable.empty(out) || !fitsOn(cursor, out)) {
            return;
        }
        if (CraftingTable.empty(cursor)) {
            player.setItemOnCursor(out);
        } else {
            ItemStack grown = cursor.clone();
            grown.setAmount(cursor.getAmount() + out.getAmount());
            player.setItemOnCursor(grown);
        }
        apply(inv, player, craft);
    }

    /** Number key: the output goes to an empty hotbar slot. */
    void toHotbar(CraftingInventory inv, Player player, CraftingTable.Match match, int slot) {
        PlayerInventory pinv = player.getInventory();
        if (slot < 0 || slot > 8 || !CraftingTable.empty(pinv.getItem(slot))) {
            return;
        }
        CraftingTable.Craft craft = table.craft(match);
        if (CraftingTable.empty(craft.output())) {
            return;
        }
        pinv.setItem(slot, craft.output());
        apply(inv, player, craft);
    }

    /** Shift-click: craft while the grid still matches our runtime and the output fits in the inventory. */
    void repeat(CraftingInventory inv, Player player, CraftingTable.Match first, boolean toInventory) {
        CraftingTable.Match match = first;
        for (int n = 0; n < MAX_REPEAT && match != null; n++) {
            ItemStack preview = table.preview(match);
            if (CraftingTable.empty(preview) || (toInventory && !fits(player.getInventory(), preview))) {
                return;
            }
            CraftingTable.Craft craft = table.craft(match);
            if (CraftingTable.empty(craft.output())) {
                return;
            }
            if (toInventory) {
                give(player, craft.output());
            } else {
                player.getWorld().dropItem(player.getEyeLocation(), craft.output());
            }
            apply(inv, player, craft);
            Decision next = decide(inv.getRecipe(), grid(inv), width(inv));
            match = next.keepServer() ? null : next.match().orElse(null);
        }
    }

    /** Q on the result: drop one craft (control: repeat), only with an empty cursor. */
    void drop(CraftingInventory inv, Player player, CraftingTable.Match match, boolean all) {
        if (!CraftingTable.empty(player.getItemOnCursor())) {
            return;
        }
        if (all) {
            repeat(inv, player, match, false);
            return;
        }
        CraftingTable.Craft craft = table.craft(match);
        if (!CraftingTable.empty(craft.output())) {
            player.getWorld().dropItem(player.getEyeLocation(), craft.output());
            apply(inv, player, craft);
        }
    }

    private void apply(CraftingInventory inv, Player player, CraftingTable.Craft craft) {
        ItemStack[] next = consume(grid(inv), craft.remainders(), s -> give(player, s)).toArray(ItemStack[]::new);
        inv.setMatrix(next);
        for (ItemStack extra : craft.extras()) {
            give(player, extra);
        }
        crafted.incrementAndGet();
    }

    /**
     * Vanilla {@code ResultSlot.onTake} on our remainders: each non-empty slot loses one item, then takes its remainder
     * (into the emptied slot, onto an equal stack, else to the player).
     */
    static List<ItemStack> consume(List<ItemStack> grid, List<ItemStack> remainders, Consumer<ItemStack> overflow) {
        List<ItemStack> out = new ArrayList<>(grid.size());
        for (int i = 0; i < grid.size(); i++) {
            ItemStack cur = grid.get(i);
            if (!CraftingTable.empty(cur)) {
                cur = cur.clone();
                cur.setAmount(cur.getAmount() - 1);
                if (cur.getAmount() <= 0) {
                    cur = null;
                }
            } else {
                cur = null;
            }
            ItemStack rem = i < remainders.size() ? remainders.get(i) : null;
            if (!CraftingTable.empty(rem)) {
                if (cur == null) {
                    cur = rem.clone();
                } else if (cur.isSimilar(rem) && cur.getAmount() + rem.getAmount() <= cur.getMaxStackSize()) {
                    cur.setAmount(cur.getAmount() + rem.getAmount());
                } else {
                    overflow.accept(rem.clone());
                }
            }
            out.add(cur);
        }
        return out;
    }

    static boolean fitsOn(ItemStack cursor, ItemStack out) {
        return CraftingTable.empty(cursor)
                || (cursor.isSimilar(out) && cursor.getAmount() + out.getAmount() <= cursor.getMaxStackSize());
    }

    static boolean fits(PlayerInventory inv, ItemStack stack) {
        int left = stack.getAmount();
        for (ItemStack s : inv.getStorageContents()) {
            if (CraftingTable.empty(s)) {
                left -= stack.getMaxStackSize();
            } else if (s.isSimilar(stack)) {
                left -= Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
            if (left <= 0) {
                return true;
            }
        }
        return left <= 0;
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getEyeLocation(), rest);
        }
    }
}
