/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/DirtBlock.java @ c3b4d9536 (getToolModifiedState, emptyBlockAbove, useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (getToolModifiedState),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/TFCRootedDirtBlock.java @ c3b4d9536 (getToolModifiedState, useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/IMudBlock.java @ c3b4d9536 (transformToMud),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (transform),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enableFarmlandCreation, enableGrassPathCreation,
 *       enableDirtToMudCreation, enableRootedDirtToDirtCreation)
 */
package dev.tfp.core.soil;

import java.util.Optional;
import java.util.Set;

/**
 * What a hoe, a shovel and a water container do to TFC's soil blocks (PAR-GATHER-05), by block id. Dirt and clay (the
 * {@code DirtBlock}s) and the two grasses till to farmland, flatten to path and (dirt, clay, rooted dirt) take 100 mB of water
 * to mud; rooted dirt hoes back to dirt. Pure and immutable; the soil variant (entisol ... mollisol) is the last id segment and
 * is kept.
 */
public final class SoilTable {
    /** TFC {@code IMudBlock.transformToMud}: the water a conversion takes. */
    public static final int MUD_WATER = 100;

    /** The tools of {@code ItemAbilities.HOE_TILL} and {@code SHOVEL_FLATTEN}. */
    public enum Tool { HOE, SHOVEL }

    /** TFC's four toggles, all on by default. */
    public record Config(boolean farmland, boolean path, boolean mud, boolean rootedToDirt) {
        public static final Config DEFAULT = new Config(true, true, true, true);
    }

    /** The eight soil variants of {@code SoilBlockType.Variant}. */
    public static final Set<String> VARIANTS = Set.of("entisol", "aridisol", "oxisol", "fluvisol", "andisol", "podzol",
        "alfisol", "mollisol");

    static final String DIRT = "tfp:dirt/";
    static final String CLAY = "tfp:clay/";
    static final String GRASS = "tfp:grass/";
    static final String CLAY_GRASS = "tfp:clay_grass/";
    static final String ROOTED = "tfp:rooted_dirt/";
    static final String MUD = "tfp:mud/";
    static final String PATH = "tfp:grass_path/";
    static final String FARMLAND = "tfp:farmland/";

    private SoilTable() {
    }

    /** The variant of a soil block id of one of our families, empty for any other block. */
    public static Optional<String> variantOf(String blockId) {
        for (String prefix : new String[] {DIRT, CLAY, GRASS, CLAY_GRASS, ROOTED, MUD, PATH, FARMLAND}) {
            if (blockId.startsWith(prefix)) {
                String v = blockId.substring(prefix.length());
                return VARIANTS.contains(v) ? Optional.of(v) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isDirtOrClay(String id) {
        return id.startsWith(DIRT) || id.startsWith(CLAY);
    }

    private static boolean isGrass(String id) {
        return id.startsWith(GRASS) || id.startsWith(CLAY_GRASS);
    }

    /**
     * The block a tool turns {@code blockId} into, empty when it does nothing. Hoe: dirt, clay and both grasses to farmland,
     * rooted dirt to dirt. Shovel: dirt, clay and both grasses to a grass path. The caller checks the clicked face and the
     * block above ({@link #needsAirAbove}).
     */
    public static Optional<String> toolResult(String blockId, Tool tool, Config config) {
        Optional<String> variant = variantOf(blockId);
        if (variant.isEmpty()) {
            return Optional.empty();
        }
        String v = variant.get();
        if (tool == Tool.HOE) {
            if ((isDirtOrClay(blockId) || isGrass(blockId)) && config.farmland()) {
                return Optional.of(FARMLAND + v);
            }
            if (blockId.startsWith(ROOTED) && config.rootedToDirt()) {
                return Optional.of(DIRT + v);
            }
            return Optional.empty();
        }
        if ((isDirtOrClay(blockId) || isGrass(blockId)) && config.path()) {
            return Optional.of(PATH + v);
        }
        return Optional.empty();
    }

    /**
     * Whether the conversion needs the clicked face not to be down and air (or an empty fluid) above: always, as
     * {@code DirtBlock.emptyBlockAbove}, {@code HoeItem.onlyIfAirAbove} and {@code ShovelItem}.
     */
    public static boolean needsAirAbove(String blockId, Tool tool) {
        return true;
    }

    /** The mud a block turns into with 100 mB of water: dirt, clay and rooted dirt only (the grasses are not mud blocks). */
    public static Optional<String> mudOf(String blockId, Config config) {
        if (!config.mud() || !(isDirtOrClay(blockId) || blockId.startsWith(ROOTED))) {
            return Optional.empty();
        }
        return variantOf(blockId).map(v -> MUD + v);
    }

    /** The grass block of a dirt or clay block, {@code SoilBlockType.transform}; empty for every other id. */
    public static Optional<String> grassOf(String blockId) {
        if (blockId.startsWith(DIRT) || blockId.startsWith(CLAY)) {
            String prefix = blockId.startsWith(DIRT) ? GRASS : CLAY_GRASS;
            return variantOf(blockId).map(v -> prefix + v);
        }
        return Optional.empty();
    }

    /** The dirt or clay block a grass block dies back to; empty for every other id. */
    public static Optional<String> dirtOf(String blockId) {
        if (blockId.startsWith(GRASS) || blockId.startsWith(CLAY_GRASS)) {
            String prefix = blockId.startsWith(GRASS) ? DIRT : CLAY;
            return variantOf(blockId).map(v -> prefix + v);
        }
        return Optional.empty();
    }

    /** Whether the id is a grass block ({@code IGrassBlock}: grass and clay grass). */
    public static boolean isGrassBlock(String blockId) {
        return variantOf(blockId).isPresent() && isGrass(blockId);
    }

    /** Whether grass may spread onto the block ({@code IDirtBlock}: dirt and clay). */
    public static boolean isDirtBlock(String blockId) {
        return variantOf(blockId).isPresent() && isDirtOrClay(blockId);
    }
}
