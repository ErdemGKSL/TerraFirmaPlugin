/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.erosion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-20: TFC's erosion pass on synthetic columns, against a scratch oracle (kept outside the repo) that runs the
 * column loop of {@code ErosionFeature.place} (c3b4d9536) copied verbatim over a char column.
 *
 * <p>Alphabet: {@code a} air, {@code c} cave air, {@code w} water and {@code p} a plant (fragile: a falling block passes),
 * {@code d} dirt, {@code g} gravel, {@code s} sand (landslide recipes), {@code 0}-{@code 4} raw rock, {@code H}-{@code L}
 * hardened rock 0-4, {@code o} ore (sturdy, never hardened). The rock layer at y is {@code floorMod(y / 7, 5)}; the aquifer
 * gives no substance where {@code y % 13 == 0}, water below y 20 and air above. 256 columns from {@code new Random(1000 + i)}:
 * min y {@code -8 - nextInt(8)}, height {@code 40 + nextInt(30)}, runs of blocks; the lines
 * {@code "minY before>after"} hashed with FNV-1a 64.
 */
class ErosionGoldenTest {
    /** The oracle's hash of the 256 columns before and after erosion. */
    private static final String GOLDEN = "2a781317e6ad7732";
    private static final String ALPHABET = "aacwpddgs01234HIo";

    /** A char column over the {@link Erosion.Column} view. */
    static final class CharColumn implements Erosion.Column {
        final char[] col;
        final int minY;

        CharColumn(char[] col, int minY) {
            this.col = col;
            this.minY = minY;
        }

        char at(int y) {
            return col[y - minY];
        }

        @Override
        public boolean air(int y) {
            return at(y) == 'a' || at(y) == 'c';
        }

        @Override
        public boolean landslides(int y) {
            final char c = at(y);
            return c == 'd' || c == 'g' || c == 's';
        }

        @Override
        public boolean fragile(int y) {
            return at(y) == 'w' || at(y) == 'p';
        }

        @Override
        public int hardenable(int y) {
            final char c = at(y);
            return c >= '0' && c <= '4' ? c - '0' : -1;
        }

        @Override
        public int rockAt(int y) {
            return Math.floorMod(y / 7, 5);
        }

        @Override
        public void setHardened(int y, int rock) {
            col[y - minY] = (char) ('H' + rock);
        }

        @Override
        public boolean hasSubstance(int y) {
            return Math.floorMod(y, 13) != 0;
        }

        @Override
        public boolean setSubstance(int y) {
            if (!hasSubstance(y)) {
                return false;
            }
            col[y - minY] = y < 20 ? 'w' : 'a';
            return true;
        }
    }

    record Case(CharColumn column, int base, String before) {
    }

    static Case column(int i) {
        final Random r = new Random(1000L + i);
        final int minY = -8 - r.nextInt(8);
        final int height = 40 + r.nextInt(30);
        final char[] col = new char[height + 2];
        for (int k = 0; k < col.length; k++) {
            col[k] = k > 0 && r.nextInt(3) > 0 ? col[k - 1] : ALPHABET.charAt(r.nextInt(ALPHABET.length()));
        }
        return new Case(new CharColumn(col, minY), minY + height, new String(col));
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
    void columnsMatchTheOracle() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            final Case c = column(i);
            Erosion.column(c.column(), c.base(), c.column().minY);
            out.append(c.column().minY).append(' ').append(c.before()).append('>').append(new String(c.column().col)).append('\n');
        }
        assertThat(Long.toHexString(fnv(out.toString()))).isEqualTo(GOLDEN);
    }

    /**
     * After erosion no stack of landslide-prone blocks ends on a block it could fall through, and no raw rock stands on a
     * fragile block, anywhere from the base height down to the lowest non-landslide block of the column.
     */
    @Test
    void noLandslideProneStackStandsOnAFragileBlock() {
        for (int i = 0; i < 2048; i++) {
            final Case c = column(i);
            final CharColumn col = c.column();
            Erosion.column(col, c.base(), col.minY);
            for (int y = c.base(); y > col.minY; y--) {
                if (col.landslides(y)) {
                    int below = y - 1;
                    while (below > col.minY && col.landslides(below)) {
                        below--;
                    }
                    if (!col.landslides(below)) {
                        assertThat(col.air(below) || col.fragile(below)).as("column " + i + " y " + y + ": " + new String(col.col))
                            .isFalse();
                    }
                }
                if (col.hardenable(y) >= 0) {
                    assertThat(col.air(y - 1) || col.fragile(y - 1)).as("raw rock on a fragile block, column " + i + " y " + y)
                        .isFalse();
                }
            }
        }
    }
}
