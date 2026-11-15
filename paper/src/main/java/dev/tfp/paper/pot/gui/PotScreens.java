/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PotContainer.java @ c3b4d9536 (addContainerSlots; moveStack:
 *       fuel to the fuel slots, anything else to the inputs unless the recipe has started),
 * file: src/main/java/net/dries007/tfc/client/screen/PotScreen.java @ c3b4d9536 (renderLabels: "Boiling!", the output's
 *       first tooltip line or "Finished"; the inputs drawn disabled while boiling; the fluid tooltip
 *       fluidUnitsAndCapacityOf; the thermometer tooltip heatTooltipStyle.formatColored(temperature)),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (getSlotStackLimit,
 *       PotInventory.extractItem: no input leaves once the recipe has started)
 */
package dev.tfp.paper.pot.gui;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.pot.PotSim;
import dev.tfp.paper.barrel.gui.BarrelTank;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-HEAT-09: the pot screen (TFC {@code PotScreen} + {@code PotContainer}) on the GUI framework ({@link GuiService}),
 * provided as a module service by {@code PotModule}.
 *
 * <p>Slots, as TFC's {@code isItemValid}: fuel goes only into the top fuel slot (TFC slot 3), anything into the five
 * inputs; fuel slots 0 to 2 are filled by the cascade and can only be taken from. Once the recipe has started (boiling past
 * the pre-boil) the inputs are locked: TFC refuses to take them out and draws them disabled. Shift-click from the player
 * inventory tries the top fuel slot, then the inputs ({@code moveStack}). Every slot holds one item
 * ({@code getSlotStackLimit}); a shift-click, drag or swap that put more in gives the excess back.
 *
 * <p>Three kinds of buttons show what TFC draws: the thermometer column (the temperature), the fluid button (TFC's fluid
 * tooltip over the pot: "{amount} / 1000 mB of {fluid}") and the status button (TFC's label: "Boiling!", the output's
 * "{count}x {item}", or "Finished"). The chest's nine slots are a copy of the pot's; the pot ({@link PotLink}) stays the
 * authority, committed one tick after a click and reloaded every {@link #RELOAD_TICKS} ticks. One viewer per pot, as the
 * firepit and barrel screens.
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class PotScreens {
    /** How often an open screen reloads the pot. */
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "pot.screen";

    /** Why {@link #open} did not open a screen. */
    public enum Refusal {
        BUSY,
        GONE,
        REFUSED
    }

    /** The result of {@link #open}. */
    public record Opened(GuiSession session, Refusal refusal) {
        public boolean ok() {
            return session != null;
        }
    }

    static final class Screen {
        final PotLink link;
        final Object key;
        PotSnapshot pot;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(PotLink link, Object key, PotSnapshot pot) {
            this.link = link;
            this.key = key;
            this.pot = pot;
        }

        float temperature() {
            return pot == null ? 0f : pot.temperature();
        }

        int boilingTicks() {
            return pot == null ? 0 : pot.boilingTicks();
        }

        boolean started() {
            return pot != null && pot.started();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public PotScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Opens the screen of pot {@code key} (normally the block position) for {@code viewer}, titled {@code label}. */
    public Opened open(Player viewer, Object key, PotLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<PotSnapshot> pot = link.snapshot();
        if (pot.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, pot.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, pot.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    /** The open screen of pot {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before the device changes pot {@code key}'s slots by another path: commits the open copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads pot {@code key} into its open screen (if any). Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    /** The pot last shown in {@code session}. */
    public static Optional<PotSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.pot);
    }

    public static boolean isPot(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        PotLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(PotScreenLayout.CHEST, PotScreenLayout.LAYERS).label(label);
        // shift-click order: SLOT_FUEL_INPUT, then the inputs (PotContainer.moveStack)
        b.input(PotScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_INPUT), (s, chestSlot, stack) -> !GuiButtons.isButton(stack)
            && roomFor(s, chestSlot) && link.isItemValid(FirepitSim.SLOT_FUEL_INPUT, stack));
        for (int tfc = PotSim.SLOT_INPUT_START; tfc <= PotSim.SLOT_INPUT_END; tfc++) {
            int t = tfc;
            b.input(PotScreenLayout.chestSlot(tfc), (s, chestSlot, stack) -> !GuiButtons.isButton(stack) && !screen.started()
                && roomFor(s, chestSlot) && link.isItemValid(t, stack));
        }
        for (int tfc = FirepitSim.SLOT_FUEL_CONSUME; tfc < FirepitSim.SLOT_FUEL_INPUT; tfc++) {
            b.output(PotScreenLayout.chestSlot(tfc));
        }
        for (int slot : PotScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        b.button(PotScreenLayout.FLUID_SLOT, s -> fluidIcon(s.viewer(), screen), (s, click) -> { });
        b.button(PotScreenLayout.STATUS_SLOT, s -> statusIcon(s.viewer(), screen), (s, click) -> { });
        return b.frames(s -> PotScreenLayout.frames(screen.temperature(), screen.boilingTicks()))
            .lockedWhen(s -> screen.pot == null)
            .onClose(this::closed)
            .build();
    }

    /** A one-item slot already holding its item takes nothing more. */
    private static boolean roomFor(GuiSession session, int chestSlot) {
        ItemStack there = session.getInventory().getItem(chestSlot);
        int tfc = PotScreenLayout.tfcSlot(chestSlot);
        return there == null || there.isEmpty() || there.getAmount() < PotScreenLayout.limit(tfc);
    }

    /** TFC's thermometer tooltip: the temperature in the heat tooltip style, nothing when cold. */
    ItemStack thermometerIcon(Player viewer, Screen screen) {
        Optional<TextComponent> text = style.formatColored(screen.temperature(), Messages.code(viewer), messages::get);
        return icon(text.map(t -> (Component) t));
    }

    /** TFC's fluid tooltip over the pot: {@code fluidUnitsAndCapacityOf}; nothing for an empty tank. */
    ItemStack fluidIcon(Player viewer, Screen screen) {
        if (screen.pot == null) {
            return icon(Optional.empty());
        }
        // TFC fluidUnitsAndCapacityOf: "{amount} / {capacity} mB of {fluid}"; the capacity is put in here, the amount
        // and the fluid name by BarrelTank.tooltip (the {0} and {1} of the formatted pattern)
        return icon(BarrelTank.tooltip(screen.pot.fluid(), messages.get(Messages.code(viewer), "gui.pot.fluid",
            "{0}", String.valueOf(screen.pot.capacity()), "{1}")));
    }

    /** TFC's label: "Boiling!" while boiling, else the output's "{count}x {item}" line, else nothing. */
    ItemStack statusIcon(Player viewer, Screen screen) {
        return icon(status(viewer, screen.pot));
    }

    Optional<Component> status(Player viewer, PotSnapshot pot) {
        if (pot == null) {
            return Optional.empty();
        }
        String locale = Messages.code(viewer);
        if (pot.boiling()) {
            return Optional.of(Component.text(messages.get(locale, "gui.pot.boiling")));
        }
        ItemStack out = pot.output();
        if (!out.isEmpty()) {
            // BlockEntityTooltips.itemWithCount: "{count}x {item name}"
            return Optional.of(Component.text(out.getAmount() + "x ").append(out.effectiveName()));
        }
        return Optional.empty();
    }

    private ItemStack icon(Optional<? extends Component> text) {
        ItemStack icon = GuiButtons.icon(gui.glyphs(), "");
        ItemMeta meta = icon.getItemMeta();
        if (text.isPresent()) {
            meta.customName(text.get().colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.setHideTooltip(true);
        }
        icon.setItemMeta(meta);
        return icon;
    }

    // ---- sync -----------------------------------------------------------------------------------------------

    /** A click or drag happened in {@code session}'s window: commit the copy next tick (once per tick). */
    void touched(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.pending) {
            return;
        }
        screen.pending = true;
        boolean queued = scheduler.entityDelayed(session.viewer(), 1, () -> {
            screen.pending = false;
            flush(session);
        }, () -> screen.pending = false);
        if (!queued) {
            screen.pending = false;
        }
    }

    /** Gives back what a shift-click, drag or swap put above a slot's limit. Returns whether anything moved. */
    static boolean clamp(GuiSession session) {
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        boolean moved = false;
        for (int tfc = 0; tfc < PotSim.SLOTS; tfc++) {
            int slot = PotScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            int limit = PotScreenLayout.limit(tfc);
            if (there == null || there.getAmount() <= limit) {
                continue;
            }
            ItemStack excess = there.clone();
            excess.setAmount(there.getAmount() - limit);
            there.setAmount(limit);
            inv.setItem(slot, there);
            give(viewer, excess);
            moved = true;
        }
        return moved;
    }

    /** Commits the chest's copy when it differs from the pot's last known slots. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.pot == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (PotSnapshot.sameSlots(now, screen.pot.slots())) {
            return;
        }
        Optional<PotSnapshot> after = screen.link.commit(now);
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
        }
    }

    void reload(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed()) {
            return;
        }
        flush(session);
        if (session.closed() || screen.pot == null) {
            return;
        }
        Optional<PotSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.pot)) {
            apply(session, screen, now.get());
        }
    }

    /** Shows {@code pot}: the nine slots, then the title (thermometer and bubble frames) and the button tooltips. */
    private static void apply(GuiSession session, Screen screen, PotSnapshot pot) {
        screen.pot = pot;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < PotSim.SLOTS; tfc++) {
            ItemStack s = pot.slot(tfc);
            inv.setItem(PotScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The pot is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private static void gone(GuiSession session, Screen screen) {
        PotSnapshot last = screen.pot;
        screen.pot = null; // locks every slot until the screen is closed
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < PotSim.SLOTS; tfc++) {
            int slot = PotScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slot(tfc);
            inv.setItem(slot, null);
            if (there != null && !there.isEmpty() && !there.equals(known)) {
                give(viewer, there);
            }
        }
        if (!session.closed()) {
            viewer.closeInventory();
        }
    }

    private void closed(GuiSession session, Player viewer) {
        Screen screen = state(session);
        if (screen == null) {
            return;
        }
        if (screen.reloader != null) {
            screen.reloader.cancel();
        }
        if (screen.pot != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!PotSnapshot.sameSlots(now, screen.pot.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : PotScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the pot holds them; the closed copy must not
        }
        open.remove(screen.key, session);
        screen.link.closed(viewer);
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(PotSim.SLOTS);
        for (int tfc = 0; tfc < PotSim.SLOTS; tfc++) {
            ItemStack s = inv.getItem(PotScreenLayout.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }

    static void give(Player viewer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : viewer.getInventory().addItem(stack.clone()).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), rest);
        }
    }
}
