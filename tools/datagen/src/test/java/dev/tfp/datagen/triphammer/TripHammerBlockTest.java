/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.triphammer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.rotation.RotationBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-MECH-27: the tfp:trip_hammer entry on the real checkout. */
class TripHammerBlockTest {
    private static OutputTree out;
    private static long states;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(TripHammerBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = TripHammerBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(TripHammerBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        Map<String, Object> blocks = (Map<String, Object>) root().get("blocks");
        assertThat(blocks).hasSize(1).containsKey("tfp:trip_hammer");
        return (Map<String, Object>) blocks.get("tfp:trip_hammer");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states() {
        return (Map<String, Object>) block().get("states");
    }

    /** TFC TripHammerBlock: one property, facing (4), default north (the name the paper side reads). */
    @Test
    @SuppressWarnings("unchecked")
    void oneBlockWithFacing() {
        assertThat(states).isEqualTo(4);
        Map<String, Object> props = (Map<String, Object>) states().get("properties");
        assertThat(props).containsOnlyKeys("facing");
        assertThat(((Map<String, Object>) props.get("facing")).get("values")).isEqualTo(List.of("north", "east", "south", "west"));
        Map<String, Object> variants = (Map<String, Object>) states().get("variants");
        assertThat(variants).hasSize(4);
        assertThat(((Map<String, Object>) variants.get("facing=east")).get("appearance")).isEqualTo("east");
    }

    /** TFCBlocks: strength(3f), SoundType.METAL, the pickaxe; the device behaviour. */
    @Test
    @SuppressWarnings("unchecked")
    void settingsAndBehaviour() {
        Map<String, Object> s = (Map<String, Object>) block().get("settings");
        assertThat(s.get("hardness")).isEqualTo(3.0);
        assertThat(s.get("resistance")).isEqualTo(3.0);
        assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/pickaxe"));
        assertThat(((Map<String, Object>) s.get("sounds")).toString()).contains("minecraft:block.metal.break");
        assertThat(((Map<String, Object>) block().get("behavior")).get("type")).isEqualTo("tfp:device");
    }

    /** The barrier carrier draws TFC's model turned as TFC's blockstate (north 0, east 90, south 180, west 270). */
    @Test
    @SuppressWarnings("unchecked")
    void carrierDrawsTheModelTurnedByTheFacing() {
        Map<String, Object> appearances = (Map<String, Object>) states().get("appearances");
        assertThat(appearances).hasSize(4);
        int[] y = {0, 90, 180, 270};
        String[] facings = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            Map<String, Object> a = (Map<String, Object>) appearances.get(facings[i]);
            assertThat(a.get("state")).isEqualTo("minecraft:barrier");
            List<Map<String, Object>> r = (List<Map<String, Object>>) a.get("entity_renderer");
            assertThat(r).hasSize(1);
            assertThat(r.get(0).get("item")).isEqualTo("tfp:render/trip_hammer");
            assertThat(r.get(0).get("rotation")).isEqualTo(RotationBlocks.rotation(0, y[i]));
        }
        assertThat(out.contains(TripHammerBlock.RP + "models/block/trip_hammer/trip_hammer.json")).isTrue();
        assertThat(out.contains(TripHammerBlock.RP + "textures/block/metal/smooth/steel.png")).isTrue();
    }

    /** TFC's loot: the trip hammer itself under survives_explosion, no state conditions. */
    @Test
    @SuppressWarnings("unchecked")
    void lootSelf() {
        List<Map<String, Object>> pools = (List<Map<String, Object>>) ((Map<String, Object>) block().get("loot")).get("pools");
        assertThat(pools).hasSize(1);
        assertThat(pools.get(0).get("conditions").toString()).contains("type=survives_explosion");
        List<Map<String, Object>> entries = (List<Map<String, Object>>) pools.get(0).get("entries");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("item")).isEqualTo("tfp:trip_hammer");
        assertThat(entries.get(0)).doesNotContainKey("conditions");
    }

    /** The block item, TFC's item model (parent the block model) and the English name. */
    @Test
    @SuppressWarnings("unchecked")
    void itemAndLang() {
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items).containsKeys("tfp:trip_hammer", "tfp:render/trip_hammer");
        JsonObject item = Json.parse(new String(out.get(TripHammerBlock.RP + "models/item/trip_hammer.json"),
            StandardCharsets.UTF_8), "item").getAsJsonObject();
        assertThat(item.get("parent").getAsString()).isEqualTo("tfp:block/trip_hammer/trip_hammer");
        assertThat(new String(out.get(TripHammerBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("\"block.tfp.trip_hammer\": \"Trip Hammer\"");
    }
}
