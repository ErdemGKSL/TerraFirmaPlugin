/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-23: the exported rock settings read through {@link RockSettings}, against TFC's files. */
class RockSettingsTest {
    static Map<String, RockSettings> exported() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isDirectory(root.resolve(RockSettings.DIR)), "no generated rock settings (run tools/datagen)");
        return RockSettings.load(RockLayerSampler.PRESET.rocks(), path -> {
            final Path f = root.resolve(path);
            try {
                return Files.isRegularFile(f) ? Optional.of(Files.readString(f)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void everyPresetRockHasSettingsWithTfcsFlagsAndBlocks() throws IOException {
        final Map<String, RockSettings> rocks = exported();
        assertThat(rocks).hasSize(21);
        final Path tfc = Path.of("/opt/tfc/src/generated/resources/data/tfc/tfc/worldgen/rock_settings");
        for (Map.Entry<String, RockSettings> e : rocks.entrySet()) {
            final String name = e.getKey().substring(4);
            final RockSettings ours = e.getValue();
            assertThat(ours.raw()).isEqualTo("tfp:rock/raw/" + name);
            assertThat(ours.isMafic()).as(name).isEqualTo(PlaceholderRocks.isMafic(PlaceholderRocks.indexOf(e.getKey())));
            if (Files.isDirectory(tfc)) {
                final RockSettings theirs = RockSettings.parse(Files.readString(tfc.resolve(name + ".json")));
                assertThat(ours.karst()).as(name).isEqualTo(theirs.karst());
                assertThat(ours.mafic()).as(name).isEqualTo(theirs.mafic());
                // the sand colour of each rock, by its TFC block
                assertThat(ours.sand().replace("tfp:", "tfc:")).as(name).isEqualTo(theirs.sand());
                assertThat(ours.sandstone().replace("tfp:", "tfc:")).as(name).isEqualTo(theirs.sandstone());
            }
        }
        assertThat(rocks.values().stream().filter(RockSettings::isKarst).map(RockSettings::raw))
            .containsExactlyInAnyOrder("tfp:rock/raw/chalk", "tfp:rock/raw/dolomite", "tfp:rock/raw/limestone", "tfp:rock/raw/marble");
    }
}
