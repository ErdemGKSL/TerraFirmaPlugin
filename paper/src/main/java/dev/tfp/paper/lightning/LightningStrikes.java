/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onEntityJoinLevel: the lightning bolt branch)
 */
package dev.tfp.paper.lightning;

import dev.tfp.core.lightning.LightningRules;
import dev.tfp.core.lightning.LightningRules.Config;
import dev.tfp.core.lightning.LightningRules.Draws;
import dev.tfp.core.lightning.LightningRules.Offset;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;

/**
 * PAR-CLIM-08: TFC's rules for a lightning bolt entering the world. {@code enableLightning=false} cancels the strike (no
 * damage, no fire); with {@code enableLightningStrippingLogs} one strike in five strips the logs within five blocks of it
 * (a third of the cells in the sphere), like TFC's axe stripping. Vanilla's fire from the bolt stays.
 *
 * <p>Threads: the event runs on the region of the bolt. The offsets are drawn there (pure, 1,332 draws on one strike in
 * five); the cells are grouped by chunk and each chunk's strips are applied on that chunk's own region ("decide, then apply
 * on the owner"), never into an unloaded chunk.
 */
public final class LightningStrikes implements Listener {
    private final Config config;
    private final CustomBlocks blocks;
    private final Supplier<Draws> draws;
    private final BiConsumer<Block, Runnable> onRegion;

    /**
     * @param onRegion runs a task on the region thread that owns the given block's chunk
     */
    public LightningStrikes(Config config, CustomBlocks blocks, Supplier<Draws> draws, BiConsumer<Block, Runnable> onRegion) {
        this.config = config;
        this.blocks = blocks;
        this.draws = draws;
        this.onRegion = onRegion;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(LightningStrikeEvent event) {
        if (!LightningRules.allowed(config)) {
            event.setCancelled(true);
            return;
        }
        List<Offset> offsets = LightningRules.strippedOffsets(config, draws.get());
        if (offsets.isEmpty()) {
            return;
        }
        Block origin = event.getLightning().getLocation().getBlock();
        World world = origin.getWorld();
        for (Offset o : offsets) {
            int x = origin.getX() + o.x();
            int y = origin.getY() + o.y();
            int z = origin.getZ() + o.z();
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            onRegion.accept(block, () -> strip(block));
        }
    }

    /** TFC {@code getToolModifiedState(AXE_STRIP)} + {@code setBlockAndUpdate}: the stripped log keeps the old properties. */
    void strip(Block block) {
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            String target = LightningRules.stripped(custom.get().id());
            if (target != null) {
                blocks.place(block, BlockStateRef.of(target, custom.get().properties()), true);
            }
            return;
        }
        String target = LightningRules.stripped("minecraft:" + block.getType().name().toLowerCase(Locale.ROOT));
        Material material = target == null ? null : Material.matchMaterial(target);
        if (material == null) {
            return;
        }
        BlockData old = block.getBlockData();
        BlockData next = material.createBlockData();
        if (old instanceof Orientable from && next instanceof Orientable to) {
            to.setAxis(from.getAxis());
        }
        block.setBlockData(next, true);
    }
}
