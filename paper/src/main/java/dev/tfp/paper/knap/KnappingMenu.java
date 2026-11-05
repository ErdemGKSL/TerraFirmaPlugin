/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/KnappingContainer.java @ c3b4d9536 (onButtonPress, stillValid,
 *       removed, onSlotTake),
 *       src/main/java/net/dries007/tfc/common/container/ItemStackContainer.java @ c3b4d9536 (the target stack in the hand),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (knapping opens on a right-click in the air
 *       with a knappable stack in the main hand),
 *       src/main/java/net/dries007/tfc/client/screen/button/KnappingButton.java @ c3b4d9536 (a pressed button hides and plays
 *       the type's click sound)
 */
package dev.tfp.paper.knap;

import dev.tfp.core.knap.KnappingDefinitions;
import dev.tfp.core.knap.KnappingRecipe;
import dev.tfp.core.knap.KnappingRules;
import dev.tfp.core.knap.KnappingScreen;
import dev.tfp.core.knap.KnappingSession;
import dev.tfp.core.knap.KnappingType;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-PROC-01: one open knapping window ({@link KnappingScreen} layout, {@link KnappingSession} rules). The input stack
 * stays in the player's main hand slot, as in TFC, and is shrunk there; {@link KnappingListener} keeps the player from
 * moving it while the window is open and routes taking the result here.
 *
 * <p>Thread: the player's entity thread (inventory events and button presses already run there).
 */
public final class KnappingMenu {
    /** The {@link GuiSession} state key holding the menu. */
    static final String KEY = "knapping";

    /** What the menu needs from the plugin. */
    public record Services(GuiService gui, Scheduler scheduler, Messages messages, Supplier<KnappingDefinitions> definitions,
            Supplier<TagLookup> tags, Function<ItemStack, Optional<String>> itemIds, Function<ItemStackTemplate, ItemStack> results) {
        public Services {
            Objects.requireNonNull(gui, "gui");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(definitions, "definitions");
            Objects.requireNonNull(tags, "tags");
            Objects.requireNonNull(itemIds, "itemIds");
            Objects.requireNonNull(results, "results");
        }
    }

    private final Services services;
    private final Player player;
    private final int heldSlot;
    private final String inputId;
    private final KnappingType type;
    private final KnappingDefinitions definitions;
    private final TagLookup tags;
    private final KnappingSession rules;
    private final String texture;
    private GuiSession session;

    private KnappingMenu(Services services, Player player, String inputId, KnappingType type, KnappingDefinitions definitions,
            TagLookup tags) {
        this.services = services;
        this.player = player;
        this.heldSlot = player.getInventory().getHeldItemSlot();
        this.inputId = inputId;
        this.type = type;
        this.definitions = definitions;
        this.tags = tags;
        this.rules = new KnappingSession(type, player.getGameMode() == GameMode.CREATIVE);
        this.texture = KnappingScreen.texture(inputId, KnappingScreen.texture(type.icon(), "rock/loose/granite"));
    }

    /**
     * TFC {@code KnappingType.get(mainHand)}: the knapping type of the main hand stack, when its count reaches the type's
     * input count. Empty when the stack cannot be knapped.
     */
    static Optional<KnappingType> typeOf(Services services, ItemStack stack) {
        Optional<String> id = services.itemIds().apply(stack);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return KnappingRules.typeOf(services.definitions().get().types().values(), id.get(), services.tags().get())
            .filter(t -> stack.getAmount() >= t.inputCount());
    }

    /** Opens the knapping window for the main hand stack. Returns the menu, or empty when nothing opened. */
    public static Optional<KnappingMenu> open(Services services, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<KnappingType> type = typeOf(services, hand);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        KnappingMenu menu = new KnappingMenu(services, player, services.itemIds().apply(hand).orElseThrow(), type.get(),
            services.definitions().get(), services.tags().get());
        GuiSession s = services.gui().open(player, menu.build());
        if (s == null) {
            return Optional.empty();
        }
        menu.session = s;
        s.put(KEY, menu);
        return Optional.of(menu);
    }

    /** The menu of an open session, or null. */
    static KnappingMenu of(GuiSession session) {
        return session == null ? null : session.get(KEY, null);
    }

    private TfpGui build() {
        TfpGui.Builder b = TfpGui.builder(KnappingScreen.CHEST, KnappingScreen.layers(texture))
            .label(p -> services.messages().get(p, "gui.knapping.title"))
            .output(KnappingScreen.OUTPUT_SLOT)
            .frames(s -> KnappingScreen.frames(rules.grid(), texture, type.hasOffTexture()))
            .onClose((s, p) -> closed());
        for (int i = 0; i < KnappingRules.GRID * KnappingRules.GRID; i++) {
            int cell = i;
            b.button(KnappingScreen.cellSlot(cell), s -> icon(cell), (s, click) -> press(cell, click));
        }
        return b.build();
    }

    /** A cell's button: a blank button over the glyph art; without CraftEngine a named paper while on, nothing when off. */
    private ItemStack icon(int cell) {
        if (!services.gui().glyphs() && !rules.grid().get(cell)) {
            return null;
        }
        return GuiButtons.icon(services.gui().glyphs(), " ");
    }

    public KnappingSession rules() {
        return rules;
    }

    public KnappingType type() {
        return type;
    }

    public String texture() {
        return texture;
    }

    public GuiSession session() {
        return session;
    }

    public int heldSlot() {
        return heldSlot;
    }

    /** TFC {@code onButtonPress}: a left click on an on cell knaps it, consumes on the first click, updates the output. */
    void press(int cell, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) {
            return; // TFC's buttons answer the left mouse button only
        }
        if (!rules.grid().get(cell) || !stillValid()) {
            return; // TFC hides a pressed button; it cannot be pressed again
        }
        shrink(rules.click(cell));
        // TFC plays the sound event as a UI sound for the clicker; the event ships with the pack sounds (PAR-DATA-08)
        player.playSound(player.getLocation(), type.clickSound(), SoundCategory.PLAYERS, 1f, 1f);
        Inventory inv = session.getInventory();
        inv.setItem(KnappingScreen.OUTPUT_SLOT, result().orElse(null));
        session.refresh();
        closeIfInvalid();
    }

    /** The output for the grid now (TFC {@code KnappingRecipe.get}: type, pattern and the original stack's item). */
    Optional<ItemStack> result() {
        Optional<KnappingRecipe> recipe = KnappingRules.find(definitions.recipesOf(type.id()), type.id(), rules.grid(), inputId, tags);
        return recipe.map(r -> services.results().apply(definitions.resultTemplate(r))).filter(s -> s != null && !s.isEmpty());
    }

    /** TFC {@code onSlotTake}: the grid resets to all off, and a type that consumes after completion consumes now. */
    void taken() {
        shrink(rules.takeResult());
        session.refresh();
        closeIfInvalid();
    }

    /** TFC {@code removed}: a result still on the output goes to the player, and consumes as if taken. */
    private void closed() {
        Inventory inv = session.getInventory();
        ItemStack out = inv.getItem(KnappingScreen.OUTPUT_SLOT);
        boolean present = out != null && !out.isEmpty();
        inv.setItem(KnappingScreen.OUTPUT_SLOT, null);
        if (present) {
            give(player, out);
        }
        shrink(rules.close(present));
    }

    /** TFC {@code placeItemBackInInventory}: into the inventory, the rest dropped at the player. */
    static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** True while the main hand slot still holds the input item. */
    boolean inputPresent() {
        ItemStack held = player.getInventory().getItem(heldSlot);
        return held != null && !held.isEmpty() && services.itemIds().apply(held).map(inputId::equals).orElse(false);
    }

    /** TFC {@code stillValid}. */
    boolean stillValid() {
        return rules.stillValid(inputPresent());
    }

    private void closeIfInvalid() {
        if (!stillValid()) {
            // never close inside the click event; the next tick on the player's thread
            services.scheduler().entityDelayed(player, 1, () -> {
                if (!session.closed() && player.getOpenInventory().getTopInventory().getHolder() == session) {
                    player.closeInventory();
                }
            }, () -> { });
        }
    }

    /** Removes {@code amount} from the input stack in the main hand slot ({@code stack.shrink}). */
    private void shrink(int amount) {
        if (amount <= 0 || !inputPresent()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItem(heldSlot);
        int left = held.getAmount() - amount;
        if (left <= 0) {
            inv.setItem(heldSlot, null);
        } else {
            held.setAmount(left);
            inv.setItem(heldSlot, held);
        }
    }
}
