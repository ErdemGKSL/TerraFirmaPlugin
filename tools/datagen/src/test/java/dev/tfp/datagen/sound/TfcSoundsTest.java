/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-DATA-08: every TFC sound event outside the animal, peat and firestarter packs resolves to a shipped ogg. */
class TfcSoundsTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static JsonObject sounds;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        Assumptions.assumeTrue(TfcSounds.available(tfc));
        out = new OutputTree();
        TfcSounds.run(tfc, out);
        sounds = Json.parse(new String(out.get(TfcSounds.RP + "sounds.json"), StandardCharsets.UTF_8), "sounds").getAsJsonObject();
    }

    private static JsonObject tfcSounds() {
        return Json.parse(tfc.readString(TfcSounds.SOUNDS), "sounds").getAsJsonObject();
    }

    @Test
    void everyEventOutsideTheOtherPacksIsShippedWithTfcsSoundList() {
        Set<String> expected = new HashSet<>();
        for (String k : tfcSounds().keySet()) {
            if (TfcSounds.SHIPPED_ELSEWHERE.stream().noneMatch(k::startsWith)) {
                expected.add(k);
            }
        }
        assertThat(sounds.keySet()).containsExactlyInAnyOrderElementsOf(expected).contains("block.anvil.hit", "block.bellows.blow",
            "block.quern.drag", "random.dirt_slide_short", "random.rock_slide_long", "random.rock_smash", "item.knapping.stone",
            "item.cool", "item.jug.blow", "item.ceramic.break");
        for (String k : expected) {
            assertThat(sounds.get(k).toString()).as(k).isEqualTo(tfcSounds().get(k).toString().replace("tfc:", "tfp:"));
        }
    }

    @Test
    void everyNamedSoundResolvesToAShippedOggOrAVanillaName() {
        for (var e : sounds.entrySet()) {
            for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                assertThat(name).as(e.getKey()).doesNotStartWith("tfc:");
                if (name.startsWith("tfp:")) {
                    assertThat(out.contains(TfcSounds.RP + "sounds/" + name.substring(4) + ".ogg")).as(e.getKey() + " " + name).isTrue();
                }
            }
        }
        long oggs = out.files().keySet().stream().filter(k -> k.startsWith(TfcSounds.RP + "sounds/") && k.endsWith(".ogg")).count();
        assertThat(oggs).isGreaterThan(40);
    }

    @Test
    void theEventListAndTheSubtitlesAreWritten() {
        String events = new String(out.get(TfcSounds.EVENTS_FILE), StandardCharsets.UTF_8);
        assertThat(events).contains("\"block.anvil.hit\"").doesNotContain("entity.");
        String en = new String(out.get(TfcSounds.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("subtitles.block.tfc.anvil.hit").contains("subtitles.generic.tfc.rock_slide_long")
            .doesNotContain("subtitles.block.generic");
    }

    @Test
    void theCcByOggIsListedInTheThirdPartyManifest() {
        String manifest = TfcSounds.thirdParty(out);
        assertThat(manifest).contains("random/rock_slide_long_3.ogg").contains("CC-BY-4.0").contains(TfcSounds.CC_BY_SOURCE);
    }
}
