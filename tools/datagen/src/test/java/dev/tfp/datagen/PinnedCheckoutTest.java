/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VisualOutputs;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Runs datagen on the real pinned TFC checkout when it is available (-Ptfc.checkout, $TFC_CHECKOUT, /opt/tfc). */
class PinnedCheckoutTest {
    private static Datagen.Result cached;

    private static synchronized Datagen.Result result(Path checkout) {
        if (cached == null) {
            cached = Datagen.generate(checkout, TfcCheckout.PINNED_COMMIT);
        }
        return cached;
    }

    @Test
    void fullCheckoutGeneratesValidRockSoilOreContent() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");

        Datagen.Result r = result(checkout);
        // 21 rocks x 10 cube types + 7 magma, 53 ore kinds x 21 rocks, 8 dirt, 8 clay, 7 sand
        assertThat(r.report().emittedCount()).isEqualTo(21 * 10 + 7 + 53 * 21 + 8 + 8 + 7);
        assertThat(r.report().count("ore_textures_flattened", 0)).isEqualTo(53 * 21);
        assertThat(r.report().missing()).isEmpty();
        CeYamlCheck.assertValid(r.tree().files());
        CeYamlCheck.assertRandomPillarVisualsMatch(r.tree().files(), "rock/raw/granite");
        VisualContentCheck.assertComplete(r.tree().files());
    }

    /** Every item of the generated food and metal configs has its display name in the English pack lang file. */
    @Test
    void everyGeneratedFoodAndMetalItemHasAnEnglishName() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");
        java.util.Map<String, byte[]> files = result(checkout).tree().files();
        String langPath = files.keySet().stream().filter(k -> k.endsWith("resourcepack/assets/tfp/lang/en_us.json"))
            .findFirst().orElseThrow();
        com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseString(
            new String(files.get(langPath), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        java.util.regex.Pattern key = java.util.regex.Pattern.compile("item_lang_key: (\\S+)");
        java.util.List<String> missing = new java.util.ArrayList<>();
        int checked = 0;
        for (String file : java.util.List.of("configuration/food.yml", "configuration/metal.yml")) {
            String path = files.keySet().stream().filter(k -> k.endsWith(file)).findFirst().orElseThrow();
            java.util.regex.Matcher m = key.matcher(new String(files.get(path), java.nio.charset.StandardCharsets.UTF_8));
            while (m.find()) {
                checked++;
                if (!lang.has(m.group(1))) {
                    missing.add(m.group(1));
                }
            }
        }
        assertThat(checked).as("food and metal item keys").isGreaterThan(200);
        assertThat(missing).as("item_lang_key without an English name").isEmpty();
    }

    /**
     * M1-DGN-04 done-criterion: the default profiles (1.21 → level-3a, 26 → level-2) leave at least the
     * solid-pool reserve (500) for other CraftEngine packs, with Nether/End blocks off (the stricter case).
     */
    @Test
    void defaultProfilesLeaveTheSolidReserveFree() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");
        Datagen.Result r = result(checkout);
        com.google.gson.JsonObject index = com.google.gson.JsonParser.parseString(new String(
            r.tree().files().get(VisualOutputs.INDEX), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        java.util.Map<String, Integer> free = new java.util.TreeMap<>();
        for (var e : index.getAsJsonArray("allocations")) {
            var a = e.getAsJsonObject();
            free.put(a.get("family").getAsString() + "/" + a.get("profile").getAsString() + "/ne="
                + a.get("nether_end").getAsBoolean(), a.get("solid_free").getAsInt());
        }
        assertThat(free).hasSize(20);
        assertThat(index.getAsJsonObject("families").getAsJsonObject("1.21").get("default_profile").getAsString())
            .isEqualTo("level-3a");
        assertThat(index.getAsJsonObject("families").getAsJsonObject("26").get("default_profile").getAsString())
            .isEqualTo("level-2");
        assertThat(free.get("1.21/level-3a/ne=false")).isGreaterThanOrEqualTo(Profile.SOLID_RESERVE_WARNING);
        assertThat(free.get("26/level-2/ne=false")).isGreaterThanOrEqualTo(Profile.SOLID_RESERVE_WARNING);
        // every profile fits (no overflow) and higher levels never use more of the solid pool
        for (String fam : java.util.List.of("1.21", "26")) {
            int prev = -1;
            for (Profile p : Profile.values()) {
                int f = free.get(fam + "/" + p.id() + "/ne=false");
                assertThat(f).isGreaterThanOrEqualTo(prev);
                assertThat(free.get(fam + "/" + p.id() + "/ne=true")).isGreaterThanOrEqualTo(f);
                prev = f;
            }
        }
    }
}
