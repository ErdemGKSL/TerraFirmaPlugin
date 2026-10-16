/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;

/**
 * In-memory {@link EntityModels}. Records attachments and animations per entity UUID; tests decide when
 * models become ready and simulate the model plugin re-creating a model ({@link #recreate}). Only
 * {@code getUniqueId()} of entities is read.
 */
public final class FakeEntityModels implements EntityModels {
    /** A model on an entity. */
    public record Attached(String modelId, float scale) {
    }

    /** One {@link #animate} call. */
    public record Played(String animation, AnimationSpec spec) {
    }

    private final Map<String, ModelSource> sources = new ConcurrentHashMap<>();
    private final Map<UUID, Attached> attached = new ConcurrentHashMap<>();
    private final Map<UUID, List<Played>> played = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tints = new ConcurrentHashMap<>();
    private final List<Consumer<Entity>> hooks = new CopyOnWriteArrayList<>();
    private volatile boolean ready;

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public Map<String, ModelSource> sources() {
        return Map.copyOf(sources);
    }

    public Optional<Attached> attachedTo(UUID entity) {
        return Optional.ofNullable(attached.get(entity));
    }

    public List<Played> played(UUID entity) {
        return List.copyOf(played.getOrDefault(entity, List.of()));
    }

    /** The last tint applied to the carrier's model since it was (re)created. */
    public Optional<Integer> tintOf(UUID entity) {
        return Optional.ofNullable(tints.get(entity));
    }

    /** Simulates the model being re-created (chunk load, restart, reload): animations and tint are lost, hooks run. */
    public void recreate(Entity entity) {
        played.remove(entity.getUniqueId());
        tints.remove(entity.getUniqueId());
        hooks.forEach(h -> h.accept(entity));
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void registerModel(ModelSource source) {
        sources.put(source.id(), source);
    }

    @Override
    public boolean attach(Entity carrier, String modelId, float scale) {
        if (!ready || !sources.containsKey(modelId)) {
            return false;
        }
        if (attached.putIfAbsent(carrier.getUniqueId(), new Attached(modelId, scale)) == null) {
            hooks.forEach(h -> h.accept(carrier));
        }
        return true;
    }

    @Override
    public boolean tint(Entity carrier, int rgb) {
        if (!attached.containsKey(carrier.getUniqueId())) {
            return false;
        }
        tints.put(carrier.getUniqueId(), rgb & 0xFFFFFF);
        return true;
    }

    @Override
    public int modelCount(Entity carrier) {
        return attached.containsKey(carrier.getUniqueId()) ? 1 : 0;
    }

    @Override
    public void detach(Entity carrier) {
        tints.remove(carrier.getUniqueId());
        attached.remove(carrier.getUniqueId());
        played.remove(carrier.getUniqueId());
    }

    @Override
    public boolean animate(Entity carrier, String animation, AnimationSpec spec) {
        if (!attached.containsKey(carrier.getUniqueId())) {
            return false;
        }
        played.computeIfAbsent(carrier.getUniqueId(), k -> new CopyOnWriteArrayList<>()).add(new Played(animation, spec));
        return true;
    }

    @Override
    public void stopAnimation(Entity carrier, String animation) {
        List<Played> list = played.get(carrier.getUniqueId());
        if (list != null) {
            list.removeIf(p -> p.animation().equals(animation));
        }
    }

    @Override
    public void onModelCreated(Consumer<Entity> hook) {
        hooks.add(hook);
    }
}
