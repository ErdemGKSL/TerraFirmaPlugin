/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.geode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-26: TFC's geode against vanilla's GeodeFeature. The rows were written by a scratch harness outside the repository
 * that runs Minecraft 1.21.11's {@code Feature.GEODE.place} with TFC's configuration (stand-in blocks per layer) on a
 * synthetic level: stone, with a water pocket next to the origin (world kind 1) or air from three blocks above the origin
 * (kind 2, aborted by the invalid-block threshold). Block tags are unbound in the harness, so only air counts as invalid and
 * every block is replaceable; the test's world says the same. Each row: level seed, random seed (XoroshiroRandomSource),
 * origin, world kind, placed, block count, SHA-256 of the sorted "x,y,z,block" lines, and the count per block.
 */
class GeodeGoldenTest {
    private static Geode.World world(int kind, int ox, int oy, int oz) {
        return new Geode.World() {
            private boolean water(int x, int y, int z) {
                return kind == 1 && Math.abs(x - ox - 2) <= 2 && Math.abs(y - oy) <= 1 && Math.abs(z - oz) <= 2;
            }

            private boolean air(int x, int y, int z) {
                return kind == 2 && y >= oy + 3;
            }

            @Override
            public boolean invalid(int x, int y, int z) {
                return air(x, y, z);
            }

            @Override
            public boolean replaceable(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean clusterCanGrow(int x, int y, int z) {
                return air(x, y, z) || water(x, y, z);
            }
        };
    }

    private static String block(Geode.Layer layer) {
        return switch (layer) {
            case FILLING, CRACK, PLACEMENT -> "air";
            case INNER -> "inner";
            case ALTERNATE_INNER -> "alternate";
            case MIDDLE -> "middle";
            case OUTER -> "outer";
        };
    }

    @Test
    void geodesEqualVanillasGeodeFeature() throws Exception {
        final List<String> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
            GeodeGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/geode/geodes.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("#")) {
                    rows.add(line);
                }
            }
        }
        assertThat(rows).hasSize(20);
        int aborted = 0;
        for (String row : rows) {
            final String[] r = row.split(" ", 10);
            final int ox = Integer.parseInt(r[2]);
            final int oy = Integer.parseInt(r[3]);
            final int oz = Integer.parseInt(r[4]);
            final Geode geode = new Geode(Long.parseLong(r[0]), GeodeConfig.TFC);
            final Map<Long, Geode.Layer> placed = geode.place(new XoroshiroRandomSource(Long.parseLong(r[1])), ox, oy, oz,
                world(Integer.parseInt(r[5]), ox, oy, oz));
            final List<String> lines = new ArrayList<>();
            final Map<String, Integer> counts = new TreeMap<>();
            for (Map.Entry<Long, Geode.Layer> e : placed.entrySet()) {
                final long p = e.getKey();
                lines.add(Geode.unpackX(p) + "," + Geode.unpackY(p) + "," + Geode.unpackZ(p) + "," + block(e.getValue()));
                counts.merge(block(e.getValue()), 1, Integer::sum);
            }
            lines.sort(null);
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                sha.update((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            assertThat(!placed.isEmpty()).as(row).isEqualTo(Boolean.parseBoolean(r[6]));
            assertThat(placed.size()).as(row).isEqualTo(Integer.parseInt(r[7]));
            assertThat(counts.toString()).as(row).isEqualTo(r[9]);
            assertThat(HexFormat.of().formatHex(sha.digest())).as(row).isEqualTo(r[8]);
            if (placed.isEmpty()) {
                aborted++;
            }
        }
        assertThat(aborted).isEqualTo(1);
    }

    @Test
    void theConfigIsTfcsWithTheVanillaDefaults() throws IOException {
        final GeodeConfig c = GeodeConfig.TFC;
        assertThat(c.outerLayer()).isEqualTo("tfp:rock/hardened/basalt");
        assertThat(c.middleLayer()).isEqualTo("tfp:rock/hardened/quartzite");
        assertThat(c.innerLayer()).isEqualTo("tfp:rock/hardened/quartzite");
        assertThat(c.alternateInnerLayer()).isEqualTo("tfp:ore/amethyst/quartzite");
        assertThat(c.filling()).isEqualTo("minecraft:air");
        assertThat(c.useAlternateLayer0Chance()).isEqualTo(0.3);
        assertThat(c.usePotentialPlacementsChance()).isEqualTo(0.35);
        assertThat(c.invalidBlocksThreshold()).isEqualTo(1);
        assertThat(c.rarity()).isEqualTo(50);
        assertThat(c.minY()).isEqualTo(-48);
        assertThat(c.maxY()).isEqualTo(30);
        assertThat(c.outerWallDistanceMin()).isEqualTo(4);
        assertThat(c.outerWallDistanceMax()).isEqualTo(5);
        assertThat(c.filling2()).isEqualTo(1.7);
        assertThat(c.crackPointOffset()).isEqualTo(2);
        final Path dir = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), "src/main/resources/data/tfc/worldgen");
        assumeTrue(Files.isDirectory(dir), "no TFC checkout");
        final var configured = JsonParser.parseString(Files.readString(dir.resolve("configured_feature/geode.json"))).getAsJsonObject();
        final var placed = JsonParser.parseString(Files.readString(dir.resolve("placed_feature/geode.json"))).getAsJsonObject();
        configured.remove("__comment__");
        placed.remove("__comment__");
        assertThat(JsonParser.parseString(GeodeConfig.CONFIGURED)).isEqualTo(configured);
        assertThat(JsonParser.parseString(GeodeConfig.PLACED)).isEqualTo(placed);
    }

    @Test
    void aGeodeAcrossChunksIsTheSameInAnyChunkOrder() {
        final Geodes geodes = new Geodes(12345L);
        // the world: stone everywhere (no geode aborts)
        final Geode.World stone = new Geode.World() {
            @Override
            public boolean invalid(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean replaceable(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean clusterCanGrow(int x, int y, int z) {
                return false;
            }
        };
        int cx = 0;
        int[] o = geodes.origin(cx, 0);
        while (o == null || (o[0] & 15) < 8 || (o[2] & 15) < 8) {
            o = geodes.origin(++cx, 0);
        }
        // The geode reaches the chunks east, south and south-east of its origin: fill those four in two orders
        final Map<Long, Geode.Layer> forward = new HashMap<>();
        final Map<Long, Geode.Layer> backward = new HashMap<>();
        final int[][] chunks = {{cx, 0}, {cx + 1, 0}, {cx, 1}, {cx + 1, 1}};
        for (int[] c : chunks) {
            geodes.chunk(c[0], c[1], stone, (x, y, z, l) -> forward.put(Geode.pack(x, y, z), l));
        }
        for (int i = chunks.length - 1; i >= 0; i--) {
            geodes.chunk(chunks[i][0], chunks[i][1], stone, (x, y, z, l) -> backward.put(Geode.pack(x, y, z), l));
        }
        assertThat(forward).isEqualTo(backward);
        final Map<Long, Geode.Layer> own = geodes.placed(cx, 0, stone);
        int spanning = 0;
        for (long p : own.keySet()) {
            if (Geode.unpackX(p) >> 4 != cx || Geode.unpackZ(p) >> 4 != 0) {
                spanning++;
            }
        }
        assertThat(spanning).isPositive();
    }

    @Test
    void aboutOneGeodePerFiftyChunksBetweenMinus48And30() {
        final Geodes geodes = new Geodes(-7L);
        int found = 0;
        final int n = 2000;
        for (int i = 0; i < n; i++) {
            final int[] o = geodes.origin(i % 50 - 25, i / 50 - 20);
            if (o != null) {
                found++;
                assertThat(o[1]).isBetween(-48, 30);
                assertThat(o[0] >> 4).isEqualTo(i % 50 - 25);
            }
        }
        // binomial(2000, 1/50): mean 40, sd 6.26; 99% bound +-2.58 sd
        assertThat(found).isBetween(24, 56);
    }

    @Test
    void packingRoundTrips() {
        for (int[] p : new int[][] {{0, 0, 0}, {-1, -64, -1}, {30000, 319, -30000}, {-123, 5, 456}}) {
            final long packed = Geode.pack(p[0], p[1], p[2]);
            assertThat(new int[] {Geode.unpackX(packed), Geode.unpackY(packed), Geode.unpackZ(packed)}).containsExactly(p);
        }
    }
}
