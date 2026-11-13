/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/BarSystem.java @ c3b4d9536 (getCustomBar)
 */
package dev.tfp.core.itembar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The item bars in TFC's registry order (heat first, then egg) and TFC's choice of the one a stack shows
 * ({@code BarSystem.getCustomBar} with the {@code ItemStackMixin} hooks). Immutable; {@link #with} builds a new
 * registry. Thread-safe.
 *
 * <p>TFC picks the first bar that is visible and either overrides other bars or would leave the stack without any bar
 * once its own state is reset ({@code !bar.createDefaultItem(stack.copy()).isBarVisible()}). Through the mixin that
 * check sees the stack's own bar (durability) and, recursively, the other registered bars: a hot fertilized egg
 * shows neither bar, a hot damaged tool shows its durability. {@link #shown} follows exactly that recursion, with each
 * bar's reset clearing only its own bar.
 *
 * @param <S> the stack view
 */
public final class ItemBarRegistry<S> {
    private final List<ItemBar<S>> bars;

    private ItemBarRegistry(List<ItemBar<S>> bars) {
        this.bars = List.copyOf(bars);
    }

    /** The registry of {@code bars}, in that order. */
    public static <S> ItemBarRegistry<S> of(List<? extends ItemBar<S>> bars) {
        return new ItemBarRegistry<>(new ArrayList<>(bars));
    }

    /** A registry with {@code bar} after the current ones (TFC registration order). */
    public ItemBarRegistry<S> with(ItemBar<S> bar) {
        Objects.requireNonNull(bar, "bar");
        for (ItemBar<S> b : bars) {
            if (b.id().equals(bar.id())) {
                throw new IllegalArgumentException("item bar '" + bar.id() + "' is already registered");
            }
        }
        List<ItemBar<S>> next = new ArrayList<>(bars);
        next.add(bar);
        return new ItemBarRegistry<>(next);
    }

    /** The bars in order. */
    public List<ItemBar<S>> bars() {
        return bars;
    }

    /**
     * The bar {@code stack} shows, if any.
     *
     * @param ownBar whether the stack draws a bar of its own without any of these bars (vanilla {@code isBarVisible}:
     *               a damaged tool)
     */
    public Optional<ShownBar> shown(S stack, boolean ownBar) {
        if (bars.isEmpty()) {
            return Optional.empty();
        }
        boolean[] visible = new boolean[bars.size()];
        for (int i = 0; i < visible.length; i++) {
            visible[i] = bars.get(i).isBarVisible(stack);
        }
        int index = choose(visible, ownBar);
        if (index < 0) {
            return Optional.empty();
        }
        ItemBar<S> bar = bars.get(index);
        return Optional.of(new ShownBar(bar.id(), Math.max(0, Math.min(ItemBar.WIDTH, bar.barWidth(stack))),
                bar.barColor(stack)));
    }

    /**
     * {@code getCustomBar} on a stack whose visible bars are {@code visible}: the index of the chosen bar or -1. The
     * default item of bar {@code i} is the same stack with bar {@code i} hidden.
     */
    private int choose(boolean[] visible, boolean ownBar) {
        for (int i = 0; i < visible.length; i++) {
            if (!visible[i]) {
                continue;
            }
            if (bars.get(i).overridesOtherBars()) {
                return i;
            }
            boolean[] reset = visible.clone();
            reset[i] = false;
            boolean defaultShowsBar = ownBar || choose(reset, ownBar) >= 0;
            if (!defaultShowsBar) {
                return i;
            }
        }
        return -1;
    }
}
