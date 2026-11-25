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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-DECO-08 against the real TFC checkout: 7 colours x 3 sandstones, hardened clay, halite, lignite, bituminous coal. */
class NaturalBlocksTest {
    @Test
    void sandstonesAndNaturalBlocks() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        Report report = new Report();
        AssetPipeline.run(tfc, out, report);
        long sandstone = report.emittedIds().stream().filter(id -> id.matches("(raw|smooth|cut)_sandstone/[a-z]+")).count();
        assertThat(sandstone).isEqualTo(7 * 3);
        assertThat(report.emittedIds()).contains("hardened_clay", "halite", "lignite", "bituminous_coal");
        String sand = new String(out.get(AssetPipeline.CONFIG + "natural/sandstone.yml"), StandardCharsets.UTF_8);
        assertThat(sand).contains("tfp:raw_sandstone/black:", "tfp:smooth_sandstone/red:", "tfp:cut_sandstone/yellow:",
            "natural_hardness: 0.8", "natural_hardness: 1.2");
        String clay = new String(out.get(AssetPipeline.CONFIG + "natural/hardened_clay.yml"), StandardCharsets.UTF_8);
        assertThat(clay).contains("tfp:hardened_clay:", "natural_hardness: 7.0", "sound_block_type: packed_mud");
        String halite = new String(out.get(AssetPipeline.CONFIG + "natural/halite.yml"), StandardCharsets.UTF_8);
        assertThat(halite).contains("tfp:halite:", "natural_hardness: 6.0");
        assertThat(out.contains(AssetPipeline.CONFIG + "natural/lignite.yml")).isTrue();
        assertThat(out.contains(AssetPipeline.CONFIG + "natural/bituminous_coal.yml")).isTrue();
    }

    /** PAR-DECO-07: 51 alabaster cubes, 8 mud bricks, fire bricks and aggregate. */
    @Test
    void buildingCubes() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        Report report = new Report();
        AssetPipeline.run(tfc, out, report);
        assertThat(report.emittedIds().stream().filter(id -> id.startsWith("alabaster/")).count()).isEqualTo(3 + 48);
        assertThat(report.emittedIds().stream().filter(id -> id.matches("mud_bricks/[a-z]+")).count()).isEqualTo(8);
        assertThat(report.emittedIds()).contains("fire_bricks", "aggregate", "alabaster/raw/light_blue", "mud_bricks/alfisol");
        String alabaster = new String(out.get(AssetPipeline.CONFIG + "natural/alabaster.yml"), StandardCharsets.UTF_8);
        assertThat(alabaster).contains("tfp:alabaster/raw/red:", "tfp:alabaster/polished:", "natural_hardness: 1.0",
            "natural_resistance: 6.0");
        String agg = new String(out.get(AssetPipeline.CONFIG + "natural/aggregate.yml"), StandardCharsets.UTF_8);
        assertThat(agg).contains("tfp:settings/natural_shovel", "sound_block_type: gravel");
        String mud = new String(out.get(AssetPipeline.CONFIG + "natural/mud_bricks.yml"), StandardCharsets.UTF_8);
        assertThat(mud).contains("tfp:mud_bricks/podzol:", "sound_block_type: mud_bricks", "natural_hardness: 2.6");
        assertThat(out.contains(AssetPipeline.CONFIG + "natural/fire_bricks.yml")).isTrue();
    }
}
