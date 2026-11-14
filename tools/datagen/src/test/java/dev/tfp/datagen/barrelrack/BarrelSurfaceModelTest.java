/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.barrelrack;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-35: the in-world fluid surface item: a tinted definition and a unit plane at model y 8 facing up, at scale 1 in
 * the fixed transform (the plugin's display scales and raises it to TFC's rectangle).
 */
class BarrelSurfaceModelTest {
    @Test
    void theDefinitionTintsTheSurfaceModelWithColourZero() {
        JsonObject model = BarrelScreenArt.surfaceDefinition().getAsJsonObject("model");
        assertThat(model.get("type").getAsString()).isEqualTo("minecraft:model");
        assertThat(model.get("model").getAsString()).isEqualTo(BarrelScreenArt.SURFACE_MODEL);
        JsonObject tint = model.getAsJsonArray("tints").get(0).getAsJsonObject();
        assertThat(tint.get("type").getAsString()).isEqualTo("minecraft:custom_model_data");
        assertThat(tint.get("index").getAsInt()).isZero();
        assertThat(BarrelScreenArt.SURFACE_ITEM_MODEL).isEqualTo("tfp:render/barrel_fluid");
    }

    @Test
    void theModelIsOneUpFaceOfTheStillSpriteAcrossTheBlockAtTheCentreHeight() {
        JsonObject m = BarrelScreenArt.surfaceModel();
        assertThat(m.getAsJsonObject("textures").get("fluid").getAsString()).isEqualTo(BarrelScreenArt.FLUID_TEXTURE);
        JsonArray elements = m.getAsJsonArray("elements");
        assertThat(elements).hasSize(1);
        JsonObject e = elements.get(0).getAsJsonObject();
        assertThat(e.get("from").toString()).isEqualTo("[0,8,0]");
        assertThat(e.get("to").toString()).isEqualTo("[16,8,16]");
        JsonObject faces = e.getAsJsonObject("faces");
        assertThat(faces.keySet()).containsExactly("up");
        assertThat(faces.getAsJsonObject("up").get("tintindex").getAsInt()).isZero();
        assertThat(m.getAsJsonObject("display").getAsJsonObject("fixed").get("scale").toString()).isEqualTo("[1,1,1]");
    }
}
