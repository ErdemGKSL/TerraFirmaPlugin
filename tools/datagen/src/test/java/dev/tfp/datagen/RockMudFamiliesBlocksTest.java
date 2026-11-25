/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.hitbox.HitboxDemands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-DECO-06: the rock stairs, slabs, buttons, pressure plates and spikes as CraftEngine blocks, on the real checkout. */
class RockMudFamiliesBlocksTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    private static final int ROCKS = 21;
    private static final int DECOR_KINDS = 7;

    private static TfcCheckout real() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }

    @Test
    void demandRocksAreTfcsRocksInEnumOrder() {
        TfcCheckout tfc = real();
        assertThat(RockFamilies.families(tfc).stream().map(RockFamilies.Family::rock).toList())
            .containsExactlyElementsOf(HitboxDemands.LOOSE_ROCKS);
        assertThat(HitboxDemands.ROCK_DECOR_KINDS).containsExactlyInAnyOrderElementsOf(
            RockFamilies.kinds(tfc.readString(RockFamilies.SOURCE)).stream()
                .filter(k -> k.variants()).map(RockFamilies.Kind::kind).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void realCheckoutWritesStairsSlabsButtonsPlatesAndSpikesAsCraftEngineBlocks() {
        TfcCheckout tfc = real();
        List<RockFamilies.Family> families = RockFamilies.families(tfc);
        OutputTree out = new OutputTree();
        // per rock: 7 kinds x (stairs of 80 states + slab of 6), a button of 24, a plate of 2, a spike of 6
        int perRock = DECOR_KINDS * (80 + 6) + 24 + 2 + 6;
        assertThat(RockFamilies.writeBlocks(tfc, out, families)).isEqualTo(ROCKS * perRock);
        int stairs = 0;
        int slabs = 0;
        int buttons = 0;
        int plates = 0;
        int spikes = 0;
        for (RockFamilies.Family f : families) {
            String file = RockFamilies.CONFIG_DIR + f.rock() + ".yml";
            Map<String, Object> root = (Map<String, Object>) CeYamlCheck.parse(
                new String(out.get(file), StandardCharsets.UTF_8), file);
            Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
            Map<String, Object> items = (Map<String, Object>) root.get("items");
            List<RockFamilies.Block> written = f.blocks().stream().filter(RockFamilies::written).toList();
            assertThat(blocks.keySet()).as(f.rock()).containsExactlyElementsOf(written.stream().map(b -> "tfp:" + b.path()).toList());
            for (RockFamilies.Block b : written) {
                String id = "tfp:" + b.path();
                String kind = RockFamilies.shapeKind(b);
                assertThat(b.shape()).as(id).isNotEqualTo("wall");
                assertThat(items).as(id).containsKey(id);
                Map<String, Object> block = (Map<String, Object>) blocks.get(id);
                Map<String, Object> settings = (Map<String, Object>) block.get("settings");
                assertThat((List<String>) settings.get("tags")).as(id).contains("minecraft:mineable/pickaxe");
                Map<String, Object> states = (Map<String, Object>) block.get("states");
                Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
                assertThat(apps.keySet()).as(id).isEqualTo(((Map<String, Object>) states.get("variants")).keySet());
                assertThat(apps).as(id).hasSize(RockFamilies.STATES.get(kind));
                String twin = HitboxDemands.twin(id);
                switch (kind) {
                    case "stairs" -> {
                        stairs++;
                        assertThat(settings.get("hardness")).isEqualTo(1.5);
                        assertThat(twin).isEqualTo("minecraft:stone_brick_stairs");
                    }
                    case "slab" -> {
                        slabs++;
                        assertThat(settings.get("hardness")).isEqualTo(1.5);
                        assertThat(twin).isEqualTo("minecraft:stone_brick_slab");
                    }
                    case "button" -> {
                        buttons++;
                        assertThat(settings.get("hardness")).isEqualTo(0.5);
                        assertThat(settings).doesNotContainKey("correct_tools"); // TFC's button needs no tool
                        assertThat(twin).isEqualTo("minecraft:jungle_button");
                    }
                    case "pressure_plate" -> {
                        plates++;
                        assertThat(settings.get("hardness")).isEqualTo(0.5);
                        assertThat(twin).isEqualTo("minecraft:oak_pressure_plate");
                        assertThat(block.get("behavior").toString()).contains("sensitivity=mobs");
                    }
                    default -> {
                        spikes++;
                        assertThat(twin).isNull();
                        assertThat(settings.get("resistance")).isEqualTo(10.0);
                        assertThat(block).doesNotContainKey("behavior");
                        assertThat(((Map<String, Object>) block.get("loot")).get("template")).isEqualTo("tfp:loot_table/item_count");
                        assertThat(((Map<String, Object>) block.get("loot")).toString()).contains("tfp:rock/loose/" + f.rock());
                    }
                }
                for (Map.Entry<String, Object> a : apps.entrySet()) {
                    Map<String, Object> app = (Map<String, Object>) a.getValue();
                    if (a.getKey().startsWith("type=double")) {
                        assertThat(app.get("state")).isEqualTo("minecraft:barrier");
                        continue;
                    }
                    if (twin != null) {
                        assertThat(app.get("state")).as(id + "#" + a.getKey()).isEqualTo(twin + "[" + a.getKey() + "]");
                    }
                    assertThat(app.get("transparent")).isEqualTo(true);
                    assertThat((List<Object>) app.get("entity_renderer")).hasSize(1);
                }
            }
        }
        assertThat(stairs).isEqualTo(ROCKS * DECOR_KINDS);
        assertThat(slabs).isEqualTo(ROCKS * DECOR_KINDS);
        assertThat(buttons).isEqualTo(ROCKS);
        assertThat(plates).isEqualTo(ROCKS);
        assertThat(spikes).isEqualTo(ROCKS);
        String granite = new String(out.get(RockFamilies.CONFIG_DIR + "granite.yml"), StandardCharsets.UTF_8);
        assertThat(granite).contains("tfp:rock/cobble/granite_stairs:", "tfp:rock/mossy_bricks/granite_slab:",
            "tfp:rock/spike/granite:", "tfp:render/rock/spike/granite_tip", "fluid_state: water");
        assertThat(granite).doesNotContain("granite_wall", "rock/aqueduct", "rope_anchor");
        String lang = new String(out.get(RockFamilies.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.rock.spike.granite\": \"Granite Spike\"",
            "\"block.tfp.rock.cobble.granite_stairs\": \"Granite Cobblestone Stairs\"");
        assertThat(lang.lines().filter(l -> l.contains("\"block.tfp.")).count()).isEqualTo(ROCKS * (DECOR_KINDS * 2 + 3));
    }

    @Test
    void everyRockHasThreeSpikePartsOnTfcsOutlines() {
        assertThat(HitboxDemands.SPIKE_PARTS).containsExactly("base", "middle", "tip");
        assertThat(HitboxDemands.rockSpikeStates()).hasSize(6);
        assertThat(HitboxDemands.twin("tfp:rock/spike/granite")).isNull();
        assertThat(HitboxDemands.twin("tfp:rock/cobble/granite_wall")).isNull();
        assertThat(HitboxDemands.twin("tfp:rock/cobble/granite")).isNull();
    }
}
