/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.function.Consumer;
import org.bukkit.entity.Entity;

/**
 * Animated entity models drawn over a vanilla carrier mob. Implemented by {@code integration/bettermodel}.
 *
 * <p>The model plugin keeps only the model id and scale across chunk reloads and restarts; everything
 * else (variant, tint, hidden parts, running animations) must be re-applied by the feature from its own
 * entity data in an {@link #onModelCreated} hook.
 */
public interface EntityModels {
    /** True after the model plugin finished loading models (successful reload). */
    boolean isReady();

    /**
     * Adds a model that is (re)registered on every model-plugin reload. Call before enable for the
     * model to be present at the first load; later calls take effect on the next reload.
     */
    void registerModel(ModelSource source);

    /**
     * Attaches {@code modelId} to {@code carrier} (no-op if already attached). Must run on the carrier's
     * owning thread (entity scheduler).
     *
     * @return false if models are not ready or the id is unknown
     */
    boolean attach(Entity carrier, String modelId, float scale);

    /**
     * Sets the scale of the carrier's attached models (the same meaning as {@link #attach}'s {@code scale}: a factor on
     * the carrier's SCALE attribute). Also works inside an {@link #onModelCreated} hook. Owning thread.
     *
     * @return false if the carrier has no model
     */
    boolean rescale(Entity carrier, float scale);

    /**
     * Tints every bone of the carrier's models ({@code 0xRRGGBB}; {@code 0xFFFFFF} = untinted). Not persisted
     * by the model plugin: features store the colour (entity PDC) and re-apply it from {@link #onModelCreated}.
     * Owning thread.
     *
     * @return false if the carrier has no model
     */
    boolean tint(Entity carrier, int rgb);

    /** How many models are attached to {@code carrier} (0 if none). Any thread. */
    int modelCount(Entity carrier);

    /** Removes every model from {@code carrier}. Owning thread. */
    void detach(Entity carrier);

    /** Plays {@code animation} on the carrier's models. Any thread. @return false if nothing played */
    boolean animate(Entity carrier, String animation, AnimationSpec spec);

    /** Stops {@code animation} on the carrier's models. Any thread. */
    void stopAnimation(Entity carrier, String animation);

    /**
     * Runs {@code hook} each time a model is (re)created on an entity, including the automatic re-creation
     * after chunk load, restart or model-plugin reload. Runs on the entity's owning thread; must be
     * idempotent and must not throw (exceptions are logged and swallowed).
     */
    void onModelCreated(Consumer<Entity> hook);

    /**
     * Runs {@code hook} after every successful model load (the first load and each model-plugin reload), on the thread
     * that finished the load. Carriers that loaded before the models existed get no model by themselves (M0-BM-01), so
     * features re-attach theirs here, each on its own entity thread. Must not throw.
     */
    void onReady(Runnable hook);
}
