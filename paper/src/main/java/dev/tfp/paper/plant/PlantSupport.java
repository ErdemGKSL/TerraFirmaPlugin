/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCBushBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (updateShape: the halves)
 */
package dev.tfp.paper.plant;

import dev.tfp.core.plant.PlantSurvival;
import dev.tfp.core.plant.PlantSurvival.Below;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.tags.Tags;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * PAR-FARM-12: the survival check of placed TFC plants, "decide async, apply sync". {@link #snapshot} reads what the rule needs from the
 * world (region thread), {@link #decide} is the pure decision (any thread), the caller removes a plant that cannot survive and re-checks
 * that the block still is the snapshotted plant first.
 */
public final class PlantSupport {
    static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private final CustomBlocks blocks;
    private final Tags tags;

    public PlantSupport(CustomBlocks blocks, Tags tags) {
        this.blocks = blocks;
        this.tags = tags;
    }

    /**
     * What one plant block needs to decide.
     *
     * @param state          the plant block's state when read (the commit checks it again)
     * @param type           its TFC block type
     * @param upper          whether it is the upper half of a tall plant
     * @param belowTags      the support tags the block below is in
     * @param sameBlockLower whether the block below is the lower half of the same plant block
     * @param solidSides     whether a horizontal neighbour is solid (only the cactus block looks at it)
     */
    public record Snapshot(BlockStateRef state, PlantType type, boolean upper, Set<String> belowTags, boolean sameBlockLower,
            boolean solidSides) {
    }

    /** The TFC plant type of a custom block id, when it is a plant with a ported survival rule that the CE block draws. */
    public static Optional<PlantType> typeOf(String blockId) {
        PlantType type = PlantType.of(blockId);
        if (type == null || !PlantSurvival.ported(type) || type == PlantType.FLOATING || type == PlantType.FLOATING_FRESH) {
            // the floating plants stand on a fluid: CraftEngine's on_liquid_block keeps them
            return Optional.empty();
        }
        return Optional.of(type);
    }

    /** Region thread. The snapshot of the plant at {@code block}, empty for anything else. */
    public Optional<Snapshot> snapshot(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        Optional<PlantType> type = typeOf(state.get().id());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        boolean upper = type.get().isTall() && "upper".equals(state.get().properties().get("half"));
        Block below = block.getRelative(BlockFace.DOWN);
        Set<String> belowTags = new HashSet<>();
        for (String tag : PlantSurvival.supportTags(type.get())) {
            if (tags.is(below, tag)) {
                belowTags.add(tag);
            }
        }
        if (type.get() == PlantType.CACTUS && tags.is(below, PlantSurvival.DRY_PLANTABLE_ON)) {
            belowTags.add(PlantSurvival.DRY_PLANTABLE_ON);
        }
        Optional<BlockStateRef> belowState = blocks.stateAt(below);
        boolean sameLower = belowState.isPresent() && belowState.get().id().equals(state.get().id())
            && !"upper".equals(belowState.get().properties().get("half"));
        boolean solidSides = false;
        if (type.get() == PlantType.CACTUS) {
            for (BlockFace face : SIDES) {
                Block side = block.getRelative(face);
                solidSides |= side.getType().isSolid() || side.getType() == org.bukkit.Material.LAVA;
            }
        }
        return Optional.of(new Snapshot(state.get(), type.get(), upper, belowTags, sameLower, solidSides));
    }

    /** Pure. Whether the plant survives. */
    public static boolean decide(Snapshot s) {
        return PlantSurvival.canSurvive(s.type(), s.upper(),
            new Below(s.belowTags()::contains, s.sameBlockLower(), false, PlantState.Fluid.EMPTY), s.solidSides());
    }
}
