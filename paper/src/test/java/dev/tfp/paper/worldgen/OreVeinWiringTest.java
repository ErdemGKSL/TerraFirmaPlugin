/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.OreVeins;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** M2-GEN-10: the generator's ore wiring over the shipped datagen output (the same files the jar carries). */
class OreVeinWiringTest {
    private static Path dataRoot() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured == null ? Path.of("..", "content", "generated", "content", "data") : Path.of(configured);
        assumeTrue(Files.isDirectory(root), "no generated content (run tools/datagen)");
        return root;
    }

    private static List<ClusterVeinConfig> veins() {
        final Path root = dataRoot();
        return OreVeinResources.load(path -> {
            final Path file = root.resolve(path);
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Every custom block id CraftEngine gets from datagen ({@code <ns>:<path>:} keys of the block YAML). */
    private static Set<String> craftEngineBlockIds() throws IOException {
        final Path ce = dataRoot().getParent().resolve("craftengine");
        assumeTrue(Files.isDirectory(ce), "no generated CraftEngine content");
        final Pattern key = Pattern.compile("^  (tfp:ore/[a-z_]+/[a-z]+):\\s*$", Pattern.MULTILINE);
        final Set<String> out = new HashSet<>();
        try (Stream<Path> files = Files.walk(ce)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                final Matcher m = key.matcher(Files.readString(f));
                while (m.find()) {
                    out.add(m.group(1));
                }
            }
        }
        return out;
    }

    @Test
    void theTagsClusterVeinsLoadInOrder() {
        final List<ClusterVeinConfig> veins = veins();
        assertThat(veins).hasSize(33);
        assertThat(veins.getFirst().name()).isEqualTo("surface_native_copper");
    }

    @Test
    void everyOreOfThePaletteIsACraftEngineBlock() throws IOException {
        final OreVeins ores = new OreVeins(0L, veins(), TfcTerrain.ROCK_IDS);
        final List<String> ids = TfcTerrain.paletteIds(ores);
        assertThat(ids.subList(0, TfcTerrain.ROCK_IDS.size())).isEqualTo(TfcTerrain.ROCK_IDS);
        assertThat(ids.subList(TfcTerrain.ROCK_IDS.size(), ids.size())).isEqualTo(ores.oreIds()).isNotEmpty();
        assertThat(craftEngineBlockIds()).containsAll(ores.oreIds());
    }

    @Test
    void anUnknownOreRefusesThePalette() {
        final OreVeins ores = new OreVeins(0L, veins(), TfcTerrain.ROCK_IDS);
        final String missing = ores.oreIds().get(3);
        final RockPalette<String> palette = RockPalette.resolve(1, true, TfcTerrain.paletteIds(ores),
            id -> id.equals(missing) ? Optional.empty() : Optional.of(id));
        assertThat(palette.resolved()).isFalse();
        assertThat(palette.refusal()).contains(missing);
    }

    @Test
    void blockIndicesAreTheRockOrAnOreOfThatRock() {
        final OreVeins ores = new OreVeins(42L, veins(), TfcTerrain.ROCK_IDS);
        final List<String> ids = TfcTerrain.paletteIds(ores);
        int oreBlocks = 0;
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                final OreVeins.Chunk chunk = ores.chunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = -64; y < 200; y += 2) {
                            final int x = (cx << 4) + lx;
                            final int z = (cz << 4) + lz;
                            final int rock = Math.floorMod(x + z + y / 8, TfcTerrain.ROCK_IDS.size());
                            final int index = TfcTerrain.blockIndex(ores, chunk, x, y, z, rock);
                            if (index == rock) {
                                continue;
                            }
                            final String rockName = TfcTerrain.ROCK_IDS.get(rock).substring("tfp:rock/raw/".length());
                            assertThat(ids.get(index)).startsWith("tfp:ore/").endsWith("/" + rockName);
                            oreBlocks++;
                        }
                    }
                }
            }
        }
        assertThat(oreBlocks).isPositive();
    }
}
