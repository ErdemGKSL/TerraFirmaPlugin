/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * PAR-HUD-07: the vanilla HUD sprites the bars replace, one list per MC version family, and the union list that
 * {@link HudLayouts} turns into transparent sprites in the CraftEngine pack (one pack serves every supported client).
 */
class HiddenVanillaSpritesTest {

    private static final String DIR = "/dev/tfp/datagen/hud/";
    /** The families we support (ARCHITECTURE.md: 1.21.4+ and 26.x). */
    private static final List<String> FAMILIES = List.of("1.21", "26");
    /** The sprite groups the hide rows cover: hearts, food, air bubbles, armour. */
    private static final Pattern HIDDEN = Pattern.compile("(heart/.*|food_.*|air.*|armor_.*)\\.png");
    private static final String JAR_PREFIX = "assets/minecraft/textures/gui/sprites/hud/";

    @Test
    void everyVersionFamilyHasASpriteList() throws IOException {
        for (String family : FAMILIES) {
            List<String> sprites = lines("hidden-vanilla-sprites-" + family + ".txt");
            assertThat(sprites).as(family).isNotEmpty().doesNotHaveDuplicates().allMatch(s -> HIDDEN.matcher(s).matches());
            // Each family carries every group the hide rows name: 47 hearts, 6 food, 3 air, 3 armour.
            assertThat(sprites.stream().filter(s -> s.startsWith("heart/")).count()).as(family + " hearts").isEqualTo(47);
            assertThat(sprites.stream().filter(s -> s.startsWith("food_")).count()).as(family + " food").isEqualTo(6);
            assertThat(sprites.stream().filter(s -> s.startsWith("air")).count()).as(family + " air").isEqualTo(3);
            assertThat(sprites.stream().filter(s -> s.startsWith("armor_")).count()).as(family + " armour").isEqualTo(3);
        }
    }

    @Test
    void consumedListIsTheUnionOfTheFamilyLists() throws IOException {
        Set<String> union = new TreeSet<>();
        for (String family : FAMILIES) {
            union.addAll(lines("hidden-vanilla-sprites-" + family + ".txt"));
        }
        assertThat(new TreeSet<>(lines("hidden-vanilla-sprites.txt"))).isEqualTo(union);
    }

    @Test
    void thePackHidesEveryFamilysSprites() throws IOException {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        for (String family : FAMILIES) {
            for (String sprite : lines("hidden-vanilla-sprites-" + family + ".txt")) {
                assertThat(out.contains("craftengine/tfp/resourcepack/assets/minecraft/textures/gui/sprites/hud/" + sprite))
                    .as(family + ": " + sprite).isTrue();
            }
        }
    }

    /**
     * Rechecks a family list against the real client jar when the machine has it (the headless-client install in
     * {@code /opt/mcclient}, docs/testing/headless.md); skipped elsewhere.
     */
    @ParameterizedTest
    @CsvSource({"1.21, 1.21.11", "26, 26.3"})
    void familyListMatchesTheClientJar(String family, String version) throws IOException {
        Path jar = Path.of("/opt/mcclient/versions", version, version + ".jar");
        assumeTrue(Files.isRegularFile(jar), "no client jar " + jar);
        Set<String> inJar = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                String name = e.nextElement().getName();
                if (name.startsWith(JAR_PREFIX) && HIDDEN.matcher(name.substring(JAR_PREFIX.length())).matches()) {
                    inJar.add(name.substring(JAR_PREFIX.length()));
                }
            }
        }
        assertThat(new TreeSet<>(lines("hidden-vanilla-sprites-" + family + ".txt"))).isEqualTo(inJar);
    }

    private static List<String> lines(String file) throws IOException {
        try (InputStream in = HiddenVanillaSpritesTest.class.getResourceAsStream(DIR + file)) {
            assertThat(in).as("resource " + file).isNotNull();
            List<String> out = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.strip());
                }
            }
            return out;
        }
    }
}
