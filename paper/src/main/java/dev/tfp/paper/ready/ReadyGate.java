/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.ready;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The single "fully ready" flag (M1-BOOT-04, {@code dependency-bootstrap} §2):
 * ready = CraftEngine reloaded ∧ BetterModel models ready ∧ our registries loaded.
 *
 * <p>Each signal comes from its owner (the CE adapter on the first {@code CraftEngineReloadEvent}, the
 * BetterModel adapter on {@code PluginEndReloadEvent(Success)}, the registry loader after its first
 * swap). A signal whose plugin is not installed is <b>waived</b> at enable, so a degraded server still
 * becomes ready. A failed signal completes the gate exceptionally. One-shot: later reloads do not
 * re-arm it. Thread-safe; callbacks run on the thread that delivers the last signal, so features
 * should continue on their own executor ({@code ready().thenRunAsync(task, scheduler.cpuExecutor())}).
 */
public final class ReadyGate {
    /** The three conditions. */
    public enum Signal {
        CRAFTENGINE_RELOADED("CraftEngine has loaded its configuration"),
        BETTERMODEL_MODELS_READY("BetterModel has loaded its models"),
        REGISTRIES_LOADED("TerraFirmaPlugin registries are loaded");

        private final String description;

        Signal(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final Object lock = new Object();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final Set<Signal> pending = EnumSet.allOf(Signal.class);
    private final Map<Signal, String> waived = new EnumMap<>(Signal.class);

    /** Marks a condition met. Idempotent. */
    public void signal(Signal signal) {
        settle(signal, null);
    }

    /** Treats a condition as met because its source is absent (degraded mode). Idempotent. */
    public void waive(Signal signal, String reason) {
        settle(signal, reason);
    }

    /** The condition can never be met (e.g. registries failed to load): the gate fails. */
    public void fail(Signal signal, Throwable cause) {
        synchronized (lock) {
            pending.remove(signal);
        }
        ready.completeExceptionally(new IllegalStateException(signal + " failed: " + signal.description(), cause));
    }

    private void settle(Signal signal, String waiveReason) {
        boolean complete;
        synchronized (lock) {
            if (!pending.remove(signal)) {
                return;
            }
            if (waiveReason != null) {
                waived.put(signal, waiveReason);
            }
            complete = pending.isEmpty();
        }
        if (complete) {
            ready.complete(null);
        }
    }

    /**
     * Completes when every signal is met or waived. A copy: completing or cancelling it does not
     * affect the gate.
     */
    public CompletableFuture<Void> ready() {
        return ready.copy();
    }

    public boolean isReady() {
        return ready.isDone() && !ready.isCompletedExceptionally();
    }

    public Set<Signal> pending() {
        synchronized (lock) {
            return pending.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(pending));
        }
    }

    public Map<Signal, String> waived() {
        synchronized (lock) {
            return Map.copyOf(waived);
        }
    }

    /** One-line status for {@code /tfp version} and logs. */
    public String describe() {
        if (ready.isCompletedExceptionally()) {
            return "failed";
        }
        Set<Signal> p = pending();
        if (p.isEmpty()) {
            Map<Signal, String> w = waived();
            return w.isEmpty() ? "ready" : "ready (degraded: " + String.join("; ", w.values()) + ")";
        }
        StringBuilder sb = new StringBuilder("waiting for: ");
        boolean first = true;
        for (Signal s : p) {
            sb.append(first ? "" : ", ").append(s.description());
            first = false;
        }
        return sb.toString();
    }
}
