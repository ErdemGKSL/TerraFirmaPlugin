/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.paper.ready.ReadyGate;
import java.util.Optional;

/**
 * One client-plugin adapter (CraftEngine, BetterModel, BetterHud) with the lifecycle of
 * ARCHITECTURE.md §12. It exposes the {@code paper} interfaces it implements through {@link #service}.
 * Created only when its plugin is installed; an exception from a lifecycle method disables the adapter
 * (its services fall back to the absent implementations, its ready signal is waived).
 */
public interface Integration {
    /** The plugin this adapter drives, as in its descriptor ({@code CraftEngine}). */
    String pluginName();

    /** The ready-gate condition this adapter signals, if any. */
    default Optional<ReadyGate.Signal> readySignal() {
        return Optional.empty();
    }

    /** From our {@code onLoad()}: e.g. CraftEngine behaviour registration. */
    default void onLoad() {
    }

    /** From our {@code onEnable()}: event subscriptions, placeholders, listeners. */
    default void onEnable() {
    }

    default void onDisable() {
    }

    /** This adapter as {@code type}, if it implements it. */
    default <T> Optional<T> service(Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }
}
