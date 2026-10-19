/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A TFC-shaped metal part of a fixture checkout: the {@code Metal} source with one metal per part-type rule (a plain
 * metal, a weathering plain metal, a tiered metal that does not weather, a tiered metal that weathers, and an ingot-only
 * metal), the full {@code Metal.BlockType} list, and one blockstate per block the generator expects.
 */
final class MetalFixture {
    /** A {@code Metal} constant, written as TFC writes it: {@code args} is the text after the name's parenthesis. */
    record MetalSpec(String name, String args) {
    }

    /** The {@code Metal} constants of the default fixture, one per part-type rule. */
    static final List<MetalSpec> DEFAULT_METALS = List.of(
        new MetalSpec("TIN", "0xFF90A4BB, MapColor.COLOR_LIGHT_GRAY, Rarity.COMMON, -1, PartType.DEFAULT"),
        new MetalSpec("SILVER", "0xFF949495, MapColor.COLOR_LIGHT_GRAY, Rarity.COMMON, 0.95f, PartType.DEFAULT_WEATHERING"),
        new MetalSpec("BLACK_STEEL", "0xFF111111, MapColor.COLOR_BLACK, Rarity.RARE, -1, TFCTiers.BLACK_STEEL, TFCArmorMaterials.BLACK_STEEL"),
        new MetalSpec("BRONZE", "0xFF96892E, MapColor.TERRACOTTA_ORANGE, Rarity.COMMON, 0.7f, TFCTiers.BRONZE, TFCArmorMaterials.BRONZE"),
        new MetalSpec("PIG_IRON", "0xFF6A595C, MapColor.COLOR_GRAY, Rarity.COMMON, -1, PartType.INGOT"));

    /** The {@code Metal.BlockType} constants of TFC at the pinned commit, in order, with their part types. */
    static final List<String[]> TFC_BLOCK_TYPES = List.of(
        new String[] {"BLOCK", "DEFAULT"}, new String[] {"EXPOSED_BLOCK", "WEATHERED"},
        new String[] {"WEATHERED_BLOCK", "WEATHERED"}, new String[] {"OXIDIZED_BLOCK", "WEATHERED"},
        new String[] {"BLOCK_SLAB", "DEFAULT"}, new String[] {"EXPOSED_BLOCK_SLAB", "WEATHERED"},
        new String[] {"WEATHERED_BLOCK_SLAB", "WEATHERED"}, new String[] {"OXIDIZED_BLOCK_SLAB", "WEATHERED"},
        new String[] {"BLOCK_STAIRS", "DEFAULT"}, new String[] {"EXPOSED_BLOCK_STAIRS", "WEATHERED"},
        new String[] {"WEATHERED_BLOCK_STAIRS", "WEATHERED"}, new String[] {"OXIDIZED_BLOCK_STAIRS", "WEATHERED"},
        new String[] {"GRATE", "ALL"}, new String[] {"EXPOSED_GRATE", "ALL_WEATHERED"},
        new String[] {"WEATHERED_GRATE", "ALL_WEATHERED"}, new String[] {"OXIDIZED_GRATE", "ALL_WEATHERED"},
        new String[] {"ANVIL", "ALL"}, new String[] {"BARS", "ALL"}, new String[] {"CHAIN", "ALL"},
        new String[] {"LAMP", "ALL"}, new String[] {"TRAPDOOR", "ALL"});

    private MetalFixture() {
    }

    /** The default part, added to {@link Fixture#create}: the metal source and every blockstate it needs. */
    static void addDefault(Path root) throws IOException {
        writeAll(root, DEFAULT_METALS, TFC_BLOCK_TYPES);
    }

    /**
     * Writes the metal source with {@code metals} and {@code blockTypes}, then an empty blockstate (with a cube model and
     * a texture) for each block that the metals of {@code blockTypes} get under TFC's rules, anvils included. Blockstates
     * that exist are kept.
     */
    static void writeAll(Path root, List<MetalSpec> metals, List<String[]> blockTypes) throws IOException {
        writeSource(root, metals, blockTypes);
        for (MetalSpec m : metals) {
            String partType = partTypeOf(m.args());
            if (partType.equals("INGOT")) {
                continue;
            }
            String metal = m.name().toLowerCase(java.util.Locale.ROOT);
            for (String[] t : blockTypes) {
                if (!MetalFamilies.allows(t[1], partType)) {
                    continue;
                }
                String name = t[0].toLowerCase(java.util.Locale.ROOT);
                String folder = name;
                String shape = "";
                if (name.contains("_slab")) {
                    folder = name.split("_slab")[0];
                    shape = "_slab";
                } else if (name.contains("_stairs")) {
                    folder = name.split("_stairs")[0];
                    shape = "_stairs";
                }
                RockMudFixture.writeBlock(root, "metal/" + folder + "/" + metal + shape);
            }
        }
    }

    /** Writes the {@code Metal} source (constants, then {@code BlockType}, then {@code PartType}) with the TFC layout. */
    static void writeSource(Path root, List<MetalSpec> metals, List<String[]> blockTypes) throws IOException {
        StringBuilder sb = new StringBuilder("public enum Metal implements StringRepresentable, RegistryMetal\n{\n");
        for (int i = 0; i < metals.size(); i++) {
            MetalSpec m = metals.get(i);
            sb.append("    ").append(m.name()).append('(').append(m.args()).append(')')
                .append(i == metals.size() - 1 ? ";" : ",").append('\n');
        }
        sb.append("\n    public enum BlockType\n    {\n");
        for (int i = 0; i < blockTypes.size(); i++) {
            String[] t = blockTypes.get(i);
            sb.append("        ").append(t[0]).append("(PartType.").append(t[1]).append(", block(Age.NONE))")
                .append(i == blockTypes.size() - 1 ? ";" : ",").append('\n');
        }
        sb.append("    }\n\n    enum PartType\n    {\n        INGOT, DEFAULT, DEFAULT_WEATHERING, ALL, ALL_WEATHERING, WEATHERED, ALL_WEATHERED;\n    }\n}\n");
        Fixture.write(root, MetalFamilies.SOURCE, sb.toString());
    }

    /** The part type name of a constant's arguments: after {@code PartType.}, or ALL / ALL_WEATHERING for a tiered metal. */
    static String partTypeOf(String args) {
        for (String a : args.split(",\\s*")) {
            if (a.startsWith("PartType.")) {
                return a.substring("PartType.".length());
            }
        }
        // A tiered constant is ALL when its weathering argument (the fourth) is -1, as in TFC's Metal constructor.
        return args.split(",\\s*")[3].equals("-1") ? "ALL" : "ALL_WEATHERING";
    }
}
