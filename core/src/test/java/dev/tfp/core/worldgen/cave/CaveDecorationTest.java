/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.cave;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-20: the chunk placement of the cave decorations and the list of the cave ids still without a rule. */
class CaveDecorationTest {
    /** TFC's in_biome/underground_decoration, underground_lakes and erosion tags (c3b4d9536), in their order. */
    private static final List<String> TAGGED = List.of("tfc:cave_column", "tfc:cave_spike", "tfc:large_cave_spike",
        "tfc:water_spring", "tfc:lava_spring", "tfc:water_surface_spring", "tfc:extra_water_surface_spring",
        "tfc:glacial_spring", "tfc:ice_sheet_spring", "tfc:calcite", "tfc:mega_calcite", "tfc:icicle",
        "tfc:underground_loose_rocks", "tfc:underground_flood_fill_lake", "tfc:erosion");
    /** Ruled by other items: the springs (PAR-WORLD-14) and the underground loose rocks (PAR-WORLD-32). */
    private static final Set<String> ELSEWHERE = Set.of("tfc:water_spring", "tfc:lava_spring", "tfc:water_surface_spring",
        "tfc:extra_water_surface_spring", "tfc:glacial_spring", "tfc:ice_sheet_spring", "tfc:underground_loose_rocks");
    /**
     * Listed in the {@code in_biome/large_features} tags of ten biomes. ({@code tfc:hanging_roots_patch} is placed by no TFC
     * biome or tag at c3b4d9536, so it never generates and has no rule.)
     */
    private static final List<String> BIOME_LISTED = List.of("tfc:cave_vegetation");

    private static List<String> openGaps() throws IOException {
        try (InputStream in = CaveDecorationTest.class.getResourceAsStream("/dev/tfp/core/worldgen/cave/open_gaps.txt")) {
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        }
    }

    @Test
    void everyCaveIdIsRuledOrListedAsAnOpenGap() throws IOException {
        final List<String> gaps = openGaps();
        final Set<String> expected = new LinkedHashSet<>();
        TAGGED.stream().filter(id -> !ELSEWHERE.contains(id)).forEach(expected::add);
        expected.addAll(BIOME_LISTED);
        final Set<String> covered = new LinkedHashSet<>(CaveDecoration.FEATURES);
        covered.addAll(gaps);
        assertThat(covered).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(gaps).doesNotContainAnyElementsOf(CaveDecoration.FEATURES);
    }

    @Test
    void featuresFollowTheTagOrder() {
        assertThat(CaveDecoration.FEATURES).isEqualTo(TAGGED.stream().filter(CaveDecoration.FEATURES::contains).toList());
    }

    @Test
    void aChunkIsDecoratedTheSameWayEveryTimeAndOnlyWithSpikesAndHardenedRock() {
        final SyntheticCaves a = new SyntheticCaves();
        final SyntheticCaves b = new SyntheticCaves();
        final CaveDecoration decoration = new CaveDecoration(1234L);
        int spikes = 0;
        for (int cx = -2; cx < 2; cx++) {
            for (int cz = -2; cz < 2; cz++) {
                a.reset();
                b.reset();
                decoration.place(cx, cz, a);
                new CaveDecoration(1234L).place(cx, cz, b);
                assertThat(a.log.toString()).isEqualTo(b.log.toString());
                for (CaveCell c : a.writes.values()) {
                    assertThat(c.kind()).isIn(CaveCell.Kind.SPIKE, CaveCell.Kind.HARDENED);
                    if (c.kind() == CaveCell.Kind.SPIKE) {
                        spikes++;
                    }
                }
            }
        }
        // cave_spike: one carved position in five (40 % of the synthetic world is cave air), so every chunk has spikes
        assertThat(spikes).isGreaterThan(16 * 100);
    }

    @Test
    void biasedToBottomStaysInRangeAndFavoursTheBottom() {
        final XoroshiroRandomSource random = new XoroshiroRandomSource(7L);
        int low = 0;
        for (int i = 0; i < 10_000; i++) {
            final int y = CaveDecoration.biasedToBottom(random, -56, 60);
            assertThat(y).isBetween(-56, 59);
            if (y < 2) {
                low++;
            }
        }
        assertThat(low).isGreaterThan(6_000);
    }
}
