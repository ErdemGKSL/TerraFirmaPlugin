/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.visuals.OverlayFace;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.rendered.CrackedOverlays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-15: the ore overlay quad items, their face orientation and the composite ore block items, on the pinned TFC checkout. */
class OverlayModelTest {
    static TfcCheckout tfc;

    @BeforeAll
    static void open() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        tfc = TfcCheckout.open(checkout, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void quadModelGolden() {
        JsonObject quad = OreOverlays.quadModelJson("normal_native_gold", OreOverlays.overlayElement(tfc));
        assertThat(Json.write(quad).replaceAll("\\s+", "")).isEqualTo(("""
            {"ambientocclusion": false,
             "textures": {"overlay": "tfp:block/ore/normal_native_gold", "particle": "tfp:block/ore/normal_native_gold"},
             "elements": [{"from": [0, 0, 8], "to": [16, 16, 8],
               "rotation": {"origin": [8, 8, 8], "axis": "x", "angle": -22.5}, "shade": false,
               "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0}}}]}
            """).replaceAll("\\s+", ""));
        // The overlay item: the quad, tinted by custom_model_data colour 0 (default white) at tintindex 0.
        Map<String, Object> t = OreOverlays.overlayTemplate();
        assertThat(t).containsEntry("material", "paper");
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) t.get("model");
        assertThat(model).containsEntry("type", "minecraft:model").containsEntry("path", "${ore_overlay_model}");
        assertThat(model.get("tints")).isEqualTo(List.of(Map.of("type", "minecraft:custom_model_data", "index", 0,
            "default", 0xFFFFFF)));
    }

    /** TFC's overlay element equals the default full-face cube the fixtures fall back to (and ore_column uses the same UVs). */
    @Test
    void tfcOverlayElementIsTheFullDefaultCube() {
        JsonObject tfcElement = OreOverlays.overlayElement(tfc);
        JsonObject def = OreOverlays.defaultOverlayElement();
        assertThat(tfcElement.get("from")).isEqualTo(def.get("from"));
        assertThat(tfcElement.get("to")).isEqualTo(def.get("to"));
        for (String face : List.of("north", "east", "south", "west", "up", "down")) {
            assertThat(tfcElement.getAsJsonObject("faces").getAsJsonObject(face).get("uv"))
                .isEqualTo(def.getAsJsonObject("faces").getAsJsonObject(face).get("uv"));
        }
    }

    /**
     * Golden face orientation: the quad item (as the item_display renderer draws it: the element tilted by its rotation,
     * model / 16 - 0.5, turned 180 degrees about y, then the display transform: {@link OverlayFace#rightRotation()},
     * {@link OverlayFace#scale()}, {@link OverlayFace#rotation()}, standing in the neighbour cell with {@link OverlayFace#translation()})
     * puts every texel where vanilla draws it on that face of {@code tfc:block/ore} (default UVs of a full face, as
     * {@code BlockElement}'s per-face UV rules give them), {@value OverlayFace#OFFSET} in front of the face.
     */
    @Test
    void everyFaceLaysTheTextureLikeTheVanillaBlockFace() {
        JsonObject quad = OreOverlays.quadModelJson("amethyst", OreOverlays.overlayElement(tfc));
        JsonObject el = quad.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertThat(el.get("from").toString()).isEqualTo("[0,0,8]");
        assertThat(el.get("to").toString()).isEqualTo("[16,16,8]");
        assertThat(el.getAsJsonObject("faces").keySet()).containsExactly("north");
        JsonArray uv = el.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
        assertThat(uv.toString()).isEqualTo("[0,0,16,16]");
        int[][] texels = {{0, 0}, {16, 0}, {0, 16}, {16, 16}, {4, 11}};
        for (OverlayFace face : OverlayFace.values()) {
            float[] q = face.rotation();
            assertThat(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).as(face + " unit quaternion")
                .isCloseTo(1f, within(1e-6f));
            for (int[] t : texels) {
                double u = t[0];
                double v = t[1];
                // north face of the quad element (uv [0, 0, 16, 16]): u runs to -x, v to -y
                double[] model = tilt(new double[] {(16 - u) / 16 - 0.5, (16 - v) / 16 - 0.5, 8.0 / 16 - 0.5}, el);
                double[] turned = {-model[0], model[1], -model[2]}; // ItemDisplayRenderer: Axis.YP.rotation(PI)
                double[] untilted = OverlayFace.rotate(face.rightRotation(), turned);
                float[] sc = face.scale();
                double[] r = face.rotate(new double[] {untilted[0] * sc[0], untilted[1] * sc[1], untilted[2] * sc[2]});
                float[] tr = face.translation();
                double[] got = {r[0] + face.dx() + tr[0], r[1] + face.dy() + tr[1], r[2] + face.dz() + tr[2]};
                double[] want = vanilla(face, u, v);
                for (int i = 0; i < 3; i++) {
                    want[i] = want[i] / 16 - 0.5 + OverlayFace.OFFSET * new int[] {face.dx(), face.dy(), face.dz()}[i];
                }
                assertThat(got).as(face + " texel " + u + "," + v).containsExactly(want, within(1e-5));
            }
        }
    }

    /** The element rotation (about x through the model centre, right-handed like vanilla's FaceBakery) of a centred point. */
    static double[] tilt(double[] p, JsonObject el) {
        JsonObject rot = el.getAsJsonObject("rotation");
        assertThat(rot.get("axis").getAsString()).isEqualTo("x");
        assertThat(rot.get("origin").toString()).isEqualTo("[8,8,8]");
        double a = Math.toRadians(rot.get("angle").getAsDouble());
        return new double[] {p[0], p[1] * Math.cos(a) - p[2] * Math.sin(a), p[1] * Math.sin(a) + p[2] * Math.cos(a)};
    }

    /** Vanilla's position (model pixels) of texel {@code (u, v)} on a full face with the default UV. */
    static double[] vanilla(OverlayFace face, double u, double v) {
        return switch (face) {
            case DOWN -> new double[] {u, 0, 16 - v};
            case UP -> new double[] {u, 16, v};
            case NORTH -> new double[] {16 - u, 16 - v, 0};
            case SOUTH -> new double[] {u, 16 - v, 16};
            case WEST -> new double[] {0, 16 - v, u};
            case EAST -> new double[] {16, 16 - v, 16 - u};
        };
    }

    @Test
    void faceShadesAreVanillaFlatShading() {
        assertThat(OverlayFace.UP.shade()).isEqualTo(0xFFFFFF);
        assertThat(OverlayFace.NORTH.shade()).isEqualTo(0xCCCCCC);
        assertThat(OverlayFace.SOUTH.shade()).isEqualTo(0xCCCCCC);
        assertThat(OverlayFace.EAST.shade()).isEqualTo(0x999999);
        assertThat(OverlayFace.WEST.shade()).isEqualTo(0x999999);
        assertThat(OverlayFace.DOWN.shade()).isEqualTo(0x808080);
    }

    @Test
    void oneOverlayItemPerTfcOverlayTextureWithTenTintedCrackedVariants() {
        List<String> overlays = CrackedOverlays.tfcOverlays(tfc);
        assertThat(overlays).hasSize(53).contains("amethyst", "poor_native_gold", "normal_native_gold", "rich_native_gold",
            "diamond", "normal_malachite");
        Map<String, Object> variants = OreOverlays.crackedVariants("rich_hematite");
        assertThat(variants).hasSize(10).containsKey("tfp:ore_overlay/rich_hematite/crack_0")
            .containsKey("tfp:ore_overlay/rich_hematite/crack_9");
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) ((Map<String, Object>) variants.get(
            "tfp:ore_overlay/rich_hematite/crack_3")).get("model");
        assertThat(model).containsEntry("type", "minecraft:composite");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) model.get("models");
        assertThat(parts.get(0)).containsEntry("path", "tfp:item/ore_overlay/rich_hematite")
            .containsEntry("tints", OreOverlays.tints());
        assertThat(parts.get(1)).containsEntry("path", "tfp:item/ore_overlay_crack/3").doesNotContainKey("tints");
    }

    /** PAR-INFRA-41: the crack layer is tilted like the overlay, with a plane on each side of it (the down face mirrors). */
    @Test
    void tiltedCrackTwinGolden() {
        assertThat(OreOverlays.tiltedCrackQuad(7)).isEqualTo("tfp:item/ore_overlay_crack/7");
        assertThat(Json.write(OreOverlays.tiltedCrackQuadJson(7)).replaceAll("\\s+", "")).isEqualTo(("""
            {"ambientocclusion": false,
             "textures": {"crack": "minecraft:block/destroy_stage_7", "particle": "minecraft:block/destroy_stage_7"},
             "elements": [
               {"from": [0, 0, 7.98], "to": [16, 16, 7.98],
                "rotation": {"origin": [8, 8, 8], "axis": "x", "angle": -22.5}, "shade": false,
                "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#crack"}}},
               {"from": [0, 0, 8.02], "to": [16, 16, 8.02],
                "rotation": {"origin": [8, 8, 8], "axis": "x", "angle": -22.5}, "shade": false,
                "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#crack"}}}]}
            """).replaceAll("\\s+", ""));
    }

    @Test
    void gradedAndColumnarOreBlockItemsAreRockPlusOverlayCube() {
        OreOverlays.OreBlock gold = OreOverlays.oreBlock(tfc, "ore/rich_native_gold/granite");
        assertThat(gold).isEqualTo(new OreOverlays.OreBlock("ore/rich_native_gold/granite", "rich_native_gold", "granite", false));
        assertThat(OreOverlays.blockItemCall(gold, "block.tfp.ore.rich_native_gold.granite")).isEqualTo(Map.of(
            "template", "tfp:item/ore_block_item",
            "arguments", Map.of("item_lang_key", "block.tfp.ore.rich_native_gold.granite",
                "ore_rock_model", "tfp:block/rock/raw/granite",
                "ore_overlay_cube", "tfp:block/ore_overlay/rich_native_gold")));
        OreOverlays.OreBlock shale = OreOverlays.oreBlock(tfc, "ore/normal_malachite/shale");
        assertThat(shale.columnar()).isTrue();
        assertThat(shale.rockModel()).isEqualTo("tfp:block/rock/raw/shale"); // cube_column: the columnar end stays
        assertThat(shale.overlayItem()).isEqualTo("tfp:ore_overlay/normal_malachite");
        // The overlay cube: TFC's overlay element on all six faces, 0.01 px outside the rock cube, block display transforms.
        JsonObject cube = OreOverlays.cubeModelJson("normal_malachite", OreOverlays.overlayElement(tfc));
        assertThat(cube.get("parent").getAsString()).isEqualTo("minecraft:block/block");
        JsonObject el = cube.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertThat(el.get("from").toString()).isEqualTo("[-0.01,-0.01,-0.01]");
        assertThat(el.get("to").toString()).isEqualTo("[16.01,16.01,16.01]");
        assertThat(el.getAsJsonObject("faces").keySet()).containsExactlyInAnyOrder("north", "east", "south", "west", "up", "down");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) ((Map<String, Object>) OreOverlays.blockItemTemplate()
            .get("model")).get("models");
        assertThat(parts).extracting(p -> p.get("path")).containsExactly("${ore_rock_model}", "${ore_overlay_cube}");
    }
}
