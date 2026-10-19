/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * A TFC-shaped fruit tree part of a fixture checkout: the {@code FruitBlocks.Tree} source with the given species, the five
 * {@code FRUIT_TREE_*} registrations of {@code TFCBlocks}, and one blockstate per block the species get (five kinds each).
 */
final class FruitFixture {
    /** The species of the default fixture: two of TFC's eight, so the fixture is small and the names are real. */
    static final List<String> DEFAULT_SPECIES = List.of("CHERRY", "OLIVE");
    /** The kinds of a species, in TFC's registration order; the blockstate path of each is in {@link #writeAll}. */
    static final List<String> KINDS = List.of("leaves", "branch", "growing_branch", "sapling", "potted_sapling");

    /** The five registrations of TFC's {@code TFCBlocks} at the pinned commit, copied as TFC writes them. */
    static final String TFC_REGISTRATIONS = """
        public static final Map<FruitBlocks.Tree, Id<Block>> FRUIT_TREE_LEAVES = Helpers.mapOf(FruitBlocks.Tree.class, tree -> register("plant/" + tree.name() + "_leaves", tree::createLeaves));
        public static final Map<FruitBlocks.Tree, Id<Block>> FRUIT_TREE_BRANCHES = Helpers.mapOf(FruitBlocks.Tree.class, tree -> registerNoItem("plant/" + tree.name() + "_branch", tree::createBranch));
        public static final Map<FruitBlocks.Tree, Id<Block>> FRUIT_TREE_GROWING_BRANCHES = Helpers.mapOf(FruitBlocks.Tree.class, tree -> registerNoItem("plant/" + tree.name() + "_growing_branch", tree::createGrowingBranch));
        public static final Map<FruitBlocks.Tree, Id<Block>> FRUIT_TREE_SAPLINGS = Helpers.mapOf(FruitBlocks.Tree.class, tree -> register("plant/" + tree.name() + "_sapling", tree::createSapling, tree::createSaplingItem));
        public static final Map<FruitBlocks.Tree, Id<Block>> FRUIT_TREE_POTTED_SAPLINGS = Helpers.mapOf(FruitBlocks.Tree.class, tree -> registerNoItem("plant/potted/" + tree.name() + "_sapling", tree::createPottedSapling));
        """;

    private FruitFixture() {
    }

    /** The default part, added to {@link Fixture#create}: the default species and every blockstate they need. */
    static void addDefault(Path root) throws IOException {
        writeAll(root, DEFAULT_SPECIES, TFC_REGISTRATIONS);
    }

    /**
     * Writes the tree enum with {@code species} and the {@code registrations} of {@code TFCBlocks}, then an empty blockstate
     * (with a cube model and a texture) for each block the species get. An empty species list writes an enum with no constants.
     */
    static void writeAll(Path root, List<String> species, String registrations) throws IOException {
        StringBuilder sb = new StringBuilder("public final class FruitBlocks\n{\n    public enum Tree implements StringRepresentable\n    {\n");
        for (int i = 0; i < species.size(); i++) {
            String s = species.get(i);
            sb.append("        ").append(s).append("(Food.").append(s)
                .append(", 8, new Lifecycle[] {HEALTHY}, new Color(251, 135, 255).getRGB())")
                .append(i == species.size() - 1 ? ";" : ",").append('\n');
        }
        if (species.isEmpty()) {
            sb.append("        ;\n");
        }
        sb.append("\n        private final Food product;\n    }\n}\n");
        Fixture.write(root, FruitFamilies.TREE_SOURCE, sb.toString());
        Fixture.write(root, FruitFamilies.BLOCKS_SOURCE, "public final class TFCBlocks\n{\n" + registrations + "}\n");
        for (String species0 : species) {
            String s = species0.toLowerCase(Locale.ROOT);
            for (String kind : KINDS) {
                RockMudFixture.writeBlock(root, path(s, kind));
            }
        }
    }

    /** The blockstate path below {@code blockstates/} of a species' kind: the potted kind has its own folder. */
    static String path(String species, String kind) {
        return kind.equals("potted_sapling") ? "plant/potted/" + species + "_sapling" : "plant/" + species + "_" + kind;
    }
}
