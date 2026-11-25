/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onFogRender planes),
 *       src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (colormap lookup)
 */
package dev.tfp.datagen.fog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.climate.SeasonalColour;
import dev.tfp.core.climate.fog.ClimateFog;
import dev.tfp.datagen.Datapacks;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * PAR-CLIM-30: the foggy datapack biomes the seasonal remap swaps in on a foggy morning. One variant per TFC biome and
 * fog level ({@link #LEVELS}), {@code tfp:fog/<biome>/l<level>}, so 125 x 3 = 375 biomes (within
 * {@link FogBudget#room}). Each keeps its TFC biome's climate and fog, sky and water-fog colours and takes the grass,
 * foliage and water colours of one reference colour band ({@link #REFERENCE_TEMPERATURE}, {@link #REFERENCE_GROUNDWATER})
 * plus the fog distances of its level at a {@linkplain #REFERENCE_RENDER_DISTANCE reference render distance}. A foggy
 * variant never replaces a freezing band (it has no snow temperature), the remap hook skips those.
 *
 * <p>Status: the datapack is generated but <b>not registered</b> by the plugin. The environment attribute names and value
 * shape in {@link #ATTRIBUTES} are unverified (no vanilla client or server jar in the build container; the spike on
 * 1.21.11 and 26.x is pending): registering a wrong attribute could break biome loading, so the install step stays off
 * until the spike has confirmed them.
 *
 * <p>Output: {@code datapack/tfp_fog/data/tfp/worldgen/biome/fog/<biome>/l<level>.json}.
 */
public final class FogVariants {
    public static final String OUT = "datapack/tfp_fog/";
    /** Fog levels a foggy variant set expresses (the same as {@code FogForecast.LEVELS} in paper). */
    public static final int LEVELS = 3;
    /** The render distance in blocks the planes are written for (12 chunks); a client's own distance differs. */
    public static final float REFERENCE_RENDER_DISTANCE = 12 * 16;
    /** The reference colour band: a mild, moist climate. */
    public static final float REFERENCE_TEMPERATURE = 10f;
    public static final float REFERENCE_GROUNDWATER = 250f;
    /** Unverified (spike pending): the biome environment attributes that carry the fog distances. */
    public static final String[] ATTRIBUTES = {"minecraft:visual/fog_start_distance", "minecraft:visual/fog_end_distance"};

    private static final String BIOMES = TfcCheckout.MAIN + "data/tfc/worldgen/biome/";
    private static final String COLORMAP = TfcCheckout.ASSETS + "textures/colormap/";
    private static final int FEATURE_STEPS = 11;

    private FogVariants() {
    }

    /** The fog scale a level stands for: its share of the levels, capped at the client's maximum. */
    public static float levelFog(int level) {
        if (level < 1 || level > LEVELS) {
            throw new IllegalArgumentException("fog level " + level);
        }
        return Math.min(ClimateFog.MAX_CLIENT_FOG, (float) level / LEVELS);
    }

    /** The datapack path of a foggy variant, relative to {@code worldgen/biome/}. */
    public static String variantPath(String biome, int level) {
        return "fog/" + biome + "/l" + level;
    }

    /** Writes the variants and returns how many biomes were written. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        BufferedImage grass = colormap(tfc, "grass");
        BufferedImage foliage = colormap(tfc, "foliage");
        BufferedImage water = colormap(tfc, "water");
        int band = SeasonalColour.band(REFERENCE_TEMPERATURE, REFERENCE_GROUNDWATER);
        int t = SeasonalColour.centreTemperatureIndex(band);
        int r = SeasonalColour.centreRainfallIndex(band);
        int biomes = 0;
        for (String file : tfc.list(BIOMES)) {
            if (!file.endsWith(".json")) {
                continue;
            }
            String name = file.substring(BIOMES.length(), file.length() - ".json".length());
            JsonObject biome = Json.parse(tfc.readString(file), file).getAsJsonObject();
            for (int level = 1; level <= LEVELS; level++) {
                out.put(OUT + "data/" + SeasonalColour.NAMESPACE + "/worldgen/biome/" + variantPath(name, level) + ".json",
                    Json.write(variant(biome, grass.getRGB(t, r) & 0xFFFFFF, foliage.getRGB(t, r) & 0xFFFFFF,
                        water.getRGB(t, r) & 0xFFFFFF, level)));
            }
            biomes++;
        }
        out.put(OUT + "pack.mcmeta", Datapacks.packMcmeta("TerraFirmaPlugin foggy biome variants (generated, not registered until the client spike)"));
        return biomes;
    }

    /** One foggy variant of a TFC biome. */
    static JsonObject variant(JsonObject tfcBiome, int grass, int foliage, int water, int level) {
        JsonObject tfcEffects = tfcBiome.getAsJsonObject("effects");
        JsonObject b = new JsonObject();
        b.add("has_precipitation", tfcBiome.get("has_precipitation"));
        b.add("temperature", tfcBiome.get("temperature"));
        b.add("downfall", tfcBiome.get("downfall"));
        JsonObject e = new JsonObject();
        e.add("fog_color", tfcEffects.get("fog_color"));
        e.add("sky_color", tfcEffects.get("sky_color"));
        e.add("water_fog_color", tfcEffects.get("water_fog_color"));
        e.addProperty("grass_color", grass);
        e.addProperty("foliage_color", foliage);
        e.addProperty("water_color", water);
        b.add("effects", e);
        float fog = levelFog(level);
        JsonObject attributes = new JsonObject();
        attributes.addProperty(ATTRIBUTES[0], ClimateFog.nearPlane(REFERENCE_RENDER_DISTANCE, fog));
        attributes.addProperty(ATTRIBUTES[1], ClimateFog.farPlane(REFERENCE_RENDER_DISTANCE, fog));
        b.add("attributes", attributes);
        b.add("carvers", new JsonArray());
        JsonArray features = new JsonArray();
        for (int i = 0; i < FEATURE_STEPS; i++) {
            features.add(new JsonArray());
        }
        b.add("features", features);
        b.add("spawners", new JsonObject());
        b.add("spawn_costs", new JsonObject());
        return b;
    }

    private static BufferedImage colormap(TfcCheckout tfc, String name) {
        String path = COLORMAP + name + ".png";
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(tfc.read(path)));
            if (img == null || img.getWidth() != 256 || img.getHeight() != 256) {
                throw new DatagenException(path + " is not a 256 x 256 colormap");
            }
            return img;
        } catch (IOException ex) {
            throw new DatagenException("cannot decode " + path + ": " + ex.getMessage());
        }
    }
}
