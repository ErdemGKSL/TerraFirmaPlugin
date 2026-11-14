/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SaladContainer.java @ c3b4d9536 (init: the bowl stack moves
 *       from the main hand, else the off hand, into the bowl slot; isItemValid; setAndUpdateSlots; onSlotTake; removed:
 *       inputs and bowls go back to the player, the salad does not; quickMoveStack: the salad slot cannot be
 *       shift-clicked)
 */
package dev.tfp.paper.salad;

import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.salad.SaladMix;
import dev.tfp.core.salad.SaladScreen;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-PROC-14: one open salad screen ({@link SaladScreen} layout, {@link SaladMix} rules). The salad in the output slot
 * is recomputed after every change of the inputs or the bowls; taking it shrinks every input and the bowls by the amount
 * taken. Closing gives the inputs and the bowls back; a salad still in the output is not made (TFC skips it).
 *
 * <p>Thread: the player's entity thread (inventory events already run there).
 */
public final class SaladMenu {
    /** The {@link GuiSession} state key holding the menu. */
    static final String KEY = "salad";
    /** TFC {@code TFCTags.Items.SALAD_BOWLS}. */
    public static final String SALAD_BOWLS = "tfp:salad_bowls";
    /** TFC {@code TFCTags.Items.USABLE_IN_SALAD}. */
    public static final String USABLE_IN_SALAD = "tfp:usable_in_salad";

    /** What the menu needs from the plugin. */
    public record Services(GuiService gui, Scheduler scheduler, Messages messages, Predicate<ItemStack> bowl,
            Predicate<ItemStack> ingredient, Function<ItemStack, Optional<FoodData>> food, Predicate<ItemStack> rotten,
            Function<ItemStack, Optional<String>> itemIds, BiFunction<String, Integer, Optional<ItemStack>> items,
            Consumer<ItemStack> stamp) {
        public Services {
            Objects.requireNonNull(gui, "gui");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(bowl, "bowl");
            Objects.requireNonNull(ingredient, "ingredient");
            Objects.requireNonNull(food, "food");
            Objects.requireNonNull(rotten, "rotten");
            Objects.requireNonNull(itemIds, "itemIds");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(stamp, "stamp");
        }
    }

    private final Services services;
    private final Player player;
    private GuiSession session;
    private boolean updateQueued;

    private SaladMenu(Services services, Player player) {
        this.services = services;
        this.player = player;
    }

    /**
     * Opens the salad screen for {@code player} and moves the bowl stack in the main hand, else the off hand, into the
     * bowl slot (TFC {@code init}). Empty when the server refused the screen.
     */
    public static Optional<SaladMenu> open(Services services, Player player) {
        SaladMenu menu = new SaladMenu(services, player);
        GuiSession s = services.gui().open(player, menu.build());
        if (s == null) {
            return Optional.empty();
        }
        menu.session = s;
        s.put(KEY, menu);
        menu.fillDecoration();
        PlayerInventory inv = player.getInventory();
        for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            ItemStack held = inv.getItem(hand);
            if (!held.isEmpty() && services.bowl().test(held)) {
                s.getInventory().setItem(SaladScreen.BOWL_CHEST_SLOT, held.clone());
                inv.setItem(hand, null);
                break;
            }
        }
        return Optional.of(menu);
    }

    /** The menu of an open session, or null. */
    static SaladMenu of(GuiSession session) {
        return session == null ? null : session.get(KEY, null);
    }

    public GuiSession session() {
        return session;
    }

    private TfpGui build() {
        TfpGui.Builder b = TfpGui.builder(SaladScreen.CHEST, SaladScreen.LAYERS)
            .label(p -> services.messages().get(p, "gui.salad.title"));
        for (int i = SaladScreen.SLOT_INPUT_START; i <= SaladScreen.SLOT_INPUT_END; i++) {
            b.input(SaladScreen.chestSlot(i), (s, slot, stack) -> services.ingredient().test(stack));
        }
        b.input(SaladScreen.BOWL_CHEST_SLOT, (s, slot, stack) -> services.bowl().test(stack));
        return b.output(SaladScreen.OUTPUT_CHEST_SLOT)
            .onClose((s, p) -> closed())
            .build();
    }

    /**
     * Until the salad art exists (PAR-PROC-36) the screen is a plain chest: the slots that are not TFC slots hold a
     * nameless grey pane so the seven slots stand out. Clicks on them are refused by the GUI framework.
     */
    private void fillDecoration() {
        Inventory inv = session.getInventory();
        for (int slot = 0; slot < SaladScreen.CHEST.size(); slot++) {
            if (SaladScreen.tfcSlot(slot) < 0) {
                inv.setItem(slot, filler());
            }
        }
    }

    static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack at(int tfcSlot) {
        ItemStack stack = session.getInventory().getItem(SaladScreen.chestSlot(tfcSlot));
        return stack == null || stack.isEmpty() ? null : stack;
    }

    /** The salad for the slots now (TFC {@code setAndUpdateSlots}), sized and stamped; empty when none. */
    Optional<ItemStack> salad() {
        ItemStack bowls = at(SaladScreen.SLOT_BOWLS);
        if (bowls == null) {
            return Optional.empty();
        }
        List<SaladMix.Ingredient> ingredients = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = SaladScreen.SLOT_INPUT_START; i <= SaladScreen.SLOT_INPUT_END; i++) {
            ItemStack stack = at(i);
            if (stack == null) {
                continue;
            }
            Optional<FoodData> food = services.food().apply(stack);
            if (food.isEmpty()) {
                continue; // TFC: FoodCapability.get is null, the slot is skipped
            }
            ingredients.add(new SaladMix.Ingredient(i, food.get(), services.rotten().test(stack), stack.getAmount()));
            ids.add(services.itemIds().apply(stack).orElse(stack.getType().getKey().toString()));
        }
        Optional<SaladMix.Salad> mix = SaladMix.mix(ingredients, bowls.getAmount());
        if (mix.isEmpty()) {
            return Optional.empty();
        }
        SaladMix.Salad salad = mix.get();
        Optional<ItemStack> made = services.items().apply(salad.itemId(), 1);
        if (made.isEmpty() || made.get().isEmpty()) {
            return Optional.empty();
        }
        ItemStack out = made.get();
        // TFC makes a stack of the smallest count even when it exceeds the salad's stack size; a Bukkit stack keeps it
        // within the item's maximum (divergence row).
        out.setAmount(Math.min(salad.count(), out.getMaxStackSize()));
        String bowlId = services.itemIds().apply(bowls).orElse(bowls.getType().getKey().toString());
        SaladPdc.write(out, salad.food(), ids, bowlId);
        services.stamp().accept(out);
        return Optional.of(out);
    }

    /** Recomputes the output slot now. */
    void update() {
        if (session.closed()) {
            return;
        }
        session.getInventory().setItem(SaladScreen.OUTPUT_CHEST_SLOT, salad().orElse(null));
    }

    /** Recomputes the output on the next tick, after the click that changed a slot was applied. */
    void queueUpdate() {
        if (updateQueued) {
            return;
        }
        updateQueued = true;
        services.scheduler().entityDelayed(player, 1, () -> {
            updateQueued = false;
            update();
        }, () -> updateQueued = false);
    }

    /**
     * TFC {@code onSlotTake}: after {@code taken} salads left the output, every input and the bowls shrink by that
     * amount, and the output is recomputed.
     */
    void taken(int taken) {
        Inventory inv = session.getInventory();
        for (int i = SaladScreen.SLOT_INPUT_START; i <= SaladScreen.SLOT_BOWLS; i++) {
            int chest = SaladScreen.chestSlot(i);
            ItemStack stack = inv.getItem(chest);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int left = SaladMix.afterTake(stack.getAmount(), taken);
            if (left == 0) {
                inv.setItem(chest, null);
            } else {
                stack.setAmount(left);
                inv.setItem(chest, stack);
            }
        }
        update();
    }

    /** TFC {@code removed}: the inputs and bowls go back to the player (the rest dropped); the salad is not made. */
    private void closed() {
        Inventory inv = session.getInventory();
        inv.setItem(SaladScreen.OUTPUT_CHEST_SLOT, null);
        for (int i = SaladScreen.SLOT_INPUT_START; i <= SaladScreen.SLOT_BOWLS; i++) {
            int chest = SaladScreen.chestSlot(i);
            ItemStack stack = inv.getItem(chest);
            inv.setItem(chest, null);
            if (stack != null && !stack.isEmpty()) {
                give(player, stack);
            }
        }
        for (int slot = 0; slot < SaladScreen.CHEST.size(); slot++) {
            if (SaladScreen.tfcSlot(slot) < 0) {
                inv.setItem(slot, null);
            }
        }
    }

    /** TFC {@code giveItemStackToPlayerOrDrop}. */
    static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }
}
