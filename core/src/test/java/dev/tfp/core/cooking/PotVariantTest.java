/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks every {@code tfc:pot_soup} and {@code tfc:pot_jam} row of {@code cooking_times.tsv} against the Java rules:
 * timing through {@link PotRules} (duration and temperature from the row), and the outputs through
 * {@link PotVariantRules}. The soup ingredient count and the jam jar count are the numbers in the recipe ids
 * ({@code soup_N}, {@code jam_<fruit>_N}); the export does not carry them, and they were checked against the TFC
 * recipe JSON at c3b4d9536 (every jam file has sealed and unsealed counts equal to N, every soup file has N ingredients).
 */
class PotVariantTest {
    private static final String TABLE = "dev/tfp/core/cooking/cooking_times.tsv";
    private static final Pattern COUNT_SUFFIX = Pattern.compile("_(\\d+)$");

    @Test
    void soupRowsGiveTheTfcTimingAndServings() throws IOException {
        List<String[]> soups = rows("tfc:pot_soup");
        assertThat(soups).hasSize(3);
        for (String[] row : soups) {
            int duration = Integer.parseInt(row[4]);
            float temperature = Float.parseFloat(row[5]);
            assertTiming(row[2], duration, temperature);
            int ingredients = countOf(row[2]);
            assertThat(PotVariantRules.soupServings(ingredients)).as(row[2]).isPositive();
        }
        // Golden values: soup_3 -> 2 servings, soup_4 -> 3, soup_5 -> 3.
        assertThat(PotVariantRules.soupServings(3)).isEqualTo(2);
        assertThat(PotVariantRules.soupServings(4)).isEqualTo(3);
        assertThat(PotVariantRules.soupServings(5)).isEqualTo(3);
    }

    @Test
    void jamRowsGiveTheTfcTimingAndJarCounts() throws IOException {
        List<String[]> jams = rows("tfc:pot_jam");
        assertThat(jams).hasSize(66);
        for (String[] row : jams) {
            int duration = Integer.parseInt(row[4]);
            float temperature = Float.parseFloat(row[5]);
            assertTiming(row[2], duration, temperature);
            assertThat(row[7]).as(row[2]).isEqualTo("sealed_result,unsealed_result");
            int jars = countOf(row[2]);
            assertThat(PotVariantRules.jamJarCount(jars, jars)).as(row[2]).isEqualTo(jars);
        }
    }

    @Test
    void soupAndJamConstantsMatchTfc() {
        assertThat(PotVariantRules.SOUP_HUNGER_VALUE).isEqualTo(4);
        assertThat(PotVariantRules.SOUP_DECAY_MODIFIER).isEqualTo(3.5f);
        assertThat(PotVariantRules.SOUP_BASE_WATER).isEqualTo(20f);
        assertThat(PotVariantRules.SOUP_BASE_SATURATION).isEqualTo(2f);
        assertThat(PotVariantRules.SOUP_NUTRITION_MULTIPLIER).isEqualTo(1.2f);
    }

    @Test
    void soupServingsFollowTfcFormula() {
        int[] expected = {1, 1, 2, 2, 3, 3, 4, 4, 5};
        for (int n = 0; n < expected.length; n++) {
            assertThat(PotVariantRules.soupServings(n)).as("ingredients %d", n).isEqualTo(expected[n]);
        }
        assertThatThrownBy(() -> PotVariantRules.soupServings(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jamJarCountRefusesMismatchedOrEmptyResults() {
        assertThat(PotVariantRules.jamJarCount(2, 2)).isEqualTo(2);
        assertThatThrownBy(() -> PotVariantRules.jamJarCount(2, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PotVariantRules.jamJarCount(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The TFC timing of a pot recipe row: boiling starts after the pre-boil time, and the output is taken one tick after the boil. */
    private static void assertTiming(String id, int duration, float temperature) {
        assertThat(duration).as(id).isPositive();
        int finish = PotRules.PRE_BOIL_TIME + duration + 1;
        assertThat(PotRules.advance(PotRules.PotState.IDLE, finish, true, duration).finishedAtTick()).as(id).isEqualTo(finish);
        assertThat(PotRules.advance(PotRules.PotState.IDLE, finish - 1, true, duration).finishedAtTick()).as(id).isEqualTo(-1);
        // The TFC soup and jam recipes all use 300 C (the pot boils only above the recipe temperature, see PotRecipe.isHotEnough).
        assertThat(temperature).as(id).isEqualTo(300f);
    }

    private static int countOf(String id) {
        Matcher m = COUNT_SUFFIX.matcher(id);
        assertThat(m.find()).as("count suffix in %s", id).isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static List<String[]> rows(String type) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = PotVariantTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("kind\t")) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if ("recipe".equals(f[0]) && type.equals(f[3])) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
