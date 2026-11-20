/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.plant.PlantBlock;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.core.worldgen.plant.WildCrop;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/**
 * The blocks of the plants, wild crops and corals (M2-GEN-13). The land and floating plants are CraftEngine blocks
 * ({@link #ceRef}, PAR-WORLD-12; the terrain writes them once PAR-WORLD-46 wires it); until then, and for the water plants,
 * wild crops and corals, each TFC block gets a vanilla placeholder (ledger): grasses, flowers, ferns, dead bushes, lily pads, seagrass, vanilla corals and
 * sea pickles. The corals are vanilla's own blocks (TFC uses vanilla's coral blocks and its own coral plants of the same five
 * colours); the coral plants, fans, wall fans and sea pickles have CraftEngine states ({@link #coralRef}, PAR-WORLD-25;
 * written once PAR-WORLD-49 wires them). The placement rule is pure: {@link #write} applies the decisions of one chunk through a {@link Target}.
 */
final class PlantBlocks {
    /** The vanilla flowers named like a TFC plant. */
    private static final Map<String, Material> FLOWERS = Map.ofEntries(
        Map.entry("allium", Material.ALLIUM), Map.entry("blue_orchid", Material.BLUE_ORCHID),
        Map.entry("dandelion", Material.DANDELION), Map.entry("poppy", Material.POPPY), Map.entry("oxeye_daisy", Material.OXEYE_DAISY),
        Map.entry("cornflower", Material.CORNFLOWER), Map.entry("lily_of_the_valley", Material.LILY_OF_THE_VALLEY),
        Map.entry("tulip_red", Material.RED_TULIP), Map.entry("tulip_orange", Material.ORANGE_TULIP),
        Map.entry("tulip_white", Material.WHITE_TULIP), Map.entry("tulip_pink", Material.PINK_TULIP),
        Map.entry("houstonia", Material.AZURE_BLUET));
    /** The flowers a TFC flower without a vanilla namesake is spread over (by a stable hash of its name). */
    private static final List<Material> OTHER_FLOWERS = List.of(Material.POPPY, Material.DANDELION, Material.ALLIUM,
        Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.BLUE_ORCHID, Material.RED_TULIP);
    private static final Map<String, Material> TALL_FLOWERS = Map.of("rose", Material.ROSE_BUSH, "lilac", Material.LILAC,
        "sunflower", Material.SUNFLOWER);

    private PlantBlocks() {}

    /**
     * The vanilla placeholder of a block, or null when the block has none (the upper half of a plant whose placeholder is one
     * block tall).
     */
    static Material placeholder(PlantState state) {
        final String name = state.block().substring(state.block().lastIndexOf('/') + 1);
        final boolean upper = state.part() == PlantState.Part.UPPER;
        return switch (state.kind()) {
            case PLANT -> plant(state.type(), name, upper, state.fluid());
            case WILD_CROP -> switch (WildCrop.of(state.block())) {
                case DOUBLE -> Material.LARGE_FERN;
                case FLOODED -> Material.SEAGRASS;
                case DEFAULT, SPREADING -> upper ? null : Material.FERN;
            };
            case WILD_CROP_FRUIT -> state.block().endsWith("melon") ? Material.MELON : Material.PUMPKIN;
            case CORAL_BLOCK, CORAL, CORAL_FAN, CORAL_WALL_FAN, SEA_PICKLE -> vanilla(state.block());
        };
    }

    private static Material plant(PlantType type, String name, boolean upper, PlantState.Fluid fluid) {
        return switch (type) {
            case SHORT_GRASS, BEACH_GRASS -> Material.SHORT_GRASS;
            case TALL_GRASS -> TALL_FLOWERS.getOrDefault(name, name.contains("fern") ? Material.LARGE_FERN : Material.TALL_GRASS);
            case TALL_SHRUB -> Material.PEONY;
            case STANDARD -> FLOWERS.getOrDefault(name, name.contains("fern") || name.contains("horsetail") ? Material.FERN
                : OTHER_FLOWERS.get(Math.floorMod(name.hashCode(), OTHER_FLOWERS.size())));
            case FLOWERBED -> Material.PINK_PETALS;
            case SHRUB, SHORT_SHRUB -> Material.AZALEA;
            case DRY, CACTUSBED -> Material.DEAD_BUSH;
            case PASSABLE_CACTUS -> upper ? null : Material.DEAD_BUSH;
            case FLOATING, FLOATING_FRESH -> Material.LILY_PAD;
            case WATER, WATER_FRESH, GRASS_WATER, GRASS_WATER_FRESH -> Material.SEAGRASS;
            // submerged (both halves in water): tall seagrass; emergent (the upper half in air): seagrass under the surface only
            case TALL_WATER, TALL_WATER_FRESH -> upper && fluid == PlantState.Fluid.EMPTY ? null : Material.TALL_SEAGRASS;
            default -> throw new IllegalArgumentException("no placeholder for plant type " + type);
        };
    }

    /** The vanilla block of a coral id ({@code minecraft:tube_coral_block}, {@code tfp:coral/tube_coral_fan}, {@code tfp:sea_pickle}). */
    private static Material vanilla(String id) {
        final String name = id.substring(id.lastIndexOf(id.contains("/") ? '/' : ':') + 1);
        return Material.valueOf(name.toUpperCase(Locale.ROOT));
    }

    /**
     * PAR-WORLD-12: the CraftEngine state of a plant block ({@code tfp:plant/rose[age=2,half=upper]}) for the types datagen
     * generates (land and floating plants: {@code dev.tfp.datagen.plant.PlantBlockGen.TYPES}), or null for the others
     * (water plants, wild crops, corals), which keep their vanilla placeholder. TFC's {@code part} is CraftEngine's
     * {@code half}; an age not drawn yet is age 0 (TFC's default state).
     */
    static String ceRef(PlantState state) {
        if (state.kind() != PlantState.Kind.PLANT || !CE_TYPES.contains(state.type())) {
            return null;
        }
        final List<String> props = new java.util.ArrayList<>(2);
        if (state.type().hasAge()) {
            props.add("age=" + Math.max(0, state.age()));
        }
        if (state.type().isTall()) {
            props.add("half=" + (state.part() == PlantState.Part.UPPER ? "upper" : "lower"));
        }
        return props.isEmpty() ? state.block() : state.block() + "[" + String.join(",", props) + "]";
    }

    /**
     * PAR-WORLD-25: the CraftEngine state of a coral plant, fan, wall fan or sea pickle
     * ({@code tfp:coral/tube_coral_wall_fan[facing=east,waterlogged=true]}, {@code tfp:sea_pickle[pickles=3,waterlogged=true]}),
     * or null for anything else (the vanilla coral blocks TFC uses stay vanilla). TFC's {@code fluid=salt_water} is
     * {@code waterlogged=true} (core {@code CoralRules}); a wall fan's extra is its facing (north, east, south, west, as
     * {@code Corals} draws it), a sea pickle's its count.
     */
    static String coralRef(PlantState state) {
        final String wet = "waterlogged=" + (state.fluid() != PlantState.Fluid.EMPTY);
        return switch (state.kind()) {
            case CORAL, CORAL_FAN -> state.block() + "[" + wet + "]";
            case CORAL_WALL_FAN -> state.block() + "[facing=" + switch (state.extra()) {
                case 0 -> "north";
                case 1 -> "east";
                case 2 -> "south";
                default -> "west";
            } + "," + wet + "]";
            case SEA_PICKLE -> state.block() + "[pickles=" + Math.clamp(state.extra(), 1, 4) + "," + wet + "]";
            default -> null;
        };
    }

    /** The plant types generated as CraftEngine blocks (PAR-WORLD-12). */
    static final java.util.Set<PlantType> CE_TYPES = java.util.EnumSet.of(PlantType.STANDARD, PlantType.SHORT_SHRUB,
        PlantType.SHRUB, PlantType.FLOWERBED, PlantType.CACTUSBED, PlantType.PASSABLE_CACTUS, PlantType.DRY,
        PlantType.SHORT_GRASS, PlantType.BEACH_GRASS, PlantType.TALL_GRASS, PlantType.TALL_SHRUB, PlantType.FLOATING,
        PlantType.FLOATING_FRESH);

    /** Whether a placeholder is a vanilla two-block plant ({@code Bisected}), written as a lower and an upper half. */
    static boolean twoHalves(Material material) {
        return material == Material.TALL_GRASS || material == Material.LARGE_FERN || material == Material.ROSE_BUSH
            || material == Material.LILAC || material == Material.SUNFLOWER || material == Material.PEONY
            || material == Material.TALL_SEAGRASS;
    }

    /** The blocks of the generating chunk, by local x and z. */
    interface Target {
        /** The block at a position of the chunk. */
        Material type(int localX, int y, int localZ);

        /**
         * Places a block's placeholder.
         *
         * @param material  the placeholder ({@link #placeholder})
         * @param upperHalf for a {@link #twoHalves} placeholder, whether this is its upper half
         */
        void place(int localX, int y, int localZ, PlantBlock block, Material material, boolean upperHalf);
    }

    /**
     * Writes the plant blocks of one chunk. A block outside the chunk or its heights is skipped; each goes only where the chunk
     * still holds what TFC's feature saw there, re-validated against the chunk as it is now: a block filled with water on
     * water, any other plant on air, a coral block on water or a coral, so an earlier feature's block (a tree, a neighbouring
     * chunk's plant) is kept. A two-block placeholder is written with both halves or not at all; the lower half of a
     * two-block plant whose placeholder is one block goes alone.
     *
     * @return the number of blocks written
     */
    static int write(List<PlantBlock> blocks, int chunkX, int chunkZ, int minY, int maxY, Target target) {
        final Map<Long, PlantBlock> upperHalves = new HashMap<>();
        for (PlantBlock b : blocks) {
            if (b.state().part() == PlantState.Part.UPPER) {
                upperHalves.put(key(b.x(), b.y(), b.z()), b);
            }
        }
        int written = 0;
        for (PlantBlock b : blocks) {
            if (b.x() >> 4 != chunkX || b.z() >> 4 != chunkZ || b.y() <= minY || b.y() >= maxY
                || b.state().part() == PlantState.Part.UPPER) {
                continue;
            }
            Material material = placeholder(b.state());
            if (material == null) {
                continue;
            }
            final int lx = b.x() & 15;
            final int lz = b.z() & 15;
            final PlantBlock upper = upperHalves.get(key(b.x(), b.y() + 1, b.z()));
            if (twoHalves(material) && upper != null && placeholder(upper.state()) == null) {
                // an emergent water plant: its upper half (in air) has no placeholder, so the lower one goes alone
                material = Material.SEAGRASS;
            }
            if (b.state().part() == PlantState.Part.LOWER && twoHalves(material)) {
                if (upper == null || b.y() + 1 >= maxY || !free(target, lx, b.y(), lz, b.state())
                    || !free(target, lx, b.y() + 1, lz, upper.state())) {
                    continue;
                }
                target.place(lx, b.y(), lz, b, material, false);
                target.place(lx, b.y() + 1, lz, upper, material, true);
                written += 2;
            } else if (free(target, lx, b.y(), lz, b.state())) {
                target.place(lx, b.y(), lz, b, material, false);
                written++;
            }
        }
        return written;
    }

    /** Whether the chunk still holds what the decision saw at a position (see {@link #write}). */
    static boolean free(Target target, int localX, int y, int localZ, PlantState state) {
        final Material type = target.type(localX, y, localZ);
        if (state.kind() == PlantState.Kind.CORAL_BLOCK) {
            return type == Material.WATER || type.name().endsWith("_CORAL") || type.name().endsWith("_CORAL_FAN");
        }
        if (state.fluid() != PlantState.Fluid.EMPTY) {
            return type == Material.WATER;
        }
        return type == Material.AIR || type == Material.CAVE_AIR;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }
}
