/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.bettermodel;

import dev.tfp.paper.integration.AnimationSpec;
import dev.tfp.paper.integration.EntityModels;
import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.ModelSource;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.BetterModelBukkit;
import kr.toxicity.model.api.bukkit.BukkitModelEventBus;
import kr.toxicity.model.api.bukkit.entity.BaseBukkitEntity;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.event.CreateEntityTrackerEvent;
import kr.toxicity.model.api.event.ModelAssetsEvent;
import kr.toxicity.model.api.event.ModelEvent;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import kr.toxicity.model.api.util.function.BonePredicate;
import org.bukkit.entity.Entity;

/**
 * BetterModel adapter base (M1-INT-03, {@code bettermodel} skill; event and class names as in the
 * compiled M0-BM-01 spike):
 * <ul>
 *   <li>{@link ModelAssetsEvent}: registers our jar's models ({@link JarModels}) plus any
 *       {@link #registerModel} sources on every BetterModel reload;</li>
 *   <li>{@link PluginEndReloadEvent} {@code Success} → models ready, signals
 *       {@link ReadyGate.Signal#BETTERMODEL_MODELS_READY}; a failed first load waives it (degraded);</li>
 *   <li>{@link CreateEntityTrackerEvent} → the features' re-apply hooks ({@link #onModelCreated});</li>
 *   <li>every subscriber is wrapped by {@link GuardedSubscriber}.</li>
 * </ul>
 * Subscriptions happen in {@code onEnable}: BetterModel's bus ignores plugins that are not enabled.
 */
public final class BetterModelIntegration implements Integration, EntityModels {
    private final IntegrationContext ctx;
    private final Map<String, ModelSource> sources = new ConcurrentHashMap<>();
    private final List<Consumer<Entity>> createdHooks = new CopyOnWriteArrayList<>();
    private final List<ModelEventListener> subscriptions = new CopyOnWriteArrayList<>();
    /**
     * The tracker whose {@link CreateEntityTrackerEvent} is being handled on this thread. BetterModel fires the
     * event from the {@code EntityTracker} constructor, before {@code EntityTrackerRegistry} stores the tracker,
     * so a hook's {@link #tint}/{@link #animate} would not find it in the registry yet.
     */
    private final ThreadLocal<EntityTracker> creating = new ThreadLocal<>();
    private volatile boolean ready;

    BetterModelIntegration(IntegrationContext ctx, List<ModelSource> jarModels) {
        this.ctx = ctx;
        jarModels.forEach(this::registerModel);
    }

    @Override
    public String pluginName() {
        return BetterModelProvider.PLUGIN;
    }

    @Override
    public Optional<ReadyGate.Signal> readySignal() {
        return Optional.of(ReadyGate.Signal.BETTERMODEL_MODELS_READY);
    }

    @Override
    public void onEnable() {
        BukkitModelEventBus bus = BetterModelBukkit.platform().eventBus();
        subscribe(bus, ModelAssetsEvent.class, "ModelAssetsEvent", this::onAssets);
        subscribe(bus, PluginEndReloadEvent.class, "PluginEndReloadEvent", this::onReloadEnd);
        subscribe(bus, CreateEntityTrackerEvent.class, "CreateEntityTrackerEvent", this::onTrackerCreated);
    }

    private <T extends ModelEvent> void subscribe(BukkitModelEventBus bus, Class<T> type, String name, Consumer<T> handler) {
        subscriptions.add(bus.subscribe(ctx.plugin(), type, GuardedSubscriber.guard(ctx.logger(), name, handler)));
    }

    @Override
    public void onDisable() {
        for (ModelEventListener l : subscriptions) {
            try {
                l.unregister();
            } catch (RuntimeException e) {
                ctx.logger().log(Level.FINE, "BetterModel unsubscribe failed", e);
            }
        }
        subscriptions.clear();
        ready = false;
    }

    // ---- events -------------------------------------------------------------------------------------

    /** BetterModel's reload threads; asset suppliers may run in parallel. */
    void onAssets(ModelAssetsEvent event) {
        if (event.type() != ModelRenderer.Type.GENERAL) {
            return;
        }
        int added = 0;
        for (ModelSource s : sources.values()) {
            try {
                // addAsset throws if an admin's file uses the same id: skip that model, keep the others.
                event.addAsset(ModelAsset.of(s.id(), () -> s.open().open()));
                added++;
            } catch (RuntimeException e) {
                ctx.logger().warning("BetterModel: model " + s.id() + " not registered: " + e.getMessage());
            }
        }
        ctx.logger().info("BetterModel: registered " + added + "/" + sources.size() + " model(s) from TerraFirmaPlugin");
    }

    void onReloadEnd(PluginEndReloadEvent event) {
        BetterModelPlatform.ReloadResult result = event.result();
        if (result instanceof BetterModelPlatform.ReloadResult.Success success) {
            ready = true;
            List<String> missing = sources.keySet().stream().filter(id -> BetterModel.model(id).isEmpty()).sorted().toList();
            if (!missing.isEmpty()) {
                ctx.logger().warning("BetterModel loaded without our model(s): " + missing);
            }
            ctx.logger().info("BetterModel models ready (" + (success.firstLoad() ? "first load" : "reload") + ")");
            ctx.readyGate().signal(ReadyGate.Signal.BETTERMODEL_MODELS_READY);
        } else if (result instanceof BetterModelPlatform.ReloadResult.Failure) {
            ctx.logger().severe("BetterModel reload failed: " + result + "; animated entities stay unavailable");
            ctx.readyGate().waive(ReadyGate.Signal.BETTERMODEL_MODELS_READY, "BetterModel failed to load models");
        }
        // OnReload: a reload was requested while one runs; the running one will end with its own event.
    }

    /** Runs on the thread that created the tracker (the entity's owner). */
    void onTrackerCreated(CreateEntityTrackerEvent event) {
        Entity entity = entityOf(event.tracker());
        if (entity == null) {
            return;
        }
        EntityTracker previous = creating.get();
        creating.set(event.tracker());
        try {
            for (Consumer<Entity> hook : createdHooks) {
                try {
                    hook.accept(entity);
                } catch (RuntimeException e) {
                    ctx.logger().log(Level.SEVERE, "model re-apply hook failed for " + entity.getUniqueId(), e);
                }
            }
        } finally {
            if (previous == null) {
                creating.remove();
            } else {
                creating.set(previous);
            }
        }
    }

    /** The carrier's registered trackers plus the one being created for it on this thread (see {@link #creating}). */
    private List<EntityTracker> trackersOf(Entity carrier) {
        List<EntityTracker> out = new ArrayList<>();
        BetterModel.registry(carrier.getUniqueId()).ifPresent(r -> out.addAll(r.trackers()));
        EntityTracker pending = creating.get();
        if (pending != null && carrier.equals(entityOf(pending)) && !out.contains(pending)) {
            out.add(pending);
        }
        return out;
    }

    private static Entity entityOf(EntityTracker tracker) {
        return tracker.sourceEntity() instanceof BaseBukkitEntity b ? b.entity() : null;
    }

    // ---- EntityModels -------------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void registerModel(ModelSource source) {
        if (sources.putIfAbsent(source.id(), source) != null) {
            throw new IllegalArgumentException("model " + source.id() + " registered twice");
        }
    }

    @Override
    public boolean attach(Entity carrier, String modelId, float scale) {
        ThreadAsserts.owned(carrier);
        if (!ready) {
            return false;
        }
        Optional<ModelRenderer> renderer = BetterModel.model(modelId);
        if (renderer.isEmpty()) {
            return false;
        }
        ModelScaler scaler = scale == 1f ? ModelScaler.entity() : ModelScaler.entity().multiply(scale);
        renderer.get().getOrCreate(BukkitAdapter.adapt(carrier), TrackerModifier.DEFAULT, t -> t.scaler(scaler));
        return true;
    }

    /** {@code TrackerUpdateAction.tint} on every bone, as in the M0-BM-01 spike (there on the body bone and its children). */
    @Override
    public boolean tint(Entity carrier, int rgb) {
        ThreadAsserts.owned(carrier);
        List<EntityTracker> trackers = trackersOf(carrier);
        for (EntityTracker t : trackers) {
            t.update(TrackerUpdateAction.tint(rgb & 0xFFFFFF), BonePredicate.TRUE);
        }
        return !trackers.isEmpty();
    }

    @Override
    public int modelCount(Entity carrier) {
        return BetterModel.registry(carrier.getUniqueId()).map(r -> r.trackers().size()).orElse(0);
    }

    @Override
    public void detach(Entity carrier) {
        ThreadAsserts.owned(carrier);
        BetterModel.registry(carrier.getUniqueId()).ifPresent(EntityTrackerRegistry::close);
    }

    @Override
    public boolean animate(Entity carrier, String animation, AnimationSpec spec) {
        List<EntityTracker> trackers = trackersOf(carrier);
        if (trackers.isEmpty()) {
            return false;
        }
        AnimationModifier modifier = AnimationModifier.builder()
                .priority(spec.priority())
                .override(spec.override())
                .type(spec.once() ? AnimationIterator.Type.PLAY_ONCE : AnimationIterator.Type.LOOP)
                .build();
        boolean any = false;
        for (EntityTracker t : trackers) {
            any |= t.animate(animation, modifier);
        }
        return any;
    }

    @Override
    public void stopAnimation(Entity carrier, String animation) {
        BetterModel.registry(carrier.getUniqueId()).ifPresent(r -> r.trackers().forEach(t -> t.stopAnimation(animation)));
    }

    @Override
    public void onModelCreated(Consumer<Entity> hook) {
        createdHooks.add(hook);
    }
}
