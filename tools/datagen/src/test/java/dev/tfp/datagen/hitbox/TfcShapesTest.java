/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-13: the TFC shape table is generated from the pinned sources, and the committed copy is current. */
class TfcShapesTest {

    @Test
    void theCommittedTableEqualsTheOneRegeneratedFromTheCheckout() throws Exception {
        TfcCheckout tfc = RealCheckout.open();
        String regenerated = TfcShapes.json(TfcShapes.read(tfc));
        String committed = VanillaCandidates.resource(TfcShapes.RESOURCE);
        assertThat(committed).as("hitbox/tfc-shapes.json is stale: run dev.tfp.datagen.hitbox.TfcShapes <checkout> "
            + "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json").isEqualTo(regenerated);
    }

    @Test
    void spotValuesFromTfcSources() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        // CharcoalPileBlock: getShape = SHAPE_BY_LAYER[layers], getCollisionShape = SHAPE_BY_LAYER[layers - 1].
        assertThat(rows.get("tfc:charcoal_pile[layers=3]").selection()).isEqualTo(Shape.box(0, 0, 0, 16, 6, 16));
        assertThat(rows.get("tfc:charcoal_pile[layers=3]").collision()).isEqualTo(Shape.box(0, 0, 0, 16, 4, 16));
        assertThat(rows.get("tfc:charcoal_pile[layers=1]").collision().isEmpty()).isTrue();
        // LooseRockBlock TWO, Rock.LOOSE noCollission.
        assertThat(rows.get("tfc:rock/loose[count=2]").selection()).isEqualTo(Shape.box(2, 0, 2, 14, 2, 14));
        assertThat(rows.get("tfc:rock/loose[count=2]").collision().isEmpty()).isTrue();
        // CharcoalForgeBlock: SHAPE_BY_LAYER[7] for both.
        assertThat(rows.get("tfc:charcoal_forge").selection()).isEqualTo(Shape.box(0, 0, 0, 16, 14, 16));
        assertThat(rows.get("tfc:charcoal_forge").collision()).isEqualTo(rows.get("tfc:charcoal_forge").selection());
        // FirepitBlock BASE_SHAPE and the grill / pot cores on top of it.
        assertThat(rows.get("tfc:firepit").selection().volume()).isEqualTo(16 * 16 * 2 + 12 * 12 * 4);
        assertThat(rows.get("tfc:grill").selection().height()).isEqualTo(11);
        assertThat(rows.get("tfc:pot").selection().height()).isEqualTo(14);
        // The unsealed barrel is hollowed by Shapes.join(..., ONLY_FIRST): 12x16x12 minus a 10x15x10 well.
        assertThat(rows.get("tfc:wood/barrel[axis=y,sealed=false]").selection().volume()).isEqualTo(12 * 16 * 12 - 10 * 15 * 10);
        // Ported: bellows (front + 12x12 middle + end, rotated), placed items (the union of quadrants, no collision).
        assertThat(rows.get("tfc:bellows[facing=east]").selection())
            .isEqualTo(rows.get("tfc:bellows[facing=north]").selection().rotateY(1));
        assertThat(rows.get("tfc:bellows[facing=north]").selection().volume()).isEqualTo(2 * 16 * 16 * 2 + 12 * 12 * 12);
        assertThat(rows.get("tfc:placed_item[item_0=true,item_1=true,item_2=true,item_3=true]").selection())
            .isEqualTo(Shape.box(0, 0, 0, 16, 1, 16));
        assertThat(rows.get("tfc:placed_item[item_0=true,item_1=false,item_2=false,item_3=false]").selection())
            .isEqualTo(Shape.box(8, 0, 8, 16, 1, 16));
        assertThat(rows.get("tfc:placed_item[item_0=false,item_1=false,item_2=false,item_3=false]").selection().isEmpty()).isTrue();
        // 0.25 px boxes stay exact (IngotPileBlock).
        assertThat(rows.get("tfc:ingot_pile[count=1]").selection()).isEqualTo(Shape.box(0.25, 0, 0.25, 15.75, 2, 15.75));
        // The large vessel lid: 11x1.5x11 at 9.5 plus the knob.
        assertThat(rows.get("tfc:ceramic/large_vessel[sealed=true]").selection().height()).isEqualTo(12);
        assertThat(rows.get("tfc:wood/leaves").seeThrough()).isTrue();
        assertThat(rows.get("tfc:wood/leaves").collision().isEmpty()).isTrue();
        // WattleBlock.getCollisionShape: unwoven wattle has a full outline and no collision.
        assertThat(rows.get("tfc:wattle[woven=false]").collision().isEmpty()).isTrue();
        assertThat(rows.get("tfc:wattle[woven=false]").seeThrough()).isTrue();
        assertThat(rows.get("tfc:rock/raw").selection()).isEqualTo(Shape.FULL);
    }

    @Test
    void everyStateAGeneratorEmitsHasARow() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (HitboxAllocation.Demand d : HitboxDemands.all()) {
            assertThat(rows).as(d.id()).containsKey(d.tfcKey());
        }
        // The firepit family's 20 states and the forge's 8 share one row each; the bellows has one row per facing.
        for (String f : HitboxDemands.FACINGS) {
            assertThat(rows).containsKey("tfc:bellows[facing=" + f + "]");
        }
    }

    @Test
    void aChangedTfcShapeFailsInsteadOfKeepingTheOldOne(@TempDir Path root) throws Exception {
        TfcCheckout real = RealCheckout.open();
        String file = TfcShapes.B + "CharcoalPileBlock.java";
        String changed = real.readString(file).replace("return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];",
            "return SHAPE_BY_LAYER[state.getValue(LAYERS)];");
        TfcCheckout fixture = fixture(root, file, changed);
        assertThatThrownBy(() -> TfcShapes.read(fixture)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];");
        TfcShapeExpressions x = new TfcShapeExpressions(fixture, TfcShapes.CLASS_FILES);
        // The evaluator itself: or, join, arrays, qualified references.
        assertThat(x.shape(List.of(file), "Shapes.or(box(0, 0, 0, 8, 1, 8), box(8, 0, 8, 16, 1, 16))").volume()).isEqualTo(128);
        assertThat(x.shape(List.of(file), "Shapes.join(Shapes.block(), box(0, 8, 0, 16, 16, 16), BooleanOp.ONLY_FIRST)"))
            .isEqualTo(Shape.box(0, 0, 0, 16, 8, 16));
        assertThat(x.shape(List.of(file), "SHAPE_BY_LAYER[8]")).isEqualTo(Shape.FULL);
        assertThatThrownBy(() -> x.shape(List.of(file), "Shapes.or(box(0, 0, 0, 8, 1, 8), foo(1))"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("unknown call foo");
        assertThatThrownBy(() -> x.shape(List.of(file), "NO_SUCH_SHAPE"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("no VoxelShape NO_SUCH_SHAPE");
    }

    private static TfcCheckout fixture(Path root, String file, String text) throws Exception {
        Path f = root.resolve(file);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return new FixtureCheckout(root).open();
    }

    /** A checkout without git: TfcCheckout.open needs a pinned HEAD, so the fixture writes a fake .git/HEAD. */
    private record FixtureCheckout(Path root) {
        TfcCheckout open() throws Exception {
            Files.createDirectories(root.resolve(".git"));
            Files.writeString(root.resolve(".git/HEAD"), TfcCheckout.PINNED_COMMIT + "\n");
            return TfcCheckout.open(root, TfcCheckout.PINNED_COMMIT);
        }
    }
}
