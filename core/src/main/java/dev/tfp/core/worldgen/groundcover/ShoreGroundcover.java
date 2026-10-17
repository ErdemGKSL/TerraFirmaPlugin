/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/BlockWithFluidFeature.java @ c3b4d9536 (place), with
 * src/main/java/net/dries007/tfc/world/placement/IntertidalPlacement.java (isValid), the data of
 * src/main/resources/data/tfc/worldgen/{configured_feature,placed_feature}/{driftwood,clam,mollusk,mussel,seaweed,sticks_shore}{,_patch}.json,
 * the tag data/tfc/tags/worldgen/placed_feature/feature/shore_decorations.json (order) and the biomes whose
 * in_biome/surface_decoration tag lists it, and the vanilla 1.21 steps they name (rarity_filter, in_square, random_patch,
 * heightmap OCEAN_FLOOR_WG, block_predicate_filter).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TFC's shore groundcover ({@code #tfc:feature/shore_decorations}): driftwood and sticks above the tide, clams, mollusks and
 * mussels in salt water, seaweed on any shore floor. Each is a {@code random_patch} behind a rarity filter, an
 * {@code in_square} origin and a climate filter, with each try on the ocean floor and filtered by its block predicate and
 * tide band. Only the shore biomes decorate with them. Pure and thread-safe.
 *
 * <p>Not ported: guano (it needs the {@code creeping_stone_plantable_on} tag of the block below, ledger).
 */
public final class ShoreGroundcover {
    /** What the block at the try position must be. */
    public enum Target {
        /** {@code tfc:air_or_empty_fluid}: air or a plain fluid block. */
        AIR_OR_FLUID,
        /** {@code matching_blocks tfc:fluid/salt_water}. */
        SALT_WATER
    }

    /**
     * One patch feature.
     *
     * @param feature  the placed feature id
     * @param kind     the groundcover kind
     * @param block    the block id
     * @param rarity   {@code rarity_filter} chance: one chunk in {@code rarity}
     * @param tries    the patch's tries
     * @param xzSpread the patch's horizontal spread
     * @param climate  the climate filter at the origin, or null
     * @param target   the block predicate of a try
     * @param minTide  the {@code tfc:intertidal} band (height above the tide level), or {@link Integer#MIN_VALUE} for none
     * @param maxTide  the upper end of the band
     */
    public record Patch(String feature, Groundcover.Kind kind, String block, int rarity, int tries, int xzSpread,
                        ClimateFilter climate, Target target, int minTide, int maxTide) {
        boolean intertidal() {
            return minTide != Integer.MIN_VALUE;
        }
    }

    private static final int NO_TIDE = Integer.MIN_VALUE;

    /** The patches of {@code shore_decorations}, in tag order (guano left out). */
    public static final List<Patch> PATCHES = List.of(
        new Patch("tfc:driftwood_patch", Groundcover.Kind.DRIFTWOOD, "tfp:groundcover/driftwood", 2, 5, 15,
            null, Target.AIR_OR_FLUID, 1, 1),
        new Patch("tfc:clam_patch", Groundcover.Kind.CLAM, "tfp:groundcover/clam", 6, 5, 15,
            ClimateFilter.of(-50, 22, 10, 450), Target.SALT_WATER, NO_TIDE, NO_TIDE),
        new Patch("tfc:mollusk_patch", Groundcover.Kind.MOLLUSK, "tfp:groundcover/mollusk", 6, 5, 15,
            ClimateFilter.of(-10, 30, 150, 500), Target.SALT_WATER, NO_TIDE, NO_TIDE),
        new Patch("tfc:mussel_patch", Groundcover.Kind.MUSSEL, "tfp:groundcover/mussel", 6, 5, 15,
            ClimateFilter.of(10, 50, 100, 500), Target.SALT_WATER, NO_TIDE, NO_TIDE),
        new Patch("tfc:seaweed_patch", Groundcover.Kind.SEAWEED, "tfp:groundcover/seaweed", 5, 8, 15,
            ClimateFilter.of(-20, 50, 150, 500), Target.AIR_OR_FLUID, NO_TIDE, NO_TIDE),
        new Patch("tfc:sticks_shore_patch", Groundcover.Kind.STICK, "tfp:groundcover/stick", 2, 18, 15,
            ClimateFilter.of(-50, 50, 50, 500), Target.AIR_OR_FLUID, 1, 10)
    );

    /** The biomes whose surface decoration tag lists {@code #tfc:feature/shore_decorations}. */
    static final Set<String> BIOME_NAMES = Set.of("coastal_dunes", "embayments", "guano_island", "ice_sheet_shore",
        "old_shield_volcano_shore", "rocky_shores", "sea_stacks", "setback_cliffs", "shield_volcano_shore", "shore",
        "terrace_lower", "terrace_upper", "tidal_flats", "tower_karst_bay");

    private static final boolean[] DECORATED = decorated();

    private final FeatureRandom[] randoms;

    public ShoreGroundcover(long levelSeed) {
        this.randoms = new FeatureRandom[PATCHES.size()];
        for (int i = 0; i < randoms.length; i++) {
            randoms[i] = new FeatureRandom(levelSeed, PATCHES.get(i).feature());
        }
    }

    /** Whether a biome is decorated with the shore groundcover. */
    public static boolean decorates(int biome) {
        return biome >= 0 && biome < DECORATED.length && DECORATED[biome];
    }

    /**
     * The shore groundcover inside chunk {@code (chunkX, chunkZ)}, in tag order, then chunk order. A chunk decorates when the
     * biome at its centre is a shore biome (ledger).
     */
    public void place(int chunkX, int chunkZ, GroundView view, List<Groundcover> out) {
        for (int p = 0; p < PATCHES.size(); p++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final int cx = chunkX + dx;
                    final int cz = chunkZ + dz;
                    if (!decorates(view.biome((cx << 4) + 8, (cz << 4) + 8))) {
                        continue;
                    }
                    patch(randoms[p].chunk(cx, cz), PATCHES.get(p), cx << 4, cz << 4, view, g -> {
                        if (g.inChunk(chunkX, chunkZ)) {
                            out.add(g);
                        }
                    });
                }
            }
        }
    }

    /**
     * One chunk's patch with TFC's draws in TFC's order: the rarity float, {@code in_square} (x, then z), the climate filter
     * at the origin (whose y is the world's lowest, as vanilla's decoration origin), then per try the {@code random_patch}
     * offsets (x, y, z). The placed feature of a try draws nothing.
     */
    public static void patch(XoroshiroRandomSource random, Patch patch, int blockX, int blockZ, GroundView view, Consumer<Groundcover> out) {
        if (!(random.nextFloat() < 1f / patch.rarity())) {
            return;
        }
        final int originX = blockX + random.nextInt(16);
        final int originZ = blockZ + random.nextInt(16);
        if (patch.climate() != null && !patch.climate().isValid(view, originX, view.minY(), originZ, random)) {
            return;
        }
        final Set<Long> placed = new HashSet<>();
        final int xz = patch.xzSpread() + 1;
        for (int i = 0; i < patch.tries(); i++) {
            final int x = originX + random.nextInt(xz) - random.nextInt(xz);
            random.nextInt(2);
            random.nextInt(2);
            final int z = originZ + random.nextInt(xz) - random.nextInt(xz);
            final int y = view.oceanFloor(x, z);
            if (y <= view.minY()) {
                continue;
            }
            final long key = LooseRocks.key(x, y, z);
            if (placed.contains(key)) {
                continue;
            }
            final boolean water = view.water(x, y, z);
            final boolean target = switch (patch.target()) {
                case AIR_OR_FLUID -> true;
                case SALT_WATER -> water && view.saltWater(x, z);
            };
            // would_survive_with_fluid: the floor below is a full block, so it survives
            if (!target || (patch.intertidal() && !intertidal(view, x, y, z, patch.minTide(), patch.maxTide()))) {
                continue;
            }
            placed.add(key);
            out.accept(new Groundcover(x, y, z, patch.kind(), patch.block(), 1, water));
        }
    }

    /** {@code IntertidalPlacement.isValid}: the position's height above the tide level, truncated, inside the band. */
    static boolean intertidal(GroundView view, int x, int y, int z, int minElevation, int maxElevation) {
        final int heightDiff = (int) (y - view.tideLevel(x, z));
        return minElevation <= heightDiff && heightDiff <= maxElevation;
    }

    private static boolean[] decorated() {
        final boolean[] out = new boolean[TfcBiomes.COUNT];
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            out[id] = BIOME_NAMES.contains(TfcBiomes.name(id).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
