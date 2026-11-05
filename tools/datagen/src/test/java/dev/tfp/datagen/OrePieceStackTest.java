/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-21: the drops.yml ore pieces stack like TFC's item_size (tfc:ore_pieces, weight medium = 16), on the pinned checkout. */
class OrePieceStackTest {
    private static TfcCheckout tfc;
    private static Map<String, Object> items;
    private static Report report;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        OutputTree out = new OutputTree();
        report = new Report();
        AssetPipeline.run(tfc, out, report);
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        String yaml = new String(out.get(AssetPipeline.CONFIG + "drops.yml"), StandardCharsets.UTF_8);
        items = (Map<String, Object>) ((Map<String, Object>) new Yaml(opts).load(yaml)).get("items");
    }

    @SuppressWarnings("unchecked")
    private static Object stack(String id) {
        Map<String, Object> entry = (Map<String, Object>) items.get(id);
        Map<String, Object> data = (Map<String, Object>) entry.get("data");
        return data == null ? null : data.get("max_stack_size");
    }

    @Test
    void everyOrePieceOfDropsStacksTo16() {
        TreeSet<String> ores = new TreeSet<>();
        items.keySet().stream().filter(id -> id.startsWith("tfp:ore/")).forEach(ores::add);
        assertThat(ores).hasSize(53).contains("tfp:ore/rich_hematite", "tfp:ore/poor_native_copper", "tfp:ore/amethyst");
        assertThat(ores).allSatisfy(id -> assertThat(stack(id)).as(id).isEqualTo(16));
        // The three item-only ores belong to the material generator (PAR-PROC-19), never to drops.yml.
        assertThat(ores).doesNotContain("tfp:ore/halite", "tfp:ore/lignite", "tfp:ore/bituminous_coal");
        assertThat(report.count("drop_items_sized", 0)).isEqualTo(53);
    }

    @Test
    void everyOrePieceIsInTfcsOrePiecesTag() {
        AssetPipeline.ItemSizes sizes = new AssetPipeline.ItemSizes(tfc);
        items.keySet().stream().filter(id -> id.startsWith("tfp:ore/"))
            .forEach(id -> assertThat(sizes.stackSize("tfc:" + id.substring(4))).as(id).contains(16));
    }

    @Test
    void looseRocksKeepTheDefault64() {
        assertThat(items.keySet()).anyMatch(id -> id.startsWith("tfp:rock/loose/"));
        items.keySet().stream().filter(id -> id.startsWith("tfp:rock/loose/"))
            .forEach(id -> assertThat(stack(id)).as(id).isNull()); // no TFC item_size: CE/vanilla 64, TFC's default too
    }

    /** Golden rows read by hand from TFC's item_size JSON (small_ores light, ingots via neoforge:difference medium). */
    @Test
    void goldenItemSizes() {
        AssetPipeline.ItemSizes sizes = new AssetPipeline.ItemSizes(tfc);
        assertThat(sizes.stackSize("tfc:ore/small_hematite")).contains(32);
        assertThat(sizes.stackSize("tfc:metal/ingot/copper")).contains(16);
        assertThat(sizes.stackSize("tfc:rock/loose/granite")).isEmpty();
        assertThat(sizes.order()).hasSize(69).contains("tfc:ores", "tfc:foods", "tfc:soups");
        // TFC's load order (HashMap over ResourceLocation hashes): foods before soups, so soups match foods (light) first.
        assertThat(sizes.order().indexOf("tfc:foods")).isLessThan(sizes.order().indexOf("tfc:soups"));
    }
}
