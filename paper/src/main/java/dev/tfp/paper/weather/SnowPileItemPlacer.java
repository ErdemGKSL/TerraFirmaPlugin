/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java @ c3b4d9536 (placeSnowPile, absorbing the block above),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (the snow item over a pileable block)
 */
package dev.tfp.paper.weather;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.itemplace.SnowPilePlacer;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/**
 * PAR-GATHER-13: the snow item over a plant or loose rock. Writes the same pile the weather's {@code PLACE_SNOW_PILE} writes
 * ({@link SnowService}): a one-layer {@code tfp:snow_pile} whose memory holds the covered block and, when it is snow-pileable,
 * the block above (the top half of a plant), through {@link WorldBlocks}, so a break or a melt gives them back. Adding a layer
 * keeps the memory. Region thread of the block; the caller has decided that the block may hold a pile
 * ({@code ItemPlacementRules#snow}).
 */
public final class SnowPileItemPlacer implements SnowPilePlacer {
    private final Function<World, WorldBlocks> access;
    private final Predicate<Block> pileable;

    /**
     * @param access   the block access of a world, the one the snow pipeline writes through
     * @param pileable whether a block is in {@code #tfc:can_be_snow_piled}: the block above a pile is absorbed only then
     */
    SnowPileItemPlacer(Function<World, WorldBlocks> access, Predicate<Block> pileable) {
        this.access = Objects.requireNonNull(access, "access");
        this.pileable = Objects.requireNonNull(pileable, "pileable");
    }

    @Override
    public boolean placePile(Block block) {
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        WorldBlocks blocks = access.apply(block.getWorld());
        if (blocks.read(pos) == null) {
            return false;
        }
        BlockData ground = block.getBlockData();
        Block above = block.getRelative(BlockFace.UP);
        BlockData absorbed = pileable.test(above) ? above.getBlockData() : null;
        blocks.write(pos, new SnowBlock(WorldBlocks.snowLayers(1), new SnowBlock.Pile(ground, absorbed), null));
        return true;
    }

    @Override
    public boolean addLayer(Block block) {
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        WorldBlocks blocks = access.apply(block.getWorld());
        SnowBlock now = blocks.read(pos);
        if (now == null || now.pile() == null) {
            return false;
        }
        String layers = SnowService.property(now.data().getAsString(), "layers");
        int n = layers == null ? 1 : Integer.parseInt(layers);
        if (n >= 8) {
            return false;
        }
        blocks.write(pos, new SnowBlock(WorldBlocks.snowLayers(n + 1), now.pile(), null));
        return true;
    }
}
