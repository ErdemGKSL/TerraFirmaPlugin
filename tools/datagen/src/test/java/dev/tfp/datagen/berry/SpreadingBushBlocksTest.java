/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-15: the spreading bush, cane, cranberry and dead entries on the real checkout. */
class SpreadingBushBlocksTest {
    private static OutputTree out;
    private static int states;
    private static TfcCheckout tfc;

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
        out = new OutputTree();
        states = SpreadingBushBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(SpreadingBushBlocks.OUT + file + ".yml"), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String a, String b) {
        return (Map<String, Object>) ((Map<String, Object>) m.get(a)).get(b);
    }

    @Test
    void statesAndPropertiesAreTfcs() {
        assertThat(SpreadingBushBlocks.species(tfc)).isEqualTo(HitboxDemands.SPREADING_SPECIES);
        assertThat(states).isEqualTo(4 * (12 + 48) + 12 + 3 + 12);
        assertThat(((Map<?, ?>) block("raspberry", "tfp:spreading_bush/raspberry_cane").get("states")).get("properties"))
            .isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) block("raspberry", "tfp:spreading_bush/raspberry_cane")
            .get("states")).get("properties");
        assertThat(props.keySet()).containsExactly("lifecycle", "stage", "facing");
        assertThat(block("dead", "tfp:spreading_bush/dead_cane")).isNotNull();
        assertThat(block("cranberry", "tfp:spreading_bush/cranberry")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void canesAreTurnedPerFacingOnTheirOwnOutlines() {
        Map<String, Object> variants = sub(block("raspberry", "tfp:spreading_bush/raspberry_cane"), "states", "variants");
        String app = (String) ((Map<String, Object>) variants.get("lifecycle=fruiting,stage=1,facing=east")).get("appearance");
        Map<String, Object> a = sub(block("raspberry", "tfp:spreading_bush/raspberry_cane"), "states", "appearances");
        Map<String, Object> appearance = (Map<String, Object>) a.get(app);
        assertThat(app).startsWith("cane_east_");
        assertThat(appearance.get("state")).isEqualTo(HitboxAllocation.agreed("tfp:spreading_bush/raspberry_cane", "cane_east_*").state());
        List<Map<String, Object>> els = (List<Map<String, Object>>) appearance.get("entity_renderer");
        assertThat(els.getFirst().get("rotation")).isEqualTo("0,90,0");
        // TFC's CANE_* boxes have no collision and no carrier matches them: the canes borrow the bush row (ledger)
        assertThat(TfcShapes.load().keySet()).noneMatch(k -> k.contains("cane"));
        assertThat(TfcShapes.load().get(TfcShapes.BERRY_BUSH_KEY).selection()).isEqualTo(Shape.box(2, 0, 2, 14, 8, 14));
    }

    @Test
    void lootItemsAndNames() {
        assertThat(new String(out.get("data/tfp/loot_table/blocks/spreading_bush/raspberry.json"), StandardCharsets.UTF_8))
            .contains("tfp:spreading_bush/raspberry").doesNotContain("tfc:");
        assertThat(out.get("data/tfp/loot_table/blocks/spreading_bush/dead_cane.json")).isNotNull();
        assertThat(out.get(BerryBlocks.RP + "models/item/spreading_bush/cranberry.json")).isNotNull();
        assertThat(out.get(BerryBlocks.RP + "models/block/plant/fruiting_raspberry_bush_side_1.json")).isNotNull();
        String en = new String(out.get(BerryBlocks.LANG_RP.replace("tfp_berry", "tfp_spreading_bush") + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("item.tfp.spreading_bush.cranberry").contains("Cranberry Bush");
    }
}
