/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-06: vanilla's dye mix (golden values computed by hand from DyedItemColor.applyDyes) and item syntax. */
class DyeMixingTest {
    private static final int RED = 0xB02E26; // DyeColor.RED texture diffuse colour
    private static final int BLUE = 0x3C44AA;

    @Test
    void oneDyeOnAnUndyedItemIsTheDyesColour() {
        assertThat(DyeMixing.mix(OptionalInt.empty(), List.of(RED))).isEqualTo(RED);
    }

    @Test
    void twoColoursAverageAndKeepTheMeanIntensity() {
        // avg (118, 57, 104), mean of maxima 173, scaled by 173 / 118 → (173, 83, 152)
        assertThat(DyeMixing.mix(OptionalInt.of(RED), List.of(BLUE))).isEqualTo(0xAD5398);
        assertThat(DyeMixing.mix(OptionalInt.empty(), List.of(RED, BLUE))).isEqualTo(0xAD5398);
    }

    @Test
    void componentsBecomeItemSyntax() {
        assertThat(ItemSyntax.of("minecraft:goat_horn", JsonParser.parseString(
                "{\"minecraft:instrument\": \"minecraft:admire_goat_horn\"}").getAsJsonObject()))
                .isEqualTo("minecraft:goat_horn[minecraft:instrument=\"minecraft:admire_goat_horn\"]");
        assertThat(ItemSyntax.of("minecraft:stick", JsonParser.parseString(
                "{\"minecraft:custom_data\": {\"a\": [1, 2], \"b\": \"x\\\"y\"}}").getAsJsonObject()))
                .isEqualTo("minecraft:stick[minecraft:custom_data={\"a\":[1,2],\"b\":\"x\\\"y\"}]");
    }
}
