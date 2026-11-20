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
import java.util.function.Function;
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

    static Function<String, Optional<String>> read() {
        final Path root = dataRoot();
        return path -> {
            final Path file = root.resolve(path);
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static List<ClusterVeinConfig> veins() {
        return OreVeinResources.load(read());
    }

    /** Every custom block id CraftEngine gets from datagen ({@code <ns>:<path>:} keys of the block YAML). */
    private static Set<String> craftEngineBlockIds() throws IOException {
        final Path ce = dataRoot().getParent().resolve("craftengine");
        assumeTrue(Files.isDirectory(ce), "no generated CraftEngine content");
        final Pattern key = Pattern.compile("^  (tfp:[a-z_/]+):\\s*$", Pattern.MULTILINE);
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
    void everyBlockOfThePaletteIsACraftEngineBlock() throws IOException {
        final VeinStep step = new VeinStep(0L, read(), TfcTerrain.ROCK_IDS);
        final List<String> ids = step.paletteIds();
        assertThat(ids.subList(0, TfcTerrain.ROCK_IDS.size())).isEqualTo(TfcTerrain.ROCK_IDS);
        assertThat(ids.subList(TfcTerrain.ROCK_IDS.size(), ids.size())).containsAll(step.main().oreIds())
            .anyMatch(id -> id.startsWith("tfp:rock/hardened/")).anyMatch(id -> id.startsWith("tfp:rock/gravel/"));
        assertThat(craftEngineBlockIds()).containsAll(ids.subList(TfcTerrain.ROCK_IDS.size(), ids.size()));
    }

    @Test
    void theBlocksOutsideThePaletteAreTheCoalAndSaltBlocksAndTheKaolinClays() {
        final VeinStep step = new VeinStep(0L, read(), TfcTerrain.ROCK_IDS);
        // Not CraftEngine blocks yet (follow-up item): written as vanilla placeholders until CraftEngine has them
        assertThat(step.customIds()).containsExactly("tfp:bituminous_coal", "tfp:halite", "tfp:lignite", "tfp:kaolin_clay_grass",
            "tfp:red_kaolin_clay", "tfp:pink_kaolin_clay", "tfp:white_kaolin_clay");
        for (String id : step.customIds()) {
            assertThat(VeinStep.placeholder(id)).isNotNull();
        }
    }

    @Test
    void anUnknownOreRefusesThePalette() {
        final VeinStep step = new VeinStep(0L, read(), TfcTerrain.ROCK_IDS);
        final String missing = step.main().oreIds().get(3);
        final RockPalette<String> palette = RockPalette.resolve(1, true, step.paletteIds(),
            id -> id.equals(missing) ? Optional.empty() : Optional.of(id));
        assertThat(palette.resolved()).isFalse();
        assertThat(palette.refusal()).contains(missing);
    }

    @Test
    void rockPositionsBecomeTheRockAnOreOfThatRockOrAVeinRock() {
        final VeinStep step = new VeinStep(42L, read(), TfcTerrain.ROCK_IDS);
        final List<String> ids = step.paletteIds();
        final OreVeins.WorldView flat = new OreVeins.WorldView() {
            @Override
            public int surfaceHeight(int x, int z) {
                return 64;
            }

            @Override
            public boolean isLava(int x, int y, int z) {
                return false;
            }
        };
        int oreBlocks = 0;
        int veinRocks = 0;
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                final VeinStep.Chunk chunk = step.chunk(cx, cz, 0, false, flat);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = -64; y < 200; y += 2) {
                            final int x = (cx << 4) + lx;
                            final int z = (cz << 4) + lz;
                            final int rock = Math.floorMod(x + z + y / 8, TfcTerrain.ROCK_IDS.size());
                            final int index = chunk.isEmpty() ? rock : chunk.rock(x, y, z, rock);
                            if (index == rock) {
                                continue;
                            }
                            if (VeinStep.isCustom(index)) {
                                assertThat(step.customIds().get(VeinStep.customOf(index))).isIn("tfp:lignite", "tfp:bituminous_coal",
                                    "tfp:halite");
                                continue;
                            }
                            final String id = ids.get(index);
                            if (id.startsWith("tfp:ore/")) {
                                oreBlocks++;
                            } else {
                                assertThat(id).startsWith("tfp:rock/");
                                veinRocks++;
                            }
                        }
                    }
                }
            }
        }
        assertThat(oreBlocks).isPositive();
        assertThat(veinRocks).isPositive();
    }
}
