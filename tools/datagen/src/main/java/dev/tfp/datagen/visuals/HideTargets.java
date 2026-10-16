/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import dev.tfp.datagen.DatagenException;
import java.util.List;
import java.util.Set;

/**
 * Where real vanilla instances of a repurposed state are sent instead ({@code block_state_mappings}). Mappings are
 * one hop, so a target must keep its vanilla look: it is never allocated and never a CraftEngine-mapped state.
 *
 * <ol>
 *   <li>An identity duplicate goes to its twin (identical model), unless the twin is itself allocated.</li>
 *   <li>Otherwise a look-alike by block family (of the twin, if any): coloured wool/concrete/terracotta → that
 *       colour's concrete powder; terracotta → red sand; sandstone → sand; wood → oak planks; soil → dirt;
 *       anything else → stone. Concrete powder and sand are falling blocks (major caveat), so they are never
 *       allocated; stone, dirt and oak planks are {@link #RESERVED} and left out of the supply.</li>
 * </ol>
 */
public final class HideTargets {
    public static final String STONE = "minecraft:stone";
    public static final String DIRT = "minecraft:dirt";
    public static final String OAK_PLANKS = "minecraft:oak_planks";
    /** Kept vanilla in every allocation, as look-alike mapping targets. */
    public static final Set<String> RESERVED = Set.of(STONE, DIRT, OAK_PLANKS);

    static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Set<String> SOIL = Set.of("dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol",
        "mycelium", "mud", "clay", "moss_block", "pale_moss_block", "muddy_mangrove_roots", "packed_mud");

    private HideTargets() {
    }

    /** Look-alike target for a block id ({@code minecraft:...}) by family; see the class comment. */
    static String fallback(String block) {
        String b = block.substring(block.indexOf(':') + 1);
        for (String c : COLORS) {
            if (b.equals(c + "_wool") || b.equals(c + "_concrete") || b.equals(c + "_terracotta")
                || b.equals(c + "_glazed_terracotta")) {
                return "minecraft:" + c + "_concrete_powder";
            }
        }
        if (b.equals("terracotta")) {
            return "minecraft:red_sand";
        }
        if (b.endsWith("sandstone")) {
            return b.contains("red_sandstone") ? "minecraft:red_sand" : "minecraft:sand";
        }
        if ((b.endsWith("_log") || b.endsWith("_wood") || b.endsWith("_planks") || b.endsWith("_hyphae")
            || (b.endsWith("_stem") && !b.equals("mushroom_stem")) || b.startsWith("stripped_")
            || b.equals("bamboo_block") || b.equals("bamboo_mosaic") || b.equals("bookshelf"))) {
            return OAK_PLANKS;
        }
        if (SOIL.contains(b)) {
            return DIRT;
        }
        return STONE;
    }

    /** Target for a repurposed state; {@code allocated} holds every state used as a visual in this allocation. */
    static String target(VanillaSupply.State s, Set<String> allocated, VanillaSupply supply, VersionFamily family) {
        String target;
        if (s.source().identity() && !allocated.contains(s.twin())) {
            target = s.twin();
        } else {
            String basis = s.source().identity() ? s.twin() : s.state();
            int i = basis.indexOf('[');
            target = fallback(i < 0 ? basis : basis.substring(0, i));
        }
        if (allocated.contains(target) || supply.get(family, target) == null) {
            throw new DatagenException("mapping target " + target + " for " + s.state() + " is allocated or not a "
                + "plain vanilla full cube on " + family.id());
        }
        return target;
    }
}
