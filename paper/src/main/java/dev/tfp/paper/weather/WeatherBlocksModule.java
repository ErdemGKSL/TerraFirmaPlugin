/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java @ c3b4d9536 (removePileOrSnow, onDestroyedByPlayer),
 *       src/main/java/net/dries007/tfc/common/blocks/IcePileBlock.java @ c3b4d9536 (removeIcePileOrIce, onDestroyedByPlayer)
 */
package dev.tfp.paper.weather;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * PAR-CLIM-04: wires the CraftEngine weather blocks ({@code tfp:snow_pile}, {@code tfp:ice_pile}, {@code tfp:sea_ice},
 * {@code tfp:icicle}) into {@link WorldBlocks} (the snow pipeline writes them from then on) and gives the piles TFC's break
 * behaviour: a player breaking a snow pile removes one layer and the last layer gives back the covered block (and the
 * absorbed top half); breaking an ice pile gives back what it covered when the block below holds it (TFC
 * {@code removePileOrSnow}, {@code removeIcePileOrIce}). The break itself runs as usual (the loot runtime drops the silk
 * touch snow or ice); the restore is a region task of the next tick on the block's chunk.
 */
public final class WeatherBlocksModule implements FeatureModule {
    public static final String ID = "weather_blocks";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        CustomBlocks blocks = context.integrations().customBlocks();
        WorldBlocks.install(new WorldBlocks.Custom(blocks, () -> context.integrations().blockVisuals()));
        context.listen(new PileBreaks(blocks, context.scheduler(), WorldBlocks::new));
        // PAR-GATHER-13: the snow item over a plant or loose rock writes the same piles (ItemPlaceListener asks for the service)
        context.services().provide(dev.tfp.paper.itemplace.SnowPilePlacer.class, new SnowPileItemPlacer(WorldBlocks::new,
                block -> context.services().get(dev.tfp.paper.tags.Tags.class)
                        .map(tags -> tags.is(block, "tfc:can_be_snow_piled")).orElse(false)));
    }

    @Override
    public void disable() {
        WorldBlocks.install(null);
    }

    /** The break behaviour of the two piles. */
    static final class PileBreaks implements Listener {
        private final CustomBlocks blocks;
        private final Scheduler scheduler;
        private final java.util.function.Function<World, WorldBlocks> access;

        PileBreaks(CustomBlocks blocks, Scheduler scheduler, java.util.function.Function<World, WorldBlocks> access) {
            this.blocks = blocks;
            this.scheduler = scheduler;
            this.access = access;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            Optional<BlockStateRef> ref = blocks.stateAt(block);
            if (ref.isEmpty()) {
                return;
            }
            String id = ref.get().id();
            if (!id.equals(WorldBlocks.SNOW_PILE) && !id.equals(WorldBlocks.ICE_PILE)) {
                return;
            }
            World world = block.getWorld();
            WorldBlocks access = this.access.apply(world);
            BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            SnowBlock pile = access.read(pos);
            if (pile == null || pile.pile() == null) {
                return;
            }
            SnowBlock next = next(id, pile, block);
            scheduler.region(world, block.getX() >> 4, block.getZ() >> 4, () -> {
                if (block.getType().isAir()) { // still the hole the break left
                    access.write(pos, next);
                }
            });
        }

        /** What stays at the position after the break (TFC {@code removePileOrSnow} / {@code removeIcePileOrIce}). */
        private static SnowBlock next(String id, SnowBlock pile, Block block) {
            SnowBlock.Pile memory = pile.pile();
            if (id.equals(WorldBlocks.SNOW_PILE)) {
                String layers = SnowService.property(pile.data().getAsString(), "layers");
                int n = layers == null ? 1 : Integer.parseInt(layers);
                if (n > 1) {
                    return new SnowBlock(WorldBlocks.snowLayers(n - 1), memory, null);
                }
                return new SnowBlock(memory.ground(), null, memory.above());
            }
            Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            boolean ultrawarm = block.getWorld().getEnvironment() == World.Environment.NETHER;
            if (!ultrawarm && (below.getType().isSolid() || below.isLiquid())) {
                return new SnowBlock(memory.ground(), null, memory.above());
            }
            return SnowBlock.of(Material.AIR.createBlockData());
        }
    }
}
