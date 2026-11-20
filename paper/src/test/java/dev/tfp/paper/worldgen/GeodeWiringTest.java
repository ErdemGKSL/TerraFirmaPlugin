/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.geode.GeodeConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-26: the geode's blocks are terrain palette blocks or CraftEngine blocks of the datagen output. */
class GeodeWiringTest {
    @Test
    void theGeodeShellIsInThePaletteAndItsOreIsACraftEngineBlock() throws IOException {
        final VeinStep step = new VeinStep(0L, OreVeinWiringTest.read(), TfcTerrain.ROCK_IDS);
        final GeodeConfig c = GeodeConfig.TFC;
        final List<String> ids = TfcTerrain.withGeodeBlocks(step.paletteIds(), c);
        assertThat(ids.subList(0, step.paletteIds().size())).isEqualTo(step.paletteIds());
        assertThat(ids).contains(c.outerLayer(), c.middleLayer(), c.innerLayer(), c.alternateInnerLayer()).doesNotHaveDuplicates();
        final Path ce = Path.of("..", "content", "generated", "content", "craftengine");
        assumeTrue(Files.isDirectory(ce), "no generated CraftEngine content");
        final StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(ce)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                all.append(Files.readString(f));
            }
        }
        // the palette refuses an unknown id, so every geode block must be a CraftEngine block
        for (String id : List.of(c.outerLayer(), c.middleLayer(), c.alternateInnerLayer())) {
            assertThat(all.indexOf("\n  " + id + ":")).as(id).isNotNegative();
        }
        assertThat(List.of(c.filling())).containsExactly("minecraft:air");
    }

    @Test
    void theVeinTagListsTheGeodeAfterTheVeins() {
        final String tag = OreVeinWiringTest.read().apply(dev.tfp.core.worldgen.ore.VeinData.VEIN_TAG).orElseThrow();
        final var values = com.google.gson.JsonParser.parseString(tag).getAsJsonObject().getAsJsonArray("values");
        // the terrain writes the geodes after the whole vein step, which keeps the tag's order for every vein pass that
        // replaces raw rock only (the geode writes no raw rock)
        assertThat(values.get(values.size() - 1).getAsString()).isEqualTo("tfp:geode");
    }
}
