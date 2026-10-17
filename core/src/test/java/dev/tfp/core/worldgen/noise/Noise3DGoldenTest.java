/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/OpenSimplex3D.java @ c3b4d9536 (golden values; the oracle also compiled
 * Noise3D.java and FastNoiseLite.java)
 */
package dev.tfp.core.worldgen.noise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden 3D noise. For four seeds, three noises: the raw {@code OpenSimplex3D(seed)}, the tall canyon cliff noise
 * {@code OpenSimplex3D(seed).octaves(2).spread(0.1f).scaled(0, 3)}, and {@code Noise3D}'s own default {@code octaves(3)},
 * {@code spread(0.05)} and {@code scaled(-1, 1, 2, 5)} over {@code OpenSimplex3D(seed + 1)}. Each line holds a hash of the raw
 * double bits over a 9 x 5 x 9 grid ({@code x = 1.37 i, y = 0.91 j, z = 1.13 k} for {@code i, k} in -40..40 step 10 and
 * {@code j} in -20..20 step 10) and three exact values.
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit): an oracle harness compiled TFC's
 * {@code Noise3D}, {@code OpenSimplex3D} and {@code FastNoiseLite} unchanged and printed these lines (Minecraft 1.21.11
 * classes on the class path; no server was started).
 */
class Noise3DGoldenTest {
    private static final String[] GOLDEN = {
        "NOISE3D seed=0 kind=0 hash=-166090746338738176L v1=0.12702929973602295 v2=-0.0034042303450405598 v3=0.10793507844209671",
        "NOISE3D seed=0 kind=1 hash=3473851395118039040L v1=1.32612169906497 v2=1.760999634861946 v3=1.2987844794988632",
        "NOISE3D seed=0 kind=2 hash=-5542469091922223104L v1=3.4673091205768287 v2=3.9327858071774244 v3=3.4877040567807853",
        "NOISE3D seed=42 kind=0 hash=1603236413673832448L v1=0.23551823198795319 v2=-0.33942508697509766 v3=0.05689670890569687",
        "NOISE3D seed=42 kind=1 hash=-8579962896377790464L v1=1.5576612576842308 v2=1.638717144727707 v3=1.6876037120819092",
        "NOISE3D seed=42 kind=2 hash=5345195038193297727L v1=3.56741974176839 v2=2.9953538738191128 v3=4.53926769271493",
        "NOISE3D seed=-7 kind=0 hash=-3807406312430501888L v1=0.024011889472603798 v2=-2.577487612143159E-4 v3=0.15507113933563232",
        "NOISE3D seed=-7 kind=1 hash=9105207946387652608L v1=1.4966017417609692 v2=1.4900761172175407 v3=1.467886439524591",
        "NOISE3D seed=-7 kind=2 hash=-8717216685921813504L v1=3.532717733643949 v2=3.328743100166321 v3=3.301690060645342",
        "NOISE3D seed=123456789 kind=0 hash=-5230900912620306432L v1=0.3420105278491974 v2=0.21412572264671326 v3=0.4869363307952881",
        "NOISE3D seed=123456789 kind=1 hash=-8972546135345856512L v1=1.6129146069288254 v2=2.217691093683243 v3=1.9355524778366089",
        "NOISE3D seed=123456789 kind=2 hash=3465168096029854208L v1=3.532691611442715 v2=3.7710126731544733 v3=3.3488582372665405",
    };

    private static long mix(long h, long v) {
        return h * 31 + v;
    }

    @Test
    void matchesTfc() {
        final long[] seeds = {0L, 42L, -7L, 123456789L};
        final List<String> lines = new ArrayList<>();
        for (long s : seeds) {
            final Noise3D raw = new OpenSimplex3D(s);
            final Noise3D cliff = new OpenSimplex3D(s).octaves(2).spread(0.1f).scaled(0, 3);
            final Noise3D base = new OpenSimplex3D(s + 1);
            final Noise3D wrapped = ((Noise3D) base::noise).octaves(3).spread(0.05).scaled(-1, 1, 2, 5);
            final Noise3D[] all = {raw, cliff, wrapped};
            for (int k = 0; k < 3; k++) {
                long h = 0;
                for (int x = -40; x <= 40; x += 10) {
                    for (int y = -20; y <= 20; y += 10) {
                        for (int z = -40; z <= 40; z += 10) {
                            h = mix(h, Double.doubleToLongBits(all[k].noise(x * 1.37, y * 0.91, z * 1.13)));
                        }
                    }
                }
                lines.add(String.format("NOISE3D seed=%d kind=%d hash=%dL v1=%s v2=%s v3=%s", s, k, h,
                    Double.toString(all[k].noise(0.5, 0.25, -0.75)), Double.toString(all[k].noise(-123.4, 64, 987.6)),
                    Double.toString(all[k].noise(31, -5, -2048.5))));
            }
        }
        assertThat(lines).containsExactly(GOLDEN);
    }

    @Test
    void scaledStaysInRangeAndIsDeterministic() {
        final Noise3D cliff = new OpenSimplex3D(99L).octaves(2).spread(0.1f).scaled(0, 3);
        final Noise3D again = new OpenSimplex3D(99L).octaves(2).spread(0.1f).scaled(0, 3);
        for (int x = -64; x <= 64; x += 7) {
            for (int y = -64; y <= 128; y += 9) {
                for (int z = -64; z <= 64; z += 5) {
                    final double v = cliff.noise(x, y, z);
                    assertThat(v).isBetween(0.0, 3.0).isEqualTo(again.noise(x, y, z));
                }
            }
        }
    }
}
