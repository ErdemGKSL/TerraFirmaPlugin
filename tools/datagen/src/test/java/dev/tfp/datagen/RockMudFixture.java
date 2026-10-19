/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A TFC-shaped rock and mud part of a fixture checkout: the {@code Rock} and {@code SoilBlockType} sources and one
 * blockstate per block the generators expect, each with a cube model and a texture. Blockstates that already exist are
 * kept, so the shared {@link Fixture} keeps its own rock blockstates.
 */
final class RockMudFixture {
    /** A {@code Rock} constant and its {@code RockDisplayCategory} constant. */
    record RockSpec(String name, String category) {
    }

    /** The {@code Rock.BlockType} constants of TFC at the pinned commit, in order, with their {@code variants} flag. */
    static final List<Map.Entry<String, Boolean>> TFC_BLOCK_TYPES = List.of(
        Map.entry("RAW", true), Map.entry("HARDENED", false), Map.entry("SMOOTH", true), Map.entry("COBBLE", true),
        Map.entry("BRICKS", true), Map.entry("GRAVEL", false), Map.entry("SPIKE", false),
        Map.entry("CRACKED_BRICKS", true), Map.entry("MOSSY_BRICKS", true), Map.entry("MOSSY_COBBLE", true),
        Map.entry("CHISELED", false), Map.entry("LOOSE", false), Map.entry("MOSSY_LOOSE", false),
        Map.entry("PRESSURE_PLATE", false), Map.entry("BUTTON", false), Map.entry("AQUEDUCT", false),
        Map.entry("ROPE_ANCHOR", false));

    /**
     * Rocks of the default fixture. Their names are not in {@code TfcTables.ROCKS}, so the asset pipeline (which reads
     * the base kinds of those rocks and needs loot tables for them) leaves them alone; the rock families still read them.
     */
    static final List<RockSpec> DEFAULT_ROCKS = List.of(
        new RockSpec("TESTITE", "FELSIC_IGNEOUS_INTRUSIVE"), new RockSpec("TESTSTONE", "SEDIMENTARY"));
    static final List<String> DEFAULT_SOILS = List.of("ALFISOL", "PODZOL");

    private RockMudFixture() {
    }

    /** The default part, added to {@link Fixture#create}: rock and mud sources plus every blockstate not yet present. */
    static void addDefault(Path root) throws IOException {
        writeAll(root, DEFAULT_ROCKS, TFC_BLOCK_TYPES, DEFAULT_SOILS, true);
    }

    /**
     * Writes the rock source with {@code rocks} and {@code blockTypes}, the mud source with {@code soils} and
     * {@code MUD_BRICKS}, and an empty blockstate for every block of the rocks (igneous only for anvil and magma)
     * and the soils, unless one exists.
     */
    static void writeAll(Path root, List<RockSpec> rocks, List<Map.Entry<String, Boolean>> blockTypes,
                         List<String> soils, boolean withMudBricks) throws IOException {
        writeRockSource(root, rocks, blockTypes);
        writeMudSource(root, soils, withMudBricks);
        for (RockSpec r : rocks) {
            boolean igneous = r.category().contains("IGNEOUS");
            String rock = r.name().toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, Boolean> t : blockTypes) {
                writeBlockstates(root, t.getKey(), rock, t.getValue());
            }
            if (igneous) {
                writeBlockstates(root, RockFamilies.ANVIL, rock, false);
                writeBlockstates(root, RockFamilies.MAGMA, rock, false);
            }
        }
        for (String s : soils) {
            for (String shape : MudFamilies.SHAPES) {
                writeBlock(root, MudFamilies.path(s.toLowerCase(java.util.Locale.ROOT), shape));
            }
        }
    }

    static void writeRockSource(Path root, List<RockSpec> rocks, List<Map.Entry<String, Boolean>> blockTypes) throws IOException {
        StringBuilder sb = new StringBuilder("public enum Rock implements RegistryRock\n{\n");
        for (int i = 0; i < rocks.size(); i++) {
            RockSpec r = rocks.get(i);
            sb.append("    ").append(r.name()).append("(RockDisplayCategory.").append(r.category())
                .append(", MapColor.STONE)").append(i == rocks.size() - 1 ? ";" : ",").append('\n');
        }
        sb.append("\n    public enum BlockType implements StringRepresentable\n    {\n");
        for (int i = 0; i < blockTypes.size(); i++) {
            Map.Entry<String, Boolean> t = blockTypes.get(i);
            sb.append("        ").append(t.getKey()).append("((rock, self) -> new Block(properties(rock)), ")
                .append(t.getValue()).append(i == blockTypes.size() - 1 ? ");" : "),").append('\n');
        }
        sb.append("    }\n}\n");
        Fixture.write(root, RockFamilies.SOURCE, sb.toString());
    }

    static void writeMudSource(Path root, List<String> soils, boolean withMudBricks) throws IOException {
        StringBuilder sb = new StringBuilder("public enum SoilBlockType\n{\n");
        if (withMudBricks) {
            sb.append("    MUD_BRICKS((self, variant) -> new Block(Block.Properties.of()),\n");
        }
        sb.append("    DIRT((self, variant) -> new DirtBlock(Block.Properties.of()));\n\n");
        sb.append("    public enum Variant implements RegistrySoilVariant\n    {\n");
        for (int i = 0; i < soils.size(); i++) {
            sb.append("        ").append(soils.get(i)).append(i == soils.size() - 1 ? ";" : ",").append('\n');
        }
        sb.append("\n        private static final Variant[] VALUES = values();\n    }\n}\n");
        Fixture.write(root, MudFamilies.SOURCE, sb.toString());
    }

    private static void writeBlockstates(Path root, String kindConstant, String rock, boolean variants) throws IOException {
        String kind = kindConstant.toLowerCase(java.util.Locale.ROOT);
        writeBlock(root, RockFamilies.path(kind, rock, "full"));
        if (variants) {
            for (String shape : RockFamilies.VARIANT_SHAPES) {
                writeBlock(root, RockFamilies.path(kind, rock, shape));
            }
        }
    }

    /**
     * One block of the fixture: a variants blockstate, a cube model and a texture for {@code path} (below the blockstate
     * folder), so the asset pipeline can read it. Skipped when the blockstate exists.
     */
    private static void writeBlock(Path root, String path) throws IOException {
        String rel = TfcCheckout.ASSETS + "blockstates/" + path + ".json";
        if (Files.exists(root.resolve(rel))) {
            return;
        }
        Fixture.write(root, rel, "{\"variants\": {\"\": {\"model\": \"tfc:block/" + path + "\"}}}");
        Fixture.write(root, TfcCheckout.ASSETS + "models/block/" + path + ".json",
            "{\"parent\": \"block/cube_all\", \"textures\": {\"all\": \"tfc:block/" + path + "\"}}");
        Fixture.png(root, TfcCheckout.ASSETS + "textures/block/" + path + ".png", Fixture.ROCK, Fixture.ROCK);
    }
}
