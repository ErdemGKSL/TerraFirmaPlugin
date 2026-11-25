/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (isItemValid, countItem,
 *       removed, stillValid, onButtonPress sounds, onSlotTake),
 *       src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java @ c3b4d9536 (the material counters)
 */
package dev.tfp.paper.sewing;

import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.craft.SewingRules;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.sewing.SewingScreen;
import dev.tfp.core.sewing.SewingSession;
import dev.tfp.core.sewing.SewingSession.ItemKind;
import dev.tfp.core.sewing.SewingSession.Mode;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-PROC-07: one open sewing table ({@link SewingScreen} layout, {@link SewingSession} rules). The cloth, yarn and
 * needle stay in the chest's item slots; the grid slots are buttons whose icons show the board in the current view.
 *
 * <p>Thread: the player's entity thread (inventory events and button presses already run there).
 */
public final class SewingMenu {
    /** The {@link GuiSession} state key holding the menu. */
    static final String KEY = "sewing";
    /** TFC {@code stillValid}: the player stays within 8 blocks. */
    static final double MAX_DISTANCE_SQUARED = 64;

    /** A sewing recipe and the stack it gives. */
    public record Entry(SewingRecipe recipe, ItemStackTemplate result) {
    }

    /** What the menu needs from the plugin. */
    public record Services(GuiService gui, Scheduler scheduler, Messages messages, Supplier<List<Entry>> recipes,
            Function<ItemStack, ItemKind> kinds, Function<ItemStackTemplate, ItemStack> results, Predicate<Block> isTable) {
        public Services {
            Objects.requireNonNull(gui, "gui");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(kinds, "kinds");
            Objects.requireNonNull(results, "results");
            Objects.requireNonNull(isTable, "isTable");
        }
    }

    private final Services services;
    private final Player player;
    private final Block table;
    private final Map<String, ItemStackTemplate> results = new java.util.HashMap<>();
    private final SewingSession rules;
    private GuiSession session;

    private SewingMenu(Services services, Player player, Block table, List<Entry> entries) {
        this.services = services;
        this.player = player;
        this.table = table;
        List<SewingRecipe> recipes = new ArrayList<>();
        for (Entry e : entries) {
            recipes.add(e.recipe());
            results.put(e.recipe().id(), e.result());
        }
        this.rules = new SewingSession(recipes);
    }

    /** Opens the sewing table screen. Returns the menu, or empty when nothing opened. */
    public static Optional<SewingMenu> open(Services services, Player player, Block table) {
        SewingMenu menu = new SewingMenu(services, player, table, services.recipes().get());
        GuiSession s = services.gui().open(player, menu.build());
        if (s == null) {
            return Optional.empty();
        }
        menu.session = s;
        s.put(KEY, menu);
        return Optional.of(menu);
    }

    /** The menu of an open session, or null. */
    static SewingMenu of(GuiSession session) {
        return session == null ? null : session.get(KEY, null);
    }

    public SewingSession rules() {
        return rules;
    }

    public GuiSession session() {
        return session;
    }

    private TfpGui build() {
        TfpGui.Builder b = TfpGui.builder(SewingScreen.CHEST, new GuiLayers(null, List.of()))
            .label(p -> services.messages().get(p, "gui.sewing.title"))
            .input(SewingScreen.SLOT_YARN, (s, slot, st) -> services.kinds().apply(st) == ItemKind.STRING)
            .input(SewingScreen.SLOT_TOOL, (s, slot, st) -> services.kinds().apply(st) == ItemKind.NEEDLE)
            .input(SewingScreen.SLOT_INPUT_1, (s, slot, st) -> isCloth(st))
            .input(SewingScreen.SLOT_INPUT_2, (s, slot, st) -> isCloth(st))
            .output(SewingScreen.SLOT_RESULT)
            .onClose((s, p) -> closed());
        for (int slot = 0; slot < SewingScreen.STITCH_COLUMNS * SewingScreen.STITCH_ROWS; slot++) {
            int cell = slot;
            b.button(slot, s -> cellIcon(cell), (s, click) -> pressCell(cell, click));
        }
        b.button(SewingScreen.BUTTON_BURLAP, s -> materialIcon(Mode.BURLAP, s), (s, click) -> select(Mode.BURLAP, click));
        b.button(SewingScreen.BUTTON_WOOL, s -> materialIcon(Mode.WOOL, s), (s, click) -> select(Mode.WOOL, click));
        b.button(SewingScreen.BUTTON_REMOVE, s -> materialIcon(Mode.REMOVE, s), (s, click) -> select(Mode.REMOVE, click));
        b.button(SewingScreen.BUTTON_NEEDLE, s -> materialIcon(Mode.NEEDLE, s), (s, click) -> select(Mode.NEEDLE, click));
        return b.build();
    }

    /** TFC {@code isItemValid} for the two input slots: anything but yarn and needles (TFC checks the tags the same way). */
    private boolean isCloth(ItemStack stack) {
        ItemKind kind = services.kinds().apply(stack);
        return kind != ItemKind.STRING && kind != ItemKind.NEEDLE;
    }

    // ----- supply -----

    /** TFC {@code countItem} and the slot counts: the items in the table's slots as {@link SewingRules.Supply}. */
    SewingRules.Supply supply() {
        return supply(session.getInventory());
    }

    private SewingRules.Supply supply(Inventory inv) {
        int wool = 0;
        int burlap = 0;
        for (int slot : new int[] {SewingScreen.SLOT_INPUT_1, SewingScreen.SLOT_INPUT_2}) {
            ItemStack stack = inv.getItem(slot);
            if (isEmpty(stack)) {
                continue;
            }
            switch (services.kinds().apply(stack)) {
                case WOOL -> wool += stack.getAmount();
                case BURLAP -> burlap += stack.getAmount();
                default -> { }
            }
        }
        ItemStack yarn = inv.getItem(SewingScreen.SLOT_YARN);
        ItemStack tool = inv.getItem(SewingScreen.SLOT_TOOL);
        return new SewingRules.Supply(wool, burlap, isEmpty(yarn) ? 0 : yarn.getAmount(), !isEmpty(tool));
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /** TFC {@code SewingInputSlot.mayPickup}: may {@code stack} be taken out of its slot now. */
    boolean mayTake(ItemStack stack) {
        return isEmpty(stack) || rules.mayTake(services.kinds().apply(stack));
    }

    // ----- icons -----

    private ItemStack cellIcon(int slot) {
        if (rules.view() == SewingSession.View.SQUARES) {
            int square = SewingScreen.squareAt(slot);
            if (square < 0) {
                return null;
            }
            return switch (rules.board().squares().charAt(square)) {
                case 'B' -> named(Material.BROWN_WOOL, "gui.sewing.burlap_square", false);
                case 'W' -> named(Material.WHITE_WOOL, "gui.sewing.wool_square", false);
                default -> blank(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            };
        }
        int stitch = SewingScreen.stitchAt(slot);
        if (stitch < 0) {
            return null;
        }
        return rules.board().stitch(stitch) ? named(Material.STRING, "gui.sewing.stitch_placed", false)
            : blank(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack blank(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        GuiButtons.style(meta, false, " ");
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack named(Material material, String key, boolean glint, Object... args) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        GuiButtons.style(meta, false, services.messages().get(player, key, args));
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack materialIcon(Mode mode, GuiSession from) {
        SewingRules.Supply supply = supply(from.getInventory());
        SewingRules.Board board = rules.board();
        boolean selected = rules.mode() == mode;
        ItemStack stack = switch (mode) {
            case BURLAP -> named(Material.BROWN_WOOL, "gui.sewing.dark_cloth", selected,
                Math.min(99, SewingRules.squaresLeft(board, supply, SewingRules.Material.BURLAP)));
            case WOOL -> named(Material.WHITE_WOOL, "gui.sewing.light_cloth", selected,
                Math.min(99, SewingRules.squaresLeft(board, supply, SewingRules.Material.WOOL)));
            case REMOVE -> named(Material.RED_DYE, rules.view() == SewingSession.View.SQUARES
                ? "gui.sewing.remove_cloth" : "gui.sewing.remove_stitch", selected);
            default -> named(Material.SHEARS, "gui.sewing.stitch", selected, Math.min(99, SewingRules.stitchesLeft(board, supply)));
        };
        if (mode == Mode.REMOVE) {
            ItemMeta meta = stack.getItemMeta();
            meta.lore(List.of(Component.text(services.messages().get(player, "gui.sewing.remove_hint"), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ----- presses -----

    private void select(Mode mode, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) {
            return;
        }
        if (!stillValid()) {
            closeNextTick();
            return;
        }
        rules.select(mode);
        session.refresh();
    }

    /** TFC {@code onButtonPress} for a square or a stitch. */
    void pressCell(int slot, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) {
            return;
        }
        if (!stillValid()) {
            closeNextTick();
            return;
        }
        SewingSession.Outcome outcome = rules.press(slot, supply());
        switch (outcome) {
            case NONE -> {
                return;
            }
            case PLACED_BURLAP, PLACED_WOOL -> sound(Sound.BLOCK_WOOL_PLACE);
            case REMOVED_SQUARE -> sound(Sound.BLOCK_WOOL_BREAK);
            case STITCHED, UNSTITCHED -> sound(Sound.BLOCK_WOOL_HIT);
        }
        update();
    }

    /** TFC {@code updateResultItem} and the refresh of the icons: call after anything the grid or the supply reads changed. */
    void update() {
        Optional<SewingRecipe> match = rules.update();
        Inventory inv = session.getInventory();
        ItemStack current = inv.getItem(SewingScreen.SLOT_RESULT);
        if (match.isPresent()) {
            ItemStack result = services.results().apply(results.get(match.get().id()));
            if (result != null && !result.isEmpty() && (isEmpty(current) || !current.isSimilar(result))) {
                inv.setItem(SewingScreen.SLOT_RESULT, result);
            }
        } else if (!isEmpty(current)) {
            inv.setItem(SewingScreen.SLOT_RESULT, null);
        }
        session.refresh();
    }

    /** Runs {@link #update} on the player's thread once the click that moved items has been applied. */
    void updateNextTick() {
        services.scheduler().entityDelayed(player, 1, () -> {
            if (!session.closed()) {
                update();
            }
        }, () -> { });
    }

    /**
     * TFC {@code onSlotTake} for the result: the cloth and yarn that were used are removed from the slots, the needle
     * takes one point of wear, the board resets.
     */
    void taken() {
        SewingRules.Consumption used = rules.take();
        Inventory inv = session.getInventory();
        shrink(inv, ItemKind.WOOL, used.wool());
        shrink(inv, ItemKind.BURLAP, used.burlap());
        ItemStack yarn = inv.getItem(SewingScreen.SLOT_YARN);
        if (!isEmpty(yarn)) {
            int left = yarn.getAmount() - used.yarn();
            inv.setItem(SewingScreen.SLOT_YARN, left <= 0 ? null : withAmount(yarn, left));
        }
        damageTool(inv);
        sound(Sound.UI_LOOM_TAKE_RESULT);
        update();
    }

    /** TFC takes from the first input slot, then the second. */
    private void shrink(Inventory inv, ItemKind kind, int amount) {
        int remaining = amount;
        for (int slot : new int[] {SewingScreen.SLOT_INPUT_1, SewingScreen.SLOT_INPUT_2}) {
            ItemStack stack = inv.getItem(slot);
            if (remaining <= 0 || isEmpty(stack) || services.kinds().apply(stack) != kind) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            inv.setItem(slot, stack.getAmount() - take <= 0 ? null : withAmount(stack, stack.getAmount() - take));
        }
    }

    private static ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    /** TFC {@code Helpers.damageItem}: one point of wear; the needle breaks at the end of its durability. */
    private void damageTool(Inventory inv) {
        ItemStack tool = inv.getItem(SewingScreen.SLOT_TOOL);
        if (isEmpty(tool) || !(tool.getItemMeta() instanceof Damageable d) || !d.hasMaxDamage() && tool.getType().getMaxDurability() <= 0) {
            return;
        }
        int max = d.hasMaxDamage() ? d.getMaxDamage() : tool.getType().getMaxDurability();
        if (d.getDamage() + 1 >= max) {
            inv.setItem(SewingScreen.SLOT_TOOL, null);
            sound(Sound.ENTITY_ITEM_BREAK);
        } else {
            d.setDamage(d.getDamage() + 1);
            tool.setItemMeta(d);
            inv.setItem(SewingScreen.SLOT_TOOL, tool);
        }
    }

    private void sound(Sound sound) {
        table.getWorld().playSound(center(), sound, SoundCategory.BLOCKS, 1f, 1f);
    }

    /** TFC {@code removed}: the items in the slots go back to the player (the result is not given). */
    private void closed() {
        Inventory inv = session.getInventory();
        for (int slot : new int[] {SewingScreen.SLOT_YARN, SewingScreen.SLOT_TOOL, SewingScreen.SLOT_INPUT_1, SewingScreen.SLOT_INPUT_2}) {
            ItemStack stack = inv.getItem(slot);
            if (!isEmpty(stack)) {
                give(player, stack);
            }
            inv.setItem(slot, null);
        }
        inv.setItem(SewingScreen.SLOT_RESULT, null);
    }

    /** TFC {@code giveItemStackToPlayerOrDrop}. */
    static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** TFC {@code stillValid}: still a sewing table, and the player within 8 blocks. */
    boolean stillValid() {
        return player.getGameMode() != GameMode.SPECTATOR && services.isTable().test(table)
            && player.getLocation().getWorld() == table.getWorld()
            && player.getLocation().distanceSquared(center()) < MAX_DISTANCE_SQUARED;
    }

    private Location center() {
        return new Location(table.getWorld(), table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5);
    }

    void closeNextTick() {
        services.scheduler().entityDelayed(player, 1, () -> {
            if (!session.closed() && player.getOpenInventory().getTopInventory().getHolder() == session) {
                player.closeInventory();
            }
        }, () -> { });
    }
}
