/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/tree/TreeHelpers.java @ c3b4d9536 (the mangrove's special root placer is
 * not ported), with RandomTreeFeature.java, OverlayTreeFeature.java, StackedTreeFeature.java and KrummholzFeature.java (place)
 * in the same package, and
 * the parts of Minecraft 1.21's StructurePlaceSettings (getRandomPalette, getRandom), StructureTemplate (processBlockInfos,
 * transform) and BlockRotProcessor that TreeHelpers.placeTemplate runs
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.BranchDirection.Mirror;
import dev.tfp.core.worldgen.tree.BranchDirection.Rotation;
import dev.tfp.core.worldgen.ore.Weighted;

/**
 * Places one tree feature into a {@link TreeWorld} with TFC's checks and draws, in TFC's order. Pure: it reads the world and
 * records what it sets.
 */
final class TreePlacer {
    private static final Rotation[] ROTATION_VALUES = Rotation.values();
    private static final Mirror[] MIRROR_VALUES = Mirror.values();

    private TreePlacer() {}

    /** The random rotation and mirror of {@code TreeHelpers.getPlacementSettings} (drawn in that order). */
    record Settings(Rotation rotation, Mirror mirror) {
        static Settings random(XoroshiroRandomSource random) {
            final Rotation rotation = ROTATION_VALUES[random.nextInt(ROTATION_VALUES.length)];
            final Mirror mirror = MIRROR_VALUES[random.nextInt(MIRROR_VALUES.length)];
            return new Settings(rotation, mirror);
        }
    }

    /** {@code ConfiguredFeature.place} of a tree feature at {@code (x, y, z)}. */
    static boolean place(TreeFeature feature, TreeWorld world, XoroshiroRandomSource random, int x, int y, int z) {
        return switch (feature) {
            case TreeFeature.RandomTree f -> randomTree(f, world, random, x, y, z);
            case TreeFeature.OverlayTree f -> overlayTree(f, world, random, x, y, z);
            case TreeFeature.StackedTree f -> stackedTree(f, world, random, x, y, z);
            case TreeFeature.Krummholz f -> krummholz(f, world, random, x, y, z);
        };
    }

    /** {@code RandomTreeFeature.place}. */
    static boolean randomTree(TreeFeature.RandomTree config, TreeWorld world, XoroshiroRandomSource random, int x, int y, int z) {
        final Settings settings = Settings.random(random);
        final TreeTemplate structure = config.structures().get(random.nextInt(config.structures().size()));
        if (isValidLocation(world, x, y, z, settings, config.placement())) {
            final boolean placeTree = config.rootSystem().map(roots -> placeRoots(world, x, y, z, roots, random) || !roots.required()).orElse(true);
            if (placeTree) {
                int top = y;
                if (config.trunk().isPresent()) {
                    top += placeTrunk(world, x, y, z, random, settings, config.trunk().get());
                }
                placeTemplate(world, random, structure, settings, x, top, z, Float.NaN);
                return true;
            }
            return false;
        }
        return false;
    }

    /** {@code OverlayTreeFeature.place}: the overlay goes through a {@code BlockRotProcessor} of the overlay integrity. */
    static boolean overlayTree(TreeFeature.OverlayTree config, TreeWorld world, XoroshiroRandomSource random, int x, int y, int z) {
        final Settings settings = Settings.random(random);
        if (isValidLocation(world, x, y, z, settings, config.placement())) {
            final boolean placeTree = config.rootSystem().map(roots -> placeRoots(world, x, y, z, roots, random) || !roots.required()).orElse(true);
            if (placeTree) {
                int top = y;
                if (config.trunk().isPresent()) {
                    top += placeTrunk(world, x, y, z, random, settings, config.trunk().get());
                }
                placeTemplate(world, random, config.base(), settings, x, top, z, Float.NaN);
                placeTemplate(world, random, config.overlay(), settings, x, top, z, config.overlayIntegrity());
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * {@code StackedTreeFeature.place}: only the ground is checked, and a root system is placed twice (once in the condition,
     * once after it), as TFC does.
     */
    static boolean stackedTree(TreeFeature.StackedTree config, TreeWorld world, XoroshiroRandomSource random, int x, int y, int z) {
        final Settings settings = Settings.random(random);
        if (isValidGround(world, x, y, z, settings, config.placement())) {
            final boolean placeTree = config.rootSystem().isPresent()
                && (placeRoots(world, x, y, z, config.rootSystem().get(), random) || !config.rootSystem().get().required())
                || config.rootSystem().isEmpty();
            if (placeTree) {
                config.rootSystem().ifPresent(roots -> placeRoots(world, x, y, z, roots, random));
                int top = y + placeTrunk(world, x, y, z, random, settings, config.trunk());
                for (TreeFeature.Layer layer : config.layers()) {
                    final int layerCount = layer.count(random);
                    for (int i = 0; i < layerCount; i++) {
                        final TreeTemplate structure = layer.templates().get(random.nextInt(layer.templates().size()));
                        placeTemplate(world, random, structure, settings, x, top, z, Float.NaN);
                        top += structure.sizeY();
                    }
                }
                return true;
            }
        }
        return false;
    }

    /** {@code KrummholzFeature.place}: the column stops at the first block that is not replaceable, which it still sets. */
    static boolean krummholz(TreeFeature.Krummholz config, TreeWorld world, XoroshiroRandomSource random, int x, int y, int z) {
        final int height = config.sampleHeight(random);
        final TreeGround below = world.get(x, y - 1, z);
        boolean validBlock = below.treeGrowsOn();
        if (config.spawnsOnStone()) {
            validBlock |= below.isStone();
        }
        if (config.spawnsOnGravel()) {
            validBlock |= below.isGravel();
        }
        if (!validBlock) {
            return false;
        }
        // KrummholzBlock.canSurvive: krummholz below, or BottomSupportedDeviceBlock.canSurvive (a sturdy top below)
        final boolean canSurvive = below == TreeGround.KRUMMHOLZ || below.sturdyTop();
        if (!canSurvive || world.get(x, y, z).hasFluid()) {
            return false;
        }
        int maxHeight = 1;
        for (int i = 0; i < height; i++) {
            maxHeight = i + 1;
            if (!world.get(x, y + i, z).isWorldgenReplaceable()) {
                break;
            }
        }
        for (int i = 0; i < maxHeight; i++) {
            world.set(x, y + i, z, TreeBlock.Kind.KRUMMHOLZ, TreeBlock.Axis.Y, BranchDirection.NONE, true);
        }
        return true;
    }

    /** {@code TreeHelpers.isValidLocation}. */
    static boolean isValidLocation(TreeWorld world, int x, int y, int z, Settings settings, TreeFeature.Placement config) {
        return isValidGround(world, x, y, z, settings, config) && isValidTrunk(world, x, y, z, settings, config);
    }

    /** {@code TreeHelpers.isValidGround}: the {@code width x width} ground under the trunk (Java division, as TFC). */
    static boolean isValidGround(TreeWorld world, int px, int py, int pz, Settings settings, TreeFeature.Placement config) {
        final int[] p = new int[3];
        for (int x = (1 - config.width()) / 2; x <= config.width() / 2; x++) {
            for (int z = (1 - config.width()) / 2; z <= config.width() / 2; z++) {
                transform(x, 0, z, settings, p);
                final int ax = p[0] + px;
                final int ay = p[1] + py;
                final int az = p[2] + pz;
                if (config.groundType() == TreeFeature.GroundType.FLOATING) {
                    return isValidFloatingPosition(world, ax, ay, az);
                } else if (!(config.mayPlaceUnderwater() ? isValidPositionPossiblyUnderwater(world, ax, ay, az, config)
                    : isValidPosition(world, ax, ay, az, config))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValidPosition(TreeWorld world, int x, int y, int z, TreeFeature.Placement config) {
        final TreeGround stateAt = world.get(x, y, z);
        final boolean isInWater = stateAt.isFreshWater();
        if (!(config.mayPlaceInWater() && stateAt.isAirOrEmptyFluid() && isInWater) && !stateAt.isAir()) {
            return false; // TFC also accepts a sapling here; the worldgen has none
        }
        final TreeGround stateBelow = world.get(x, y - 1, z);
        boolean treeGrowsOn = stateBelow.treeGrowsOn();
        if (!treeGrowsOn && config.groundType() == TreeFeature.GroundType.SAND) {
            treeGrowsOn = stateBelow.isSand();
        }
        if (!treeGrowsOn && config.mayPlaceInWater() && isInWater) {
            treeGrowsOn = stateBelow.seaBushPlantableOn();
        }
        return treeGrowsOn;
    }

    private static boolean isValidFloatingPosition(TreeWorld world, int x, int y, int z) {
        int cy = y;
        for (int i = 0; i <= 3; i++) {
            if (!world.get(x, cy, z).isWorldgenReplaceable()) {
                return false;
            }
            cy--;
            final TreeGround stateBelow = world.get(x, cy, z);
            if (stateBelow.bushPlantableOn() || stateBelow.seaBushPlantableOn() || stateBelow.hasFluid()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidPositionPossiblyUnderwater(TreeWorld world, int x, int y, int z, TreeFeature.Placement config) {
        final TreeGround stateAt = world.get(x, y, z);
        // TFC refuses river water first; the terrain has no river water yet
        final boolean water = stateAt.isFreshWater() || (!config.requiresFreshwater() && stateAt == TreeGround.SALT_WATER);
        final TreeGround stateBelow = world.get(x, y - 1, z);
        if (water) {
            return stateBelow.seaBushPlantableOn();
        }
        if (!stateAt.hasFluid()) {
            boolean treeGrowsOn = stateBelow.treeGrowsOn();
            if (!treeGrowsOn && config.groundType() == TreeFeature.GroundType.SAND) {
                treeGrowsOn = stateBelow.isSand();
            }
            return treeGrowsOn;
        }
        return false;
    }

    /** {@code TreeHelpers.isValidTrunk}: air (or a fluid, for underwater trees) above the ground up to the placement height. */
    static boolean isValidTrunk(TreeWorld world, int px, int py, int pz, Settings settings, TreeFeature.Placement config) {
        final boolean underwater = config.mayPlaceUnderwater();
        final int[] p = new int[3];
        for (int x = (1 - config.width()) / 2; x <= config.width() / 2; x++) {
            for (int z = (1 - config.width()) / 2; z <= config.width() / 2; z++) {
                for (int y = 1; y < config.height(); y++) {
                    transform(x, y, z, settings, p);
                    final TreeGround stateAt = world.get(p[0] + px, p[1] + py, p[2] + pz);
                    if (!(underwater ? stateAt.isAirOrEmptyFluid() : stateAt.isAir())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * {@code TreeHelpers.placeTemplate} at the trunk top {@code (x, y, z)}: the template's corner is the top minus the
     * transformed centre. Vanilla's {@code getRandomPalette} draws {@code nextInt(1)} for the single palette; with an
     * {@code integrity} (not NaN) each block draws a float and is kept when it is at most the integrity
     * ({@code BlockRotProcessor}). A kept block replaces air, a fluid or leaves.
     */
    static void placeTemplate(TreeWorld world, XoroshiroRandomSource random, TreeTemplate template, Settings settings, int x, int y,
                              int z, float integrity) {
        final int[] c = new int[3];
        transform((template.sizeX() - 1) / 2, 0, (template.sizeZ() - 1) / 2, settings, c);
        final int ox = x - c[0];
        final int oy = y - c[1];
        final int oz = z - c[2];
        random.nextInt(1);
        final boolean rot = !Float.isNaN(integrity);
        final int[] p = new int[3];
        // processBlockInfos: every block is processed (and drawn for) before any is placed; the placing reads no random
        final boolean[] kept = new boolean[template.blocks().size()];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = !rot || random.nextFloat() <= integrity;
        }
        for (int i = 0; i < kept.length; i++) {
            if (!kept[i]) {
                continue;
            }
            final TreeTemplate.Block block = template.blocks().get(i);
            transform(block.x(), block.y(), block.z(), settings, p);
            final int ax = p[0] + ox;
            final int ay = p[1] + oy;
            final int az = p[2] + oz;
            final TreeGround stateAt = world.get(ax, ay, az);
            if (stateAt.isWorldgenReplaceable() || stateAt.isLeaves()) {
                world.set(ax, ay, az, block.kind(), block.wood(), block.axis().rotate(settings.rotation()),
                    block.branch().mirror(settings.mirror()).rotate(settings.rotation()), false);
            }
        }
    }

    /** {@code TreeHelpers.placeTrunk}: returns the height. A trunk keeps its axis; its branch direction is transformed. */
    static int placeTrunk(TreeWorld world, int x, int y, int z, XoroshiroRandomSource random, Settings settings, TreeFeature.Trunk trunk) {
        final int height = trunk.height(random);
        for (int dy = 0; dy < height; dy++) {
            if (trunk.wide()) {
                trunkBlock(world, settings, trunk, x, y, z, 0, dy, 0, BranchDirection.TRUNK_SOUTH_EAST);
                trunkBlock(world, settings, trunk, x, y, z, 0, dy, 1, BranchDirection.TRUNK_NORTH_EAST);
                trunkBlock(world, settings, trunk, x, y, z, 1, dy, 0, BranchDirection.TRUNK_SOUTH_WEST);
                trunkBlock(world, settings, trunk, x, y, z, 1, dy, 1, BranchDirection.TRUNK_NORTH_WEST);
            } else {
                trunkBlock(world, settings, trunk, x, y, z, 0, dy, 0, BranchDirection.DOWN);
            }
        }
        return height;
    }

    private static void trunkBlock(TreeWorld world, Settings settings, TreeFeature.Trunk trunk, int x, int y, int z, int dx, int dy,
                                   int dz, BranchDirection branch) {
        final int[] p = new int[3];
        transform(dx, dy, dz, settings, p);
        world.set(p[0] + x, p[1] + y, p[2] + z, TreeBlock.Kind.LOG, trunk.wood(), trunk.axis(),
            branch.mirror(settings.mirror()).rotate(settings.rotation()), true);
    }

    /**
     * {@code TreeHelpers.placeRoots} for the trunk base {@code (x, y, z)}: {@code tries} roots around the block below it, each
     * with a triangle x and z offset and a downward y offset; a root replaces a listed ground kind with a weighted pick.
     */
    static boolean placeRoots(TreeWorld world, int x, int y, int z, TreeFeature.Roots config, XoroshiroRandomSource random) {
        final int oy = y - 1;
        for (int i = 0; i < config.tries(); i++) {
            final int dx = triangle(random, config.width());
            final int dz = triangle(random, config.width());
            final int dy = -random.nextInt(config.height());
            final int cx = x + dx;
            final int cy = oy + dy;
            final int cz = z + dz;
            final Weighted<TreeBlock.Kind> weighted = config.blocks().get(world.get(cx, cy, cz));
            if (weighted != null) {
                world.set(cx, cy, cz, weighted.get(random.nextDouble()), TreeBlock.Axis.Y, BranchDirection.NONE, true);
            }
        }
        return true;
    }

    /** {@code Helpers.triangle}. */
    static int triangle(XoroshiroRandomSource random, int range) {
        return random.nextInt(range) - random.nextInt(range);
    }

    /** {@code TreeHelpers.transform}: mirror, then rotate about the origin; the result goes to {@code out}. */
    static void transform(int x, int y, int z, Settings settings, int[] out) {
        int px = x;
        int pz = z;
        switch (settings.mirror()) {
            case LEFT_RIGHT -> pz = -pz;
            case FRONT_BACK -> px = -px;
            default -> { }
        }
        switch (settings.rotation()) {
            case COUNTERCLOCKWISE_90 -> {
                out[0] = pz;
                out[2] = -px;
            }
            case CLOCKWISE_90 -> {
                out[0] = -pz;
                out[2] = px;
            }
            case CLOCKWISE_180 -> {
                out[0] = -px;
                out[2] = -pz;
            }
            default -> {
                out[0] = px;
                out[2] = pz;
            }
        }
        out[1] = y;
    }
}
