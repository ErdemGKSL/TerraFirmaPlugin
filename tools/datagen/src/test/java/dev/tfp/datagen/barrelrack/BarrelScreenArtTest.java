/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.barrelrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-09 "tank glyph frame per level": what each generated tank frame draws, without the shared content store. TFC's
 * BarrelScreen.renderBg fills a solid column from window y {@code 70 - fill} down to y 70 (16 wide at x 8), tiled from the
 * top by RenderHelpers.fillAreaWithSprite, and draws the gauge (18 x 52 at 7, 19) on top.
 */
class BarrelScreenArtTest {
    private static final double EPS = 1e-9;
    private static final int BOTTOM = BarrelScreenArt.TANK_BOTTOM + BarrelScreenArt.DY_TANK;

    @Test
    void theDefinitionIsOversizedAndMapsEachLevelToItsModel() {
        JsonObject root = BarrelScreenArt.definition();
        assertThat(root.has("oversized_in_gui")).as("oversized_in_gui (1.21.6+ clips GUI items to the slot)").isTrue();
        assertThat(root.get("oversized_in_gui").getAsBoolean()).isTrue();
        JsonObject dispatch = root.getAsJsonObject("model");
        assertThat(dispatch.get("type").getAsString()).isEqualTo("minecraft:range_dispatch");
        assertThat(dispatch.get("property").getAsString()).isEqualTo("minecraft:custom_model_data");
        assertThat(dispatch.get("index").getAsInt()).isZero();
        assertThat(dispatch.getAsJsonObject("fallback").get("type").getAsString()).isEqualTo("minecraft:empty");
        JsonArray entries = dispatch.getAsJsonArray("entries");
        assertThat(entries).hasSize(BarrelScreenArt.TANK_HEIGHT);
        for (int n = 1; n <= BarrelScreenArt.TANK_HEIGHT; n++) {
            JsonObject e = entries.get(n - 1).getAsJsonObject();
            assertThat(e.get("threshold").getAsDouble()).isEqualTo(n);
            JsonObject model = e.getAsJsonObject("model");
            assertThat(model.get("type").getAsString()).isEqualTo("minecraft:model");
            assertThat(model.get("model").getAsString()).isEqualTo(BarrelScreenArt.TANK_MODEL_PREFIX + n);
            JsonObject tint = model.getAsJsonArray("tints").get(0).getAsJsonObject();
            assertThat(tint.get("type").getAsString()).isEqualTo("minecraft:custom_model_data");
            assertThat(tint.get("index").getAsInt()).isZero();
        }
    }

    @Test
    void frameNFillsASolidColumnFromTheTankBottom() {
        for (int n = 1; n <= BarrelScreenArt.TANK_HEIGHT; n++) {
            JsonObject model = BarrelScreenArt.tankModel(n);
            List<double[]> fluid = new ArrayList<>();
            for (JsonElement el : model.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                JsonObject south = e.getAsJsonObject("faces").getAsJsonObject("south");
                if (!south.get("texture").getAsString().equals("#fluid")) {
                    continue;
                }
                double[] from = nums(e.getAsJsonArray("from"));
                double[] to = nums(e.getAsJsonArray("to"));
                assertThat(from[0]).as("frame %d fluid x from", n).isCloseTo(4, within(EPS));
                assertThat(to[0]).as("frame %d fluid x to", n).isCloseTo(12, within(EPS));
                assertThat(from[0]).isCloseTo(BarrelScreenArt.unitX(BarrelScreenArt.TANK_X), within(EPS));
                assertThat(south.get("tintindex").getAsInt()).isZero();
                double[] uv = nums(south.getAsJsonArray("uv"));
                // each tile shows the sprite's top rows, as tall as the tile (TFC: getV0 .. getV(heightRatio))
                assertThat(uv[1]).isZero();
                assertThat(uv[3]).as("frame %d uv height", n)
                    .isCloseTo((to[1] - from[1]) * BarrelScreenArt.TANK_SCALE, within(EPS));
                fluid.add(new double[] {from[1], to[1]});
            }
            fluid.sort(Comparator.comparingDouble(a -> a[0]));
            assertThat(fluid).as("frame %d tiles", n).hasSize((n + 15) / 16);
            assertThat(fluid.getFirst()[0]).as("frame %d bottom", n).isCloseTo(BarrelScreenArt.unitY(BOTTOM), within(EPS));
            for (int i = 1; i < fluid.size(); i++) {
                assertThat(fluid.get(i)[0]).as("frame %d gap at tile %d", n, i).isCloseTo(fluid.get(i - 1)[1], within(EPS));
            }
            assertThat(fluid.getLast()[1]).as("frame %d top", n).isCloseTo(BarrelScreenArt.unitY(BOTTOM - n), within(EPS));
            // TFC tiles from the top of the fill: the top tile is whole whenever the fill reaches 16
            double topTile = (fluid.getLast()[1] - fluid.getLast()[0]) * BarrelScreenArt.TANK_SCALE;
            assertThat(topTile).isCloseTo(Math.min(16, n), within(EPS));
        }
    }

    @Test
    void theGaugeCoversTheWholeTankInEveryFrame() {
        for (int n : new int[] {1, 16, 25, 50}) {
            JsonObject gauge = null;
            for (JsonElement el : BarrelScreenArt.tankModel(n).getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                if (e.getAsJsonObject("faces").getAsJsonObject("south").get("texture").getAsString().equals("#gauge")) {
                    assertThat(gauge).as("one gauge").isNull();
                    gauge = e;
                }
            }
            assertThat(gauge).as("frame %d gauge", n).isNotNull();
            double[] from = nums(gauge.getAsJsonArray("from"));
            double[] to = nums(gauge.getAsJsonArray("to"));
            assertThat(from[0]).isCloseTo(3.5, within(EPS));
            assertThat(to[0]).isCloseTo(12.5, within(EPS));
            int gy = BarrelScreenArt.GAUGE_Y + BarrelScreenArt.DY_TANK;
            assertThat(to[1]).isCloseTo(BarrelScreenArt.unitY(gy), within(EPS));
            assertThat(from[1]).isCloseTo(BarrelScreenArt.unitY(gy + BarrelScreenArt.GAUGE_HEIGHT), within(EPS));
            // the gauge frames the whole tank, bottom (window y 69) to top (y 19)
            assertThat(from[1]).isLessThanOrEqualTo(BarrelScreenArt.unitY(BOTTOM));
            assertThat(to[1]).isGreaterThanOrEqualTo(BarrelScreenArt.unitY(BOTTOM - BarrelScreenArt.TANK_HEIGHT));
            assertThat(from[2]).isLessThan(8);
            assertThat(to[2]).isGreaterThan(8);
        }
    }

    private static double[] nums(JsonArray a) {
        double[] d = new double[a.size()];
        for (int i = 0; i < d.length; i++) {
            d[i] = a.get(i).getAsDouble();
        }
        return d;
    }
}
