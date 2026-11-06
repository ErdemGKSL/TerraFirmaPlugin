/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.thirst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-04: rain drinking assumes that in a TFC world the client renders a seasonal biome variant with the TFC biome
 * climate ({@link RainDrinking#TFC_BIOME_TEMPERATURE}, precipitation). This pins that against the generated variants.
 */
class SeasonalVariantRainTest {
    private static final Pattern PRECIPITATION = Pattern.compile("\"has_precipitation\"\\s*:\\s*(true|false)");
    private static final Pattern TEMPERATURE = Pattern.compile("\"temperature\"\\s*:\\s*(-?[0-9.]+)");

    @Test
    void everyGeneratedVariantRainsWithTheTfcBiomeTemperature() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        Path variants = data.resolveSibling("datapack").resolve("tfp_seasonal_biomes/data/tfp/worldgen/biome");
        assumeTrue(Files.isDirectory(variants), "no generated seasonal variants at " + variants.toAbsolutePath());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(variants)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertThat(files).hasSizeGreaterThan(100);
        for (Path f : files) {
            String json = Files.readString(f);
            Matcher p = PRECIPITATION.matcher(json);
            Matcher t = TEMPERATURE.matcher(json);
            assertThat(p.find()).as(f.toString()).isTrue();
            assertThat(t.find()).as(f.toString()).isTrue();
            assertThat(p.group(1)).as(f.toString()).isEqualTo("true");
            assertThat(Float.parseFloat(t.group(1))).as(f.toString()).isEqualTo(RainDrinking.TFC_BIOME_TEMPERATURE);
        }
    }
}
