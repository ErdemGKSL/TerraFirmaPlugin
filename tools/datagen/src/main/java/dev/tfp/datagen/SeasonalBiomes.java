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
 * <p>Output: {@code datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/seasonal/<biome>/<variant>.json}, with the
 * biome ids of {@link SeasonalColour#variantId}. PAR-CLIM-05: 28 variants per biome (7 temperature x 4 rainfall bands,
 * {@link SeasonalColour#variantName}), 3,500 in all.
 *
 * <p>PAR-WORLD-02: next to the variants, one <b>base biome</b> per TFC biome,
 * {@code datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/<biome>.json} ({@code tfp:<biome>}). The Bukkit
 * {@code BiomeProvider} returns these, so F3 and the server name the TFC biome. A base biome keeps the TFC biome's climate,
 * effects and its {@code minecraft:} spawners (the monsters; TFC's own animals and fish are ours to spawn); features and
 * carvers are empty because our generator places everything itself and the server has no {@code tfc:} features.
 *
 * <p>The colour phases (one per calendar month, {@code SeasonalBiomeRemap.PHASE_COUNT} in {@code paper}) add no
 * variants: a variant is a colour band, and a phase only chooses which band a chunk cell shows by sampling the climate at
 * its month. So the set stays one variant per biome and band for any number of phases.
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
            out.put(OUT + "data/" + SeasonalColour.NAMESPACE + "/worldgen/biome/" + name + ".json",
                Json.write(base(tfcBiome, effects)));
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

    /**
     * The base biome of a TFC biome (PAR-WORLD-02): its climate and effects, no features or carvers, and only the spawner
     * entries and spawn costs of {@code minecraft:} entities (a {@code tfc:} entity does not exist on the server).
     */
    static JsonObject base(JsonObject tfcBiome, JsonObject tfcEffects) {
        JsonObject b = variant(tfcBiome, tfcEffects, 0, 0, 0);
        b.add("effects", tfcEffects.deepCopy());
        JsonObject spawners = new JsonObject();
        JsonObject tfcSpawners = tfcBiome.getAsJsonObject("spawners");
        if (tfcSpawners != null) {
            for (var category : tfcSpawners.entrySet()) {
                JsonArray kept = new JsonArray();
                for (var entry : category.getValue().getAsJsonArray()) {
                    if (isVanilla(entry.getAsJsonObject().get("type").getAsString())) {
                        kept.add(entry.deepCopy());
                    }
                }
                if (!kept.isEmpty()) {
                    spawners.add(category.getKey(), kept);
                }
            }
        }
        b.add("spawners", spawners);
        JsonObject costs = new JsonObject();
        JsonObject tfcCosts = tfcBiome.getAsJsonObject("spawn_costs");
        if (tfcCosts != null) {
            for (var cost : tfcCosts.entrySet()) {
                if (isVanilla(cost.getKey())) {
                    costs.add(cost.getKey(), cost.getValue().deepCopy());
                }
            }
        }
        b.add("spawn_costs", costs);
        return b;
    }

    private static boolean isVanilla(String entityId) {
        return entityId.startsWith("minecraft:") || entityId.indexOf(':') < 0;
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
