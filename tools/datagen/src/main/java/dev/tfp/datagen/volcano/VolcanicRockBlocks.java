/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (ROCK_BLOCKS hardened, MAGMA_BLOCKS: igneous
 *       extrusive and intrusive rocks only), src/main/java/net/dries007/tfc/common/blocks/TFCMagmaBlock.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/blockstates/rock/{hardened,magma}/*.json @ c3b4d9536
 */
package dev.tfp.datagen.volcano;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * PAR-WORLD-15: the CraftEngine blocks the volcanic features place for TFC's hardened rock and magma blocks. The blocks
 * themselves are rock family members ({@code RockFamilies}, written by the asset pipeline into
 * {@code configuration/rock/<rock>.yml}); this step fails datagen when one the world generator relies on is missing: a
 * {@code tfp:rock/hardened/<rock>} for every one of the 21 rocks and a {@code tfp:rock/magma/<rock>} with its animated texture for
 * exactly the rocks TFC registers a magma block for (the 7 igneous rocks), which must also be the igneous rocks of
 * {@link PlaceholderRocks}. Runs after the asset pipeline; writes nothing.
 */
public final class VolcanicRockBlocks {
    static final String CONFIG = "craftengine/tfp/configuration/rock/";
    static final String TEXTURES = "craftengine/tfp/resourcepack/assets/tfp/textures/block/rock/magma/";
    static final String TFC_MAGMA = TfcCheckout.ASSETS + "blockstates/rock/magma/";

    private VolcanicRockBlocks() {}

    /** Whether the checkout has TFC's magma blockstates (the small fixture checkouts of the tests do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.isDirectory(TFC_MAGMA);
    }

    /** The igneous rocks of {@link PlaceholderRocks} (extrusive and intrusive), in index order: the rocks with a magma block. */
    public static List<String> igneousRocks() {
        return PlaceholderRocks.names().stream().filter(IGNEOUS::contains).toList();
    }

    /** TFC's igneous extrusive and intrusive rocks ({@code RockCategory.IGNEOUS_*}), checked against the checkout. */
    static final List<String> IGNEOUS = List.of("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite", "gabbro");

    /**
     * Checks the generated rock blocks against TFC and the world generator's rock table.
     *
     * @return the number of checked blocks (hardened and magma)
     */
    public static int run(TfcCheckout tfc, OutputTree out) {
        final List<String> tfcMagma = new ArrayList<>();
        for (String file : tfc.list(TFC_MAGMA)) {
            final String name = file.substring(file.lastIndexOf('/') + 1);
            if (name.endsWith(".json")) {
                tfcMagma.add(name.substring(0, name.length() - ".json".length()));
            }
        }
        final List<String> igneous = igneousRocks();
        if (igneous.size() != IGNEOUS.size() || !new TreeSet<>(tfcMagma).equals(new TreeSet<>(igneous))) {
            throw new DatagenException("TFC's magma rocks " + tfcMagma + " differ from the igneous rocks of PlaceholderRocks " + igneous);
        }
        int checked = 0;
        for (String rock : PlaceholderRocks.names()) {
            final byte[] bytes = out.get(CONFIG + rock + ".yml");
            if (bytes == null) {
                throw new DatagenException("no CraftEngine rock config for " + rock + " (" + CONFIG + rock + ".yml)");
            }
            final String yaml = new String(bytes, StandardCharsets.UTF_8);
            require(yaml, "tfp:rock/hardened/" + rock, true);
            checked++;
            final boolean magma = igneous.contains(rock);
            require(yaml, "tfp:rock/magma/" + rock, magma);
            if (magma) {
                if (!out.contains(TEXTURES + rock + ".png") || !out.contains(TEXTURES + rock + ".png.mcmeta")) {
                    throw new DatagenException("no animated magma texture for " + rock + " under " + TEXTURES);
                }
                checked++;
            }
        }
        return checked;
    }

    private static void require(String yaml, String id, boolean present) {
        final boolean has = yaml.contains("\n  " + id + ":\n");
        if (has != present) {
            throw new DatagenException((present ? "missing " : "unexpected ") + "CraftEngine block " + id);
        }
    }
}
