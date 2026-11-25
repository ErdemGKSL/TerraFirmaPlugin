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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-20: golden values of TFC's cave features at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from a scratch oracle (kept outside the repo): the method bodies of TFC's
 * {@code CaveSpikesFeature}, {@code LargeCaveSpikesFeature}, {@code CaveColumnFeature} and {@code ThinSpikeFeature} (with
 * {@code ThinSpikeConfig.getHeight}, {@code ThinSpikeBlock.canSurvive} and the {@code FluidHelpers.fillWithFluid} case of the
 * spike's {@code WATER} fluid property) copied over a string-keyed block-state stub of the {@link SyntheticCaves} world, run
 * with Minecraft 1.21.11's own {@code XoroshiroRandomSource}, {@code Mth}, {@code BlockPos} and {@code Direction} from the
 * Paper 1.21.11 server jar the project's run-paper tooling fetches. Each feature runs at 64 origins per level seed, with
 * {@code new XoroshiroRandomSource(seed ^ x * 341873128712L ^ z * 132897987541L ^ (y + 64 f) * 0x9E3779B97F4A7C15L)} for
 * feature number {@code f}, and the lines {@code "x y z|<writes in order>|<next long>"} are hashed (FNV-1a 64), so the writes,
 * their order and the number of draws all match.
 *
 * <p>The thin spikes write nothing: TFC's search moves up to the block below the first empty block (or seven blocks up), which
 * is never empty itself, so {@code placeSpikeBlock} always fails. TFC generates no calcite and no icicles; the port keeps it.
 */
class CaveFeatureGoldenTest {
    private static final String[] FEATURES = {"cave_spike", "large_cave_spike", "cave_column", "calcite", "mega_calcite", "icicle"};

    private record Golden(long seed, String feature, String hash, int writes) {
    }

    private static List<Golden> goldens() throws IOException {
        final List<Golden> out = new ArrayList<>();
        try (InputStream in = CaveFeatureGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/cave/cave_features.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                final String[] f = line.split("\t");
                out.add(new Golden(Long.parseLong(f[0]), f[1], f[2], Integer.parseInt(f[3])));
            }
        }
        return out;
    }

    static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    @Test
    void everyFeatureMatchesTheOracle() throws IOException {
        final List<Golden> goldens = goldens();
        assertThat(goldens).hasSize(18);
        final SyntheticCaves level = new SyntheticCaves();
        for (Golden g : goldens) {
            final int f = List.of(FEATURES).indexOf(g.feature());
            final StringBuilder out = new StringBuilder();
            int writes = 0;
            for (int i = 0; i < 64; i++) {
                final int x = (i * 37) % 200 - 100;
                final int y = -40 + (i * 13) % 100;
                final int z = (i * 53) % 200 - 100;
                level.reset();
                final XoroshiroRandomSource r = new XoroshiroRandomSource(
                    g.seed() ^ x * 341873128712L ^ z * 132897987541L ^ (y + 64L * f) * 0x9E3779B97F4A7C15L);
                switch (f) {
                    case 0 -> CaveSpikes.place(level, x, y, z, r, false);
                    case 1 -> CaveSpikes.place(level, x, y, z, r, true);
                    case 2 -> level.log.append(CaveColumn.place(level, x, y, z, r));
                    case 3 -> level.log.append(ThinSpike.CALCITE.place(level, x, y, z, r));
                    case 4 -> level.log.append(ThinSpike.MEGA_CALCITE.place(level, x, y, z, r));
                    default -> level.log.append(ThinSpike.ICICLE.place(level, x, y, z, r));
                }
                writes += level.writes.size();
                out.append(x).append(' ').append(y).append(' ').append(z).append('|').append(level.log).append('|')
                    .append(r.nextLong()).append('\n');
            }
            assertThat(Long.toHexString(fnv(out.toString()))).as(g.seed() + " " + g.feature()).isEqualTo(g.hash());
            assertThat(writes).as(g.seed() + " " + g.feature()).isEqualTo(g.writes());
        }
    }
}
