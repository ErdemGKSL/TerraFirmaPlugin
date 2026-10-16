/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/** The degraded-mode services used when a client plugin is not installed or its adapter failed. */
final class Absent {
    private Absent() {
    }

    static final BlockVisuals BLOCK_VISUALS = new BlockVisuals() {
        @Override
        public boolean isLoaded() {
            return false;
        }

        @Override
        public Optional<BlockData> blockData(BlockStateRef state) {
            return Optional.empty();
        }

        @Override
        public long generation() {
            return 0;
        }

        @Override
        public void onReload(Runnable listener) {
        }
    };

    static final CustomBlocks CUSTOM_BLOCKS = new CustomBlocks() {
        @Override
        public boolean exists(String id) {
            return false;
        }

        @Override
        public Optional<BlockStateRef> stateAt(Block block) {
            return Optional.empty();
        }

        @Override
        public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
            return false;
        }

        @Override
        public boolean remove(Block block) {
            return false;
        }

        @Override
        public void onChunkLoad(Consumer<Chunk> hook) {
        }

        @Override
        public void onChunkUnload(Consumer<Chunk> hook) {
        }
    };

    static final CustomItems CUSTOM_ITEMS = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return false;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            return Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return Optional.empty();
        }
    };

    static final EntityModels ENTITY_MODELS = new EntityModels() {
        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public void registerModel(ModelSource source) {
        }

        @Override
        public boolean attach(Entity carrier, String modelId, float scale) {
            return false;
        }

        @Override
        public boolean tint(Entity carrier, int rgb) {
            return false;
        }

        @Override
        public int modelCount(Entity carrier) {
            return 0;
        }

        @Override
        public void detach(Entity carrier) {
        }

        @Override
        public boolean animate(Entity carrier, String animation, AnimationSpec spec) {
            return false;
        }

        @Override
        public void stopAnimation(Entity carrier, String animation) {
        }

        @Override
        public void onModelCreated(Consumer<Entity> hook) {
        }
    };

    static final HudPlaceholders HUD_PLACEHOLDERS = new HudPlaceholders() {
        @Override
        public java.util.Set<String> registered() {
            return java.util.Set.of();
        }

        @Override
        public java.util.OptionalDouble evaluate(String name, java.util.UUID player) {
            return java.util.OptionalDouble.empty();
        }
    };

    static final PackRebuilder PACK_REBUILDER = new PackRebuilder() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletableFuture<Boolean> rebuild() {
            return CompletableFuture.failedFuture(new IllegalStateException("CraftEngine is not available"));
        }
    };

    static final VisualPools VISUAL_POOLS = () -> Optional.<PoolUsage>empty();
}
