/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiLayers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-03: one textured chest screen, immutable, shared by every viewer ({@link GuiSession} holds the per-viewer
 * part). Built with {@link #builder}:
 *
 * <pre>
 * TfpGui gui = TfpGui.builder(SampleScreen.CHEST, SampleScreen.LAYERS)
 *         .label(p -&gt; messages.get(p, "gui.sample.title"))
 *         .input(3, SlotPredicate.ANY)            // TFC slot 0
 *         .button(16, s -&gt; icon, (session, click) -&gt; ...)
 *         .frames(s -&gt; Map.of("seal", 1))
 *         .build();
 * </pre>
 *
 * Every chest slot that is neither an input, an output nor a button is decoration: clicks on it are refused. The title
 * is the background and overlay glyphs ({@link GuiLayers}) followed by the label.
 */
public final class TfpGui {
    /** What a container slot is for. */
    public enum Kind {
        /** Items go in and out; going in is checked with the slot's {@link SlotPredicate}. */
        INPUT,
        /** Items only come out (TFC result slots). */
        OUTPUT
    }

    /** A container slot's kind and insert predicate. */
    public record SlotSpec(Kind kind, SlotPredicate accepts) {
    }

    /** What a button press does; runs on the viewer's entity scheduler. */
    @FunctionalInterface
    public interface ButtonHandler {
        void press(GuiSession session, ClickType click);
    }

    /** A button in a slot: its icon (normally {@link GuiButtons#icon}) is rebuilt on every title refresh. */
    public record Button(int slot, Function<GuiSession, ItemStack> icon, ButtonHandler handler) {
    }

    private final ChestGeometry chest;
    private final GuiLayers layers;
    private final Function<Player, String> label;
    private final Map<Integer, SlotSpec> slots;
    private final Map<Integer, Button> buttons;
    private final List<Integer> quickMoveOrder;
    private final Function<GuiSession, Map<String, Integer>> frames;
    private final Predicate<GuiSession> locked;
    private final BiConsumer<GuiSession, Player> onClose;

    private TfpGui(Builder b) {
        this.chest = b.chest;
        this.layers = b.layers;
        this.label = b.label;
        this.slots = Map.copyOf(b.slots);
        this.buttons = Map.copyOf(b.buttons);
        this.quickMoveOrder = List.copyOf(b.quickMoveOrder);
        this.frames = b.frames;
        this.locked = b.locked;
        this.onClose = b.onClose;
    }

    public static Builder builder(ChestGeometry chest, GuiLayers layers) {
        return new Builder(chest, layers);
    }

    public ChestGeometry chest() {
        return chest;
    }

    public GuiLayers layers() {
        return layers;
    }

    public String label(Player viewer) {
        return label.apply(viewer);
    }

    /** The slot spec of chest slot {@code slot}, or null for a button or decoration slot. */
    public SlotSpec slot(int slot) {
        return slots.get(slot);
    }

    public Button button(int slot) {
        return buttons.get(slot);
    }

    public Map<Integer, Button> buttons() {
        return buttons;
    }

    /** Input slots in the order shift-click fills them (TFC's {@code moveItemStackTo} range order). */
    public List<Integer> quickMoveOrder() {
        return quickMoveOrder;
    }

    /** The overlay frames for {@code session}'s current state. */
    public Map<String, Integer> frames(GuiSession session) {
        return frames.apply(session);
    }

    /** True while every container slot refuses clicks (TFC: a sealed vessel or barrel). */
    public boolean locked(GuiSession session) {
        return locked.test(session);
    }

    void closed(GuiSession session, Player viewer) {
        onClose.accept(session, viewer);
    }

    public static final class Builder {
        private final ChestGeometry chest;
        private final GuiLayers layers;
        private Function<Player, String> label = p -> "";
        private final Map<Integer, SlotSpec> slots = new LinkedHashMap<>();
        private final Map<Integer, Button> buttons = new LinkedHashMap<>();
        private final List<Integer> quickMoveOrder = new ArrayList<>();
        private Function<GuiSession, Map<String, Integer>> frames = s -> Map.of();
        private Predicate<GuiSession> locked = s -> false;
        private BiConsumer<GuiSession, Player> onClose = (s, p) -> { };

        private Builder(ChestGeometry chest, GuiLayers layers) {
            this.chest = Objects.requireNonNull(chest);
            this.layers = Objects.requireNonNull(layers);
        }

        public Builder label(Function<Player, String> label) {
            this.label = Objects.requireNonNull(label);
            return this;
        }

        /** An input slot; shift-clicks fill input slots in the order they are added. */
        public Builder input(int slot, SlotPredicate accepts) {
            claim(slot);
            slots.put(slot, new SlotSpec(Kind.INPUT, Objects.requireNonNull(accepts)));
            quickMoveOrder.add(slot);
            return this;
        }

        public Builder output(int slot) {
            claim(slot);
            slots.put(slot, new SlotSpec(Kind.OUTPUT, (s, i, st) -> false));
            return this;
        }

        public Builder button(int slot, Function<GuiSession, ItemStack> icon, ButtonHandler handler) {
            claim(slot);
            buttons.put(slot, new Button(slot, Objects.requireNonNull(icon), Objects.requireNonNull(handler)));
            return this;
        }

        public Builder frames(Function<GuiSession, Map<String, Integer>> frames) {
            this.frames = Objects.requireNonNull(frames);
            return this;
        }

        public Builder lockedWhen(Predicate<GuiSession> locked) {
            this.locked = Objects.requireNonNull(locked);
            return this;
        }

        /** Runs on the viewer's thread when the screen closes (return or drop the contents here). */
        public Builder onClose(BiConsumer<GuiSession, Player> onClose) {
            this.onClose = Objects.requireNonNull(onClose);
            return this;
        }

        private void claim(int slot) {
            if (slot < 0 || slot >= chest.size()) {
                throw new IllegalArgumentException("slot " + slot + " is outside the " + chest.rows() + "-row chest");
            }
            if (slots.containsKey(slot) || buttons.containsKey(slot)) {
                throw new IllegalArgumentException("slot " + slot + " is used twice");
            }
        }

        public TfpGui build() {
            return new TfpGui(this);
        }
    }
}
