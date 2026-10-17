/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (the colormap lookup; the colormap PNGs are
 * assets/tfc/textures/colormap/{grass,foliage,water}.png and the biomes are data/tfc/worldgen/biome/*.json at the same commit)
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.climate.SeasonalColour;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * M2-SEA-01 step 1: one datapack biome per TFC overworld biome and colour band ({@link SeasonalColour}).
 *
 * <p>Each variant copies its TFC biome's fog, sky and water-fog colours and sets the band's grass, foliage and water
 * colours, read from TFC's colormaps at the band's centre index (the lookup in TFC's {@code getClimateColor}). The
 * climate fields are the TFC biome's. The features, carvers and spawners are empty: the variants are palette ids for
 * the chunk packet remap (M2-SEA-01 step 3) and are never used to generate terrain, and the server must not reference
 * {@code tfc:} features it does not have.
 *
 * <p>Output: {@code datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/seasonal/<biome>/t<T>r<R>.json}, with the
 * biome ids of {@link SeasonalColour#variantId}.
 */
final class SeasonalBiomes {
    static final String OUT = "datapack/tfp_seasonal_biomes/";
    static final String BIOMES = TfcCheckout.MAIN + "data/tfc/worldgen/biome/";
    static final String COLORMAP = TfcCheckout.ASSETS + "textures/colormap/";
    /** Decoration steps in a biome's {@code features} list (vanilla {@code GenerationStep.Decoration}). */
    static final int FEATURE_STEPS = 11;

    private static final int COLORMAP_SIZE = 256;

    private SeasonalBiomes() {
    }

    /** Writes the variants and returns how many biomes were written. */
    static int write(TfcCheckout tfc, OutputTree out) {
        BufferedImage grass = colormap(tfc, "grass");
        BufferedImage foliage = colormap(tfc, "foliage");
        BufferedImage water = colormap(tfc, "water");

        int biomes = 0;
        for (String file : tfc.list(BIOMES)) {
            if (!file.endsWith(".json")) {
                throw new DatagenException("unexpected non-JSON biome file " + file);
            }
            String name = file.substring(BIOMES.length(), file.length() - ".json".length());
            JsonObject tfcBiome = Json.parse(tfc.readString(file), file).getAsJsonObject();
            JsonObject effects = tfcBiome.getAsJsonObject("effects");
            for (int band = 0; band < SeasonalColour.BAND_COUNT; band++) {
                int t = SeasonalColour.centreTemperatureIndex(band);
                int r = SeasonalColour.centreRainfallIndex(band);
                out.put(OUT + "data/" + SeasonalColour.NAMESPACE + "/worldgen/biome/" + SeasonalColour.variantPath(name, band) + ".json",
                    Json.write(variant(tfcBiome, effects, colourAt(grass, t, r), colourAt(foliage, t, r), colourAt(water, t, r))));
            }
            biomes++;
        }
        out.put(OUT + "pack.mcmeta", Datapacks.packMcmeta("TerraFirmaPlugin seasonal colour biome variants (generated)"));
        return biomes;
    }

    /** One variant: the TFC biome's climate and sky/fog colours, the band's grass, foliage and water colours. */
    static JsonObject variant(JsonObject tfcBiome, JsonObject tfcEffects, int grass, int foliage, int water) {
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
        // Minecraft 1.21 reads a biome's carvers as a list of carver ids (a HolderSet), e.g. ["minecraft:cave"] in
        // vanilla badlands.json; the variants have no terrain, so the empty list is the correct "no carvers" form.
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

    /** The colormap as TFC reads it: x is the temperature index, y the groundwater index (row-major, 256 x 256). */
    static BufferedImage colormap(TfcCheckout tfc, String name) {
        String path = COLORMAP + name + ".png";
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(tfc.read(path)));
            if (img == null || img.getWidth() != COLORMAP_SIZE || img.getHeight() != COLORMAP_SIZE) {
                throw new DatagenException(path + " is not a 256 x 256 colormap");
            }
            return img;
        } catch (IOException e) {
            throw new DatagenException("cannot decode " + path + ": " + e.getMessage());
        }
    }

    /** The RGB colour (no alpha) at a colormap index pair (x = temperature index, y = groundwater index). */
    static int colourAt(BufferedImage map, int temperatureIndex, int rainfallIndex) {
        return map.getRGB(temperatureIndex, rainfallIndex) & 0xFFFFFF;
    }
}
