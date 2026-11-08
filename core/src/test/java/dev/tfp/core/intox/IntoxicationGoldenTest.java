/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.intox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-08: golden rows ({@code intoxication_golden.tsv}) from a scratch oracle kept outside the repository that
 * holds literal copies of TFC c3b4d9536 {@code PlayerInfo.addIntoxication}/{@code getIntoxication}, vanilla 1.21.1
 * {@code Mth.clampedMap} and {@code ForgeEventHandler.onServerChat}, with {@code java.util.Random(seed)} standing in
 * for the player's {@code RandomSource} (the same draws in the same order give the same result). Floats are compared
 * by their bits. Row kinds: {@code seq} (from sober, {@code now,ticks=stored} after each add), {@code level}
 * ({@code until now max bits}), {@code chance} ({@code levelBits chanceBits}), {@code slur}
 * ({@code seed levelBits [message] [result]}).
 */
class IntoxicationGoldenTest {
    private static List<String[]> rows(String kind) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = Objects.requireNonNull(
                IntoxicationGoldenTest.class.getResourceAsStream("intoxication_golden.tsv"));
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f[0].equals(kind)) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    private static String unwrap(String bracketed) {
        assertThat(bracketed).startsWith("[").endsWith("]");
        return bracketed.substring(1, bracketed.length() - 1);
    }

    @Test
    void addMatchesTfcStepByStep() throws IOException {
        List<String[]> seqs = rows("seq");
        assertThat(seqs).hasSize(6);
        for (String[] row : seqs) {
            long until = Intoxication.SOBER;
            for (int i = 1; i < row.length; i++) {
                String[] step = row[i].split("[,=]");
                until = Intoxication.add(until, Long.parseLong(step[0]), Long.parseLong(step[1]),
                        Intoxication.DEFAULT_MAX_TICKS);
                assertThat(until).as(String.join(" ", row) + " step " + i).isEqualTo(Long.parseLong(step[2]));
            }
        }
    }

    @Test
    void levelDecaysLinearlyAsTfc() throws IOException {
        List<String[]> levels = rows("level");
        assertThat(levels).hasSize(62);
        for (String[] row : levels) {
            float level = Intoxication.level(Long.parseLong(row[1]), Long.parseLong(row[2]), Integer.parseInt(row[3]));
            assertThat(Float.floatToIntBits(level)).as(String.join(" ", row)).isEqualTo(Integer.parseInt(row[4]));
        }
    }

    @Test
    void slurChanceIsTfcsClampedMap() throws IOException {
        List<String[]> chances = rows("chance");
        assertThat(chances).hasSize(12);
        for (String[] row : chances) {
            float level = Float.intBitsToFloat(Integer.parseInt(row[1]));
            assertThat(Float.floatToIntBits(Intoxication.slurChance(level))).as("level " + level)
                    .isEqualTo(Integer.parseInt(row[2]));
        }
    }

    @Test
    void chatIsSlurredExactlyAsTfcWithTheSameDraws() throws IOException {
        List<String[]> slurs = rows("slur");
        assertThat(slurs).hasSize(48);
        int changed = 0;
        for (String[] row : slurs) {
            java.util.Random random = new java.util.Random(Long.parseLong(row[1]));
            float level = Float.intBitsToFloat(Integer.parseInt(row[2]));
            String message = unwrap(row[3]);
            String result = ChatSlur.slur(message, level, new ChatSlur.Random() {
                @Override
                public float nextFloat() {
                    return random.nextFloat();
                }

                @Override
                public int nextInt(int bound) {
                    return random.nextInt(bound);
                }
            });
            assertThat(result).as(String.join(" | ", row)).isEqualTo(unwrap(row[4]));
            if (!result.equals(message)) {
                changed++;
            }
        }
        assertThat(changed).as("the rows exercise real slurring").isGreaterThan(20);
    }

    @Test
    void aJugOfBeerAtATime() {
        // Four drinks of 4000 ticks: 16000 of 43200 = 0.37 (slurs); the 11th drink reaches the 36 hour cap.
        long until = Intoxication.SOBER;
        for (int i = 0; i < 4; i++) {
            until = Intoxication.add(until, 0, 4000, Intoxication.DEFAULT_MAX_TICKS);
        }
        assertThat(until).isEqualTo(16000);
        assertThat(ChatSlur.slurs(Intoxication.level(until, 0, Intoxication.DEFAULT_MAX_TICKS))).isTrue();
        assertThat(ChatSlur.slurs(Intoxication.level(until, 7360, Intoxication.DEFAULT_MAX_TICKS)))
                .as("8640 left = exactly 0.2").isFalse();
        for (int i = 0; i < 7; i++) {
            until = Intoxication.add(until, 0, 4000, Intoxication.DEFAULT_MAX_TICKS);
        }
        assertThat(until).isEqualTo(Intoxication.DEFAULT_MAX_TICKS);
        assertThat(Intoxication.level(until, 0, Intoxication.DEFAULT_MAX_TICKS)).isEqualTo(1f);
        assertThat(Intoxication.remaining(until, 43_000)).isEqualTo(200);
        assertThat(Intoxication.remaining(Intoxication.SOBER, 5)).isZero();
    }

    @Test
    void lazyEqualsStepped() {
        // Reading the level once after a long gap equals reading it every tick on the way (no state changes on read).
        long until = Intoxication.add(Intoxication.SOBER, 1000, 30_000, Intoxication.DEFAULT_MAX_TICKS);
        float stepped = 0;
        for (long t = 1000; t <= 25_000; t++) {
            stepped = Intoxication.level(until, t, Intoxication.DEFAULT_MAX_TICKS);
        }
        assertThat(Intoxication.level(until, 25_000, Intoxication.DEFAULT_MAX_TICKS)).isEqualTo(stepped);
        // Adding after it wore off counts from now, never from the stale tick.
        long later = Intoxication.add(until, 100_000, 4000, Intoxication.DEFAULT_MAX_TICKS);
        assertThat(later).isEqualTo(104_000);
    }

    @Test
    void soberChatIsUntouchedWithoutDraws() {
        ChatSlur.Random none = new ChatSlur.Random() {
            @Override
            public float nextFloat() {
                throw new AssertionError("no draw expected");
            }

            @Override
            public int nextInt(int bound) {
                throw new AssertionError("no draw expected");
            }
        };
        assertThat(ChatSlur.slur("hello  there ", 0.2f, none)).isEqualTo("hello  there ");
        assertThat(ChatSlur.slur("hello", 0f, none)).isEqualTo("hello");
    }
}
