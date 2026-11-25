/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.kiln;

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

/** PAR-POT-14: the tfp:pit_kiln entry against the real checkout, TFC's shapes and the selector's carriers. */
class PitKilnBlockTest {
    private static TfcCheckout tfc;

    @BeforeAll
    static void open() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
    }

    @Test
    void tfcsSourceStillMatches() {
        assertThat(PitKilnBlock.available(tfc)).isTrue();
        PitKilnBlock.checkSource(tfc);
        PitKilnBlock.checkBlockstate(tfc);
    }

    @Test
    void theOutlinesAreTfcsShapeByLayer() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (int stage = 0; stage < 8; stage++) {
            TfcShapes.Row r = rows.get(TfcShapes.pitKilnKey(stage));
            assertThat(r.selection()).as("stage " + stage).isEqualTo(Shape.box(0, 0, 0, 16, stage + 1, 16));
            assertThat(r.collision()).as("collision " + stage).isEqualTo(r.selection());
            assertThat(r.seeThrough()).as("noOcclusion " + stage).isTrue();
        }
        // stage 8..11: the straw slab 8 px plus 1..4 log columns of 4 x 4 px (8..12 px high); 12..15 the second row
        for (int i = 0; i < 4; i++) {
            assertThat(rows.get(TfcShapes.pitKilnKey(8 + i)).selection().volume())
                .as("stage " + (8 + i)).isEqualTo(16.0 * 16 * 8 + (i + 1) * 4.0 * 4 * 16);
            assertThat(rows.get(TfcShapes.pitKilnKey(12 + i)).selection().volume())
                .as("stage " + (12 + i)).isEqualTo(16.0 * 16 * 12 + (i + 1) * 4.0 * 4 * 16);
        }
        assertThat(rows.get(TfcShapes.pitKilnKey(15)).selection()).isEqualTo(rows.get(TfcShapes.pitKilnKey(16)).selection())
            .isEqualTo(Shape.FULL);
        assertThat(rows.get(TfcShapes.pitKilnKey(8)).selection().height()).isEqualTo(12);
    }

    @Test
    void everyStageHasADemandOnItsOutlineAndALook() {
        List<HitboxAllocation.Demand> demands = HitboxDemands.all().stream()
            .filter(d -> d.blockId().equals(PitKilnBlock.ID)).toList();
        assertThat(demands).hasSize(HitboxDemands.PIT_KILN_POOL_FROM);
        for (int stage = 0; stage < HitboxDemands.PIT_KILN_POOL_FROM; stage++) {
            final int st = stage;
            HitboxAllocation.Demand d = demands.stream().filter(x -> x.appearance().equals("stage_" + st)).findFirst().orElseThrow();
            assertThat(d.tfcKey()).isEqualTo("tfc:pit_kiln[stage=" + stage + "]");
            assertThat(d.look()).isEqualTo("tfp:block/pit_kiln/pitkiln_" + stage);
            assertThat(d.rare()).isTrue();
            assertThat(d.weight()).as("served after every other demand").isLessThanOrEqualTo(-30);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theEntryHasTfcsSettingsNoItemNoLootAndOneAppearancePerStage() {
        OutputTree out = new OutputTree();
        int states = PitKilnBlock.run(tfc, out);
        assertThat(states).isEqualTo(PitKilnBlock.LIT + 1);
        String text = new String(out.get(PitKilnBlock.OUTPUT), StandardCharsets.UTF_8);
        Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(text);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        assertThat(blocks).containsOnlyKeys(PitKilnBlock.ID);
        Map<String, Object> block = (Map<String, Object>) blocks.get(PitKilnBlock.ID);
        assertThat(block).doesNotContainKeys("loot", "template");
        assertThat(((Map<String, Object>) block.get("behavior")).get("type")).isEqualTo("tfp:device");
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        assertThat(settings.get("hardness").toString()).isEqualTo("0.6");
        assertThat(settings.get("resistance").toString()).isEqualTo("0.6");
        assertThat(settings.get("can_occlude")).isEqualTo(false);
        assertThat(settings).doesNotContainKey("tags");
        assertThat(((Map<String, Object>) settings.get("sounds")).values()).allMatch(v -> v.toString().contains("wood"));
        Map<String, Object> st = (Map<String, Object>) block.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props).containsOnlyKeys("stage");
        assertThat(props.get("stage").toString()).contains("0").contains("16");
        assertThat((Map<String, Object>) st.get("appearances")).hasSize(PitKilnBlock.LIT + 1);
        assertThat((Map<String, Object>) st.get("variants")).hasSize(PitKilnBlock.LIT + 1);
        // 17 models, the three TFC textures
        for (int stage = 0; stage <= PitKilnBlock.LIT; stage++) {
            assertThat(out.contains(PitKilnBlock.RP + "models/block/pit_kiln/pitkiln_" + stage + ".json")).as("model " + stage).isTrue();
        }
        for (String t : List.of("thatch", "log_pile_side", "molten")) {
            assertThat(out.contains(PitKilnBlock.RP + "textures/block/" + t + ".png")).as(t).isTrue();
        }
        assertThat(out.contains("craftengine/tfp/configuration/items/pit_kiln.yml")).isFalse(); // no block item
    }

    @Test
    void theHighStagesAreDeclaredLeavesPoolAppearances() {
        for (int stage = 0; stage <= PitKilnBlock.LIT; stage++) {
            String pool = HitboxDemands.charcoalPitPool(PitKilnBlock.ID, HitboxDemands.pitKilnAppearance(stage));
            assertThat(pool).as("stage " + stage).isEqualTo(stage >= HitboxDemands.PIT_KILN_POOL_FROM ? "leaves" : null);
        }
        // every stage from the pool on is see-through in TFC (noOcclusion) and at least half the outline the pool serves
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (int stage = HitboxDemands.PIT_KILN_POOL_FROM; stage <= PitKilnBlock.LIT; stage++) {
            assertThat(rows.get(TfcShapes.pitKilnKey(stage)).seeThrough()).isTrue();
            assertThat(Shape.iou(rows.get(TfcShapes.pitKilnKey(stage)).selection(), Shape.FULL)).as("stage " + stage)
                .isGreaterThanOrEqualTo(0.5);
        }
    }

    @Test
    void everyDemandedStageGetsOneCarrierThatAgreesOnEveryFamily() {
        for (int stage = 0; stage < HitboxDemands.PIT_KILN_POOL_FROM; stage++) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(PitKilnBlock.ID, HitboxDemands.pitKilnAppearance(stage));
            assertThat(a.mode()).as("stage " + stage).isNotEqualTo(HitboxAllocation.Mode.VANILLA);
            assertThat(a.state()).as("stage " + stage).isNotBlank();
        }
    }
}
