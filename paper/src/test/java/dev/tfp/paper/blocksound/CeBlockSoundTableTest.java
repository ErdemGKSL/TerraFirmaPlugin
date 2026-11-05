/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.craftengine.CeBlockSoundTable;
import dev.tfp.paper.integration.craftengine.CeBlockSoundTable.Sound;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-33: the hit/break table read from the CraftEngine YAML shapes datagen writes. */
class CeBlockSoundTableTest {
    /** The repository root (the paper module's tests run in paper/). */
    static Path repo() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("spikes")) ? here : here.getParent();
    }

    @Test
    void theS2CopperRendererCubeHasTfcMetalValues() throws Exception {
        String s2 = Files.readString(repo().resolve("spikes/s2-destroy-stages/pack/configuration/s2.yml"), StandardCharsets.UTF_8);
        CeBlockSoundTable table = CeBlockSoundTable.parse(s2);
        assertThat(table.size()).isEqualTo(1);
        CeBlockSoundTable.Entry copper = table.entry("tfps2:metal/block/copper").orElseThrow();
        assertThat(copper.hit()).isEqualTo(new Sound("minecraft:block.metal.hit", 0.25f, 0.75f));
        assertThat(copper.breakSound()).isEqualTo(new Sound("minecraft:block.metal.break", 1.0f, 1.2f));
    }

    @Test
    void plainIdsInSettingsOrDeviceArgumentsPlayAtTheUnitSoundType() {
        CeBlockSoundTable table = CeBlockSoundTable.parse("""
            blocks:
              tfp:bellows:
                settings:
                  sounds:
                    break: minecraft:block.wood.break
                    hit: minecraft:block.wood.hit
              tfp:firepit:
                template: tfp:block/device
                arguments:
                  device_sound_break: minecraft:block.nether_wart.break
                  device_sound_hit: minecraft:block.stone.hit
              tfp:metal_device:
                template: tfp:block/device
                arguments:
                  device_sound_hit: {id: minecraft:block.metal.hit, volume: 0.25, pitch: 0.75}
                  device_sound_break: {id: minecraft:block.metal.break, volume: 1.0, pitch: 1.2}
              tfp:silent:
                settings:
                  hardness: 1.0
              other:not_ours:
                settings:
                  sounds:
                    hit: minecraft:block.stone.hit
            """);
        assertThat(table.entry("tfp:bellows").orElseThrow().hit()).isEqualTo(new Sound("minecraft:block.wood.hit", 0.25f, 0.5f));
        assertThat(table.entry("tfp:bellows").orElseThrow().breakSound())
            .isEqualTo(new Sound("minecraft:block.wood.break", 1.0f, 0.8f));
        assertThat(table.entry("tfp:firepit").orElseThrow().breakSound())
            .isEqualTo(new Sound("minecraft:block.nether_wart.break", 1.0f, 0.8f));
        assertThat(table.entry("tfp:firepit").orElseThrow().hit()).isEqualTo(new Sound("minecraft:block.stone.hit", 0.25f, 0.5f));
        assertThat(table.entry("tfp:metal_device").orElseThrow().hit())
            .isEqualTo(new Sound("minecraft:block.metal.hit", 0.25f, 0.75f));
        assertThat(table.contains("tfp:silent")).isFalse();
        assertThat(table.contains("other:not_ours")).as("other packs' blocks are never rewritten").isFalse();
    }

    @Test
    void replacedPrefixIsStripped() {
        assertThat(CeBlockSoundTable.normalise("minecraft:replaced.block.metal.break")).isEqualTo("minecraft:block.metal.break");
        assertThat(CeBlockSoundTable.normalise("minecraft:block.metal.hit")).isEqualTo("minecraft:block.metal.hit");
        assertThat(CeBlockSoundTable.normalise("block.stone.hit")).isEqualTo("minecraft:block.stone.hit");
    }

    @Test
    void theGeneratedDevicesAreInTheTable(@TempDir Path dir) throws Exception {
        // The real datagen output (content/generated, linked by tools/worktree/share-build.sh) laid out as CraftEngine's
        // resources folder: resources/tfp/... .
        Path generated = repo().resolve("content/generated/content/craftengine");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(generated.resolve("tfp")), "datagen output present");
        List<String> problems = new ArrayList<>();
        CeBlockSoundTable table = CeBlockSoundTable.load(generated, problems);
        assertThat(problems).isEmpty();
        assertThat(table.entry("tfp:bellows").orElseThrow().hit()).isEqualTo(new Sound("minecraft:block.wood.hit", 0.25f, 0.5f));
        assertThat(table.entry("tfp:charcoal_forge").orElseThrow().breakSound())
            .isEqualTo(new Sound("tfp:block.charcoal.break", 1.0f, 0.8f));
        assertThat(table.entry("tfp:firepit").orElseThrow().breakSound().id()).isEqualTo("minecraft:block.nether_wart.break");
        assertThat(CeBlockSoundTable.load(dir.resolve("missing"), problems).size()).isZero();
    }
}
