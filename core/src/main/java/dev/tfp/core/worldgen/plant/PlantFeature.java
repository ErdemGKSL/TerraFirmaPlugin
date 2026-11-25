/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.tree.ForestType;
import java.util.List;
import java.util.Set;

/**
 * The parsed form of TFC's plant, wild crop and coral features: a placed feature is its placement modifiers and the
 * configured feature they lead to, with the parts the M2-GEN-13 port runs ({@link PlantData} refuses the others). All
 * immutable.
 */
public final class PlantFeature {
    private PlantFeature() {}

    /**
     * A placed feature ({@code worldgen/placed_feature}).
     *
     * @param id        the placed feature's id ({@code tfp:plant/allium_patch})
     * @param modifiers the placement modifiers, in order
     * @param feature   the configured feature placed at every position the modifiers give
     */
    public record Placed(String id, List<Modifier> modifiers, Configured feature) {
        public Placed {
            modifiers = List.copyOf(modifiers);
        }
    }

    /** A placement modifier. Each one maps a position to zero or more positions, drawing from the feature's random. */
    public sealed interface Modifier {}

    /** {@code minecraft:rarity_filter}: one float, kept when below {@code 1 / chance}. */
    public record Rarity(int chance) implements Modifier {}

    /** {@code minecraft:in_square}: x then z in {@code [0, 16)} added to the position. */
    public record InSquare() implements Modifier {}

    /** {@code minecraft:heightmap}: the y of the column's {@code OCEAN_FLOOR_WG} or {@code WORLD_SURFACE_WG} height. */
    public record Heightmap(boolean oceanFloor) implements Modifier {}

    /**
     * {@code tfc:climate}: {@link ClimateFilter} at the position, with the forest density of its chunk; a non-empty
     * {@code forestTypes} also needs the chunk's forest type in it (checked before the fuzzy draw, as TFC's condition is).
     */
    public record Climate(ClimateFilter filter, Set<ForestType> forestTypes) implements Modifier {
        public Climate {
            forestTypes = Set.copyOf(forestTypes);
        }

        public Climate(ClimateFilter filter) {
            this(filter, Set.of());
        }
    }

    /** {@code minecraft:height_range} with a uniform height: the y drawn in {@code [min, max]}. */
    public record HeightRange(int min, int max) implements Modifier {}

    /** {@code tfc:near_fluid}: a fluid (any of {@code fluids}) within {@code radius} around and below the position. */
    public record NearFluid(int radius, Set<PlantState.Fluid> fluids) implements Modifier {
        public NearFluid {
            fluids = Set.copyOf(fluids);
        }
    }

    /**
     * {@code tfc:carving_mask}: every carved air position of the chunk ({@link CarvingMaskView}), ordered by y, z, x, with
     * {@code minY <= y <= maxY}.
     */
    public record CarvingMask(int minY, int maxY) implements Modifier {}

    /** {@code tfc:atoll}: an atoll's easing at the column strictly between the bounds ({@link Atolls}). */
    public record Atoll(float minEasing, float maxEasing) implements Modifier {}

    /** {@code minecraft:count}: the position repeated a constant or uniform ({@code [min, max]}) number of times. */
    public record Count(int min, int max) implements Modifier {}

    /** {@code minecraft:noise_based_count}: {@code ceil((biomeInfoNoise(x / factor, z / factor) + offset) * ratio)} times. */
    public record NoiseCount(int ratio, double factor, double offset) implements Modifier {}

    /** {@code minecraft:block_predicate_filter}. */
    public record Filter(Predicate predicate) implements Modifier {}

    /** {@code tfc:intertidal}: the height above the tide level, truncated, in {@code [min, max]}. */
    public record Intertidal(int min, int max) implements Modifier {}

    /** {@code tfc:no_solid_neighbors}: the four horizontal neighbours are replaceable or snow-pileable. */
    public record NoSolidNeighbors() implements Modifier {}

    /** {@code tfc:shallow_water}: water below for {@code [min, max)} blocks; one float decides with the depth. */
    public record ShallowWater(int minDepth, int maxDepth) implements Modifier {}

    /** A block predicate of a {@link Filter}. */
    public sealed interface Predicate {}

    /** {@code tfc:air_or_empty_fluid}. */
    public record AirOrEmptyFluid() implements Predicate {}

    /** {@code tfc:replaceable}: {@code EnvironmentHelpers.isWorldgenReplaceable}. */
    public record Replaceable() implements Predicate {}

    /** {@code minecraft:would_survive}: the state's {@code canSurvive} at the position. */
    public record WouldSurvive(PlantState state) implements Predicate {}

    /** {@code tfc:would_survive_with_fluid}: the state filled with the position's fluid, then {@code canSurvive}. */
    public record WouldSurviveWithFluid(PlantState state) implements Predicate {}

    /** {@code tfc:dry_replaceable}: replaceable and without fluid. */
    public record DryReplaceable() implements Predicate {}

    /** {@code minecraft:matching_blocks} of {@code tfc:fluid/salt_water}: TFC's salt water block. */
    public record SaltWaterBlock() implements Predicate {}

    /** A configured feature. */
    public sealed interface Configured {}

    /**
     * {@code minecraft:random_patch} ({@code dynamic} false) or {@code tfc:dynamic_random_patch} (tries scaled by height and
     * forest density): {@code tries} positions around the origin, each running {@code feature}.
     */
    public record Patch(boolean dynamic, int tries, int xzSpread, int ySpread, Placed feature) implements Configured {}

    /**
     * {@code minecraft:simple_block} ({@code withFluid} false) or {@code tfc:block_with_fluid}: the state (its age drawn when
     * {@code randomAge}), placed when it survives.
     */
    public record SimpleBlock(PlantState state, boolean randomAge, boolean withFluid) implements Configured {}

    /** {@code tfc:tall_plant}: {@code TFCTallGrassBlock.placeTwoHalves} (the age drawn for a plant that has one). */
    public record TallPlant(PlantState state) implements Configured {}

    /** {@code tfc:emergent_plant}: the two halves of a tall water plant when the block above holds no fluid. */
    public record EmergentPlant(PlantState state) implements Configured {}

    /** {@code tfc:submerged_tall_plant}: the two halves of a tall water plant when both blocks hold the same fluid. */
    public record SubmergedTallPlant(PlantState state) implements Configured {}

    /** {@code tfc:tall_wild_crop}: {@code WildDoubleCropBlock.placeTwoHalves}. */
    public record TallWildCrop(PlantState state) implements Configured {}

    /** {@code tfc:spreading_crop}: the crop with its fruit on a random horizontal side, when that side can hold it. */
    public record SpreadingCrop(PlantState state) implements Configured {}

    /** {@code tfc:noisy_multiple}: the features in an order rotated by the temperature, stopping after two placed. */
    public record NoisyMultiple(List<Placed> features) implements Configured {
        public NoisyMultiple {
            features = List.copyOf(features);
        }
    }

    /** {@code tfc:coral_tree}, {@code tfc:coral_mushroom}, {@code tfc:coral_claw}. */
    public record Coral(Corals.Shape shape) implements Configured {}

    /**
     * {@code tfc:creeping_plant} ({@code ocean} false) or {@code tfc:creeping_ocean_plant}: the creeping plant on every
     * replaceable position of a disc-ish volume above (land) or below (ocean) the tide that it can creep on.
     */
    public record Creeping(PlantState state, int radius, int height, int tideHeight, float integrity, boolean ocean)
        implements Configured {}

    /** {@code tfc:epiphyte_plant} and {@code tfc:rotatable_water_plant}: the plant facing the first direction it survives with. */
    public record Attached(PlantState state, boolean rotatable) implements Configured {}

    /** The growth of a {@link Column}: {@code tfc:twisting_vines}, {@code tfc:weeping_vines}, {@code tfc:kelp}. */
    public enum ColumnKind { TWISTING, WEEPING, KELP }

    /** {@code ColumnPlantConfig} features: {@code tries} columns of body blocks ending in a head. */
    public record Column(ColumnKind kind, PlantState body, PlantState head, int tries, int radius, int minHeight, int maxHeight)
        implements Configured {}

    /** {@code tfc:vines}: a vine on the first horizontal face it can hang on, and a column below. */
    public record Vines(PlantState state) implements Configured {}

    /** {@code tfc:kelp_tree}: {@code KelpTreeFlowerBlock.generatePlant}. */
    public record GiantKelp(PlantState flower, PlantState body) implements Configured {}

    /** {@code tfc:branching_cactus}: {@code GrowingBranchingCactusBlock.growRecursively}, height 8. */
    public record BranchingCactus(PlantState head, PlantState body, PlantState fruit) implements Configured {}
}
