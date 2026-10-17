/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's forest feature (the trees: forest type chance and counts, entry choice by climate, krummholz, old
 * growth, dead and normal trees, and the random, overlay, stacked and krummholz tree features with their trunks, roots and
 * templates) at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from TFC's own classes, compiled in a scratch oracle: {@code ForestFeature} (place up to the
 * tree loop, placeTree, getTrees, getTree; the soil disc call, bushes, groundcover, leaf piles and fallen trees removed),
 * {@code ForestConfig} and the tree configs (codecs removed), {@code TreeHelpers} (the structure manager lookup removed),
 * {@code RandomTreeFeature}, {@code OverlayTreeFeature}, {@code StackedTreeFeature}, {@code KrummholzFeature},
 * {@code ForestType}, {@code BranchDirection}, {@code ClimatePlacement}, {@code IWeighted}/{@code Weighted} and
 * {@code Helpers.triangle}, with Minecraft 1.21.11's {@code RandomSource}, {@code XoroshiroRandomSource},
 * {@code Xoroshiro128PlusPlus} and {@code RandomSupport}. Stubs: {@code BlockPos}, {@code BlockState} (block name and
 * properties; mirror/rotate as TFC's {@code LogBlock}), {@code WorldGenLevel} (the terrain of {@link TreeTestView}),
 * {@code ChunkData} (its climate and forest type), the block and fluid tags (their members at the pinned commit over the
 * harness's names), {@code StructurePlaceSettings}, {@code StructureTemplate} (palette order and {@code processBlockInfos}),
 * {@code BlockIgnoreProcessor}, {@code BlockRotProcessor} and {@code Mth.clampedMap}, written from the 1.21.11 server bytecode.
 * The templates were the datagen export of TFC's {@code .nbt} files. Nothing was run on a server.
 *
 * <p>Each chunk {@code (cx, cz)} seeds {@code new XoroshiroRandomSource(seed ^ cx * 341873128712L ^ cz * 132897987541L ^
 * 12 * 0x9E3779B97F4A7C15L)}, runs the forest, and hashes (FNV-1a 64) the line {@code "cx cz|trees|x,y,z,block[,axis,branch];
 * ...|<next long of the random>"}, so the placements and the number of draws both match. One golden per seed and chunk column
 * {@code cx} in [-5, 4], over {@code cz} in [-5, 4]: 400 chunks with 485 trees (roots, krummholz and 2 x 2 trunks
 * included), negative coordinates included.
 */
class TreeGoldenTest {
    record Golden(long seed, int cx, int trees, int blocks, long hash) {}

    record LayerGolden(long seed, long hash, int distinct, String first) {}

    /**
     * The forest type layer: TFC's {@code TFCLayers.createOverworldForestLayer} (the artist calls removed) with
     * {@code ForestInitLayer}, {@code ForestRandomizeLayer}, {@code ForestEdgeLayer}, {@code ForestRandomizeSmallLayer} and
     * {@code ForestType} (codecs removed) at c3b4d9536, run on the layer framework, {@code ZoomLayer} and
     * {@code OpenSimplex2D} of the chunk data oracle against the bootstrapped 1.21.11 server jar. FNV-1a 64 of
     * {@code "x,z,ordinal;"} over chunk x in [-96, 96) step 3 and z in [-96, 96) step 2, the number of distinct types and the
     * first 40 ordinals.
     */
    static final List<LayerGolden> LAYER = List.of(
        new LayerGolden(0L, -939784997691356014L, 28, "16 16 16 16 16 16 16 16 21 21 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 2 2 2 2 2 5 5 14 5 5 5 5 5 5 5 5"),
        new LayerGolden(42L, -5928287674109752725L, 28, "15 15 3 3 20 16 16 16 16 21 21 21 0 0 0 0 0 0 0 0 0 2 2 2 2 2 2 2 2 2 2 2 22 22 3 18 18 20 20 20 16 23 23 23 0 0 0 0 0 0"),
        new LayerGolden(-7654321L, -3140061748264728249L, 29, "0 0 0 0 0 0 0 0 22 22 3 3 21 21 15 15 15 15 15 15 12 11 11 11 11 11 21 2 2 2 2 2 2 24 24 2 2 2 2 0 0 0 0 0 0 0 2 2 2 2 2"),
        new LayerGolden(123456789012345L, -5039883670707517076L, 26, "15 15 15 15 23 23 2 2 2 2 2 23 23 23 15 23 23 23 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 3 3 3 5 5 5 5 5"));

    static final List<Golden> GOLDEN = List.of(
        new Golden(0L, -5, 5, 847, -5156374032248712083L),
        new Golden(0L, -4, 12, 1168, 5270801478593981196L),
        new Golden(0L, -3, 19, 2277, -1546557609285048013L),
        new Golden(0L, -2, 15, 1095, 596600948043827279L),
        new Golden(0L, -1, 11, 1882, -1921835359853815671L),
        new Golden(0L, 0, 11, 1433, -4876438301116345039L),
        new Golden(0L, 1, 20, 2117, -6815650613195924619L),
        new Golden(0L, 2, 11, 1085, 2057079100704041426L),
        new Golden(0L, 3, 10, 1223, 5344062406176984898L),
        new Golden(0L, 4, 13, 2150, -7432038791962701745L),
        new Golden(42L, -5, 7, 932, -8627887534032804642L),
        new Golden(42L, -4, 13, 1364, 1102096209803158329L),
        new Golden(42L, -3, 14, 1909, -6642771294440190944L),
        new Golden(42L, -2, 10, 678, 6434008749400949266L),
        new Golden(42L, -1, 11, 1102, 3551909952487333452L),
        new Golden(42L, 0, 11, 1637, 214653941550135915L),
        new Golden(42L, 1, 15, 1762, 928715587025560234L),
        new Golden(42L, 2, 14, 1639, -2620911346887264011L),
        new Golden(42L, 3, 7, 1208, -7280716058425782311L),
        new Golden(42L, 4, 16, 1982, -3980803923496906653L),
        new Golden(-7654321L, -5, 9, 1434, 2908744737980034757L),
        new Golden(-7654321L, -4, 18, 2313, 6985670883960014335L),
        new Golden(-7654321L, -3, 14, 1656, -5336992022823655219L),
        new Golden(-7654321L, -2, 15, 1238, -3510116906366758952L),
        new Golden(-7654321L, -1, 10, 1142, 4146905894565605870L),
        new Golden(-7654321L, 0, 14, 1554, 3448977015988188804L),
        new Golden(-7654321L, 1, 16, 2311, 7700909163187284056L),
        new Golden(-7654321L, 2, 15, 1797, -6715515308709179483L),
        new Golden(-7654321L, 3, 7, 941, -1372025642063885904L),
        new Golden(-7654321L, 4, 13, 2356, -2145733086380905983L),
        new Golden(123456789012345L, -5, 8, 646, 317431085417119890L),
        new Golden(123456789012345L, -4, 8, 1115, -3453284150959423975L),
        new Golden(123456789012345L, -3, 8, 1507, 2297326607028493252L),
        new Golden(123456789012345L, -2, 12, 862, 2798106220369351810L),
        new Golden(123456789012345L, -1, 14, 1834, -5043162747853979124L),
        new Golden(123456789012345L, 0, 18, 1869, 143348275869132888L),
        new Golden(123456789012345L, 1, 7, 800, -5658843818215000131L),
        new Golden(123456789012345L, 2, 17, 2048, -4321293582521680955L),
        new Golden(123456789012345L, 3, 8, 1308, 4176418762101448131L),
        new Golden(123456789012345L, 4, 9, 841, -5087878827474430310L));

    static long fnv(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** The oracle's block name: {@code log/oak}, {@code wood/oak}, {@code leaves/oak}, {@code krummholz/aspen}, {@code rooted_dirt}, {@code muddy_roots}. */
    static String name(TreeBlock b) {
        return switch (b.kind()) {
            case LOG -> "log/" + b.wood().serializedName();
            case WOOD -> "wood/" + b.wood().serializedName();
            case LEAVES -> "leaves/" + b.wood().serializedName();
            case KRUMMHOLZ -> "krummholz/" + b.wood().serializedName();
            case ROOTED_DIRT -> "rooted_dirt";
            case MUDDY_ROOTS -> "muddy_roots";
        };
    }

    static String log(List<Tree> trees) {
        final StringBuilder out = new StringBuilder();
        for (Tree tree : trees) {
            for (TreeBlock b : tree.blocks()) {
                out.append(tree.x() + b.dx()).append(',').append(tree.y() + b.dy()).append(',').append(tree.z() + b.dz()).append(',').append(name(b));
                if (b.kind().isLog()) {
                    out.append(',').append(b.axis().name().toLowerCase(Locale.ROOT)).append(',').append(b.branch().serializedName());
                }
                out.append(';');
            }
        }
        return out.toString();
    }

    @Test
    void forestMatchesTfc() {
        final Forest forest = new Forest(0, TreeTestData.entries());
        final TreeView view = new TreeTestView();
        for (Golden golden : GOLDEN) {
            long hash = 0xcbf29ce484222325L;
            int blocks = 0;
            int trees = 0;
            for (int cz = -5; cz < 5; cz++) {
                final XoroshiroRandomSource r = new XoroshiroRandomSource(golden.seed() ^ (golden.cx() * 341873128712L)
                    ^ (cz * 132897987541L) ^ (12 * 0x9E3779B97F4A7C15L));
                final List<Tree> placed = forest.trees(r, golden.cx() << 4, cz << 4, view);
                hash = fnv(hash, golden.cx() + " " + cz + "|" + placed.size() + "|" + log(placed) + "|" + r.nextLong() + "\n");
                trees += placed.size();
                for (Tree t : placed) {
                    blocks += t.blocks().size();
                }
            }
            assertThat(new Golden(golden.seed(), golden.cx(), trees, blocks, hash)).as("seed %d cx %d", golden.seed(), golden.cx())
                .isEqualTo(golden);
        }
    }

    @Test
    void forestLayerMatchesTfc() {
        for (LayerGolden golden : LAYER) {
            final ForestLayers layers = new ForestLayers(golden.seed());
            long hash = 0xcbf29ce484222325L;
            final boolean[] seen = new boolean[ForestType.values().length];
            final StringBuilder first = new StringBuilder();
            for (int x = -96; x < 96; x += 3) {
                for (int z = -96; z < 96; z += 2) {
                    final ForestType t = layers.forestType(x, z);
                    seen[t.ordinal()] = true;
                    hash = fnv(hash, x + "," + z + "," + t.ordinal() + ";");
                    if (first.length() < 120) {
                        first.append(t.ordinal()).append(' ');
                    }
                }
            }
            int distinct = 0;
            for (boolean b : seen) {
                distinct += b ? 1 : 0;
            }
            assertThat(new LayerGolden(golden.seed(), hash, distinct, first.toString().trim())).isEqualTo(golden);
        }
    }
}
