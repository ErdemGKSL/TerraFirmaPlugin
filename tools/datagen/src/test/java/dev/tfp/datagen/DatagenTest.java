/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.climate.SeasonalColour;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatagenTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    private static final String RP = AssetPipeline.RP;

    @TempDir
    Path tmp;

    private Datagen.Result run() throws IOException {
        return Datagen.generate(Fixture.create(tmp.resolve("tfc"), PIN), PIN);
    }

    private static String text(SortedMap<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void refusesCheckoutAtAnotherCommit() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), "0123456789012345678901234567890123456789");
        assertThatThrownBy(() -> Datagen.generate(root, PIN))
            .isInstanceOf(DatagenException.class)
            .hasMessageContaining("0123456789012345678901234567890123456789")
            .hasMessageContaining(PIN);
    }

    @Test
    void refusesDirectoryWithoutGit() throws IOException {
        Files.createDirectories(tmp.resolve("plain"));
        assertThatThrownBy(() -> Datagen.generate(tmp.resolve("plain"), PIN))
            .isInstanceOf(DatagenException.class).hasMessageContaining("not a git checkout");
    }

    @Test
    void resolvesSymbolicHeadThroughLooseAndPackedRefs() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), "ref: refs/heads/main");
        Files.writeString(root.resolve(".git/packed-refs"), "# pack-refs with: peeled\n" + PIN + " refs/heads/main\n");
        assertThat(TfcCheckout.resolveHead(root)).isEqualTo(PIN);
        Files.createDirectories(root.resolve(".git/refs/heads"));
        Files.writeString(root.resolve(".git/refs/heads/main"), "ffffffffffffffffffffffffffffffffffffffff\n");
        assertThat(TfcCheckout.resolveHead(root)).isEqualTo("ffffffffffffffffffffffffffffffffffffffff");
    }

    @Test
    void twoRunsAreByteIdenticalOnDisk() throws IOException {
        Path checkout = Fixture.create(tmp.resolve("tfc"), PIN);
        Datagen.generate(checkout, PIN).tree().writeTo(tmp.resolve("out1"));
        Datagen.generate(checkout, PIN).tree().writeTo(tmp.resolve("out2"));
        SortedMap<String, byte[]> a = OutputTree.readOwned(tmp.resolve("out1"));
        SortedMap<String, byte[]> b = OutputTree.readOwned(tmp.resolve("out2"));
        assertThat(a.keySet()).isEqualTo(b.keySet()).contains(Manifest.FILE);
        for (String p : a.keySet()) {
            assertThat(Arrays.equals(a.get(p), b.get(p))).as(p).isTrue();
        }
    }

    @Test
    void checkModeDetectsStaleChangedAndMissingFilesAndLeavesForeignFilesAlone() throws IOException {
        Datagen.Result r = run();
        Path content = tmp.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("compat.yml"), "kept: true\n");
        r.tree().writeTo(content);
        assertThat(r.tree().diff(content)).isEmpty();
        assertThat(content.resolve("compat.yml")).exists();

        Files.writeString(content.resolve("data/stale.json"), "{}");
        Files.writeString(content.resolve("data/id_map.json"), "{}");
        Files.delete(content.resolve(Manifest.FILE));
        assertThat(r.tree().diff(content)).containsExactlyInAnyOrder(
            "stale:   data/stale.json", "changed: data/id_map.json", "missing: " + Manifest.FILE);

        r.tree().writeTo(content);
        assertThat(r.tree().diff(content)).isEmpty();
        assertThat(content.resolve("data/stale.json")).doesNotExist();
    }

    @Test
    void manifestRecordsCommitAndInputGroups() throws IOException {
        JsonObject m = JsonParser.parseString(text(run().tree().files(), Manifest.FILE)).getAsJsonObject();
        assertThat(m.get("tfc_commit").getAsString()).isEqualTo(PIN);
        assertThat(m.getAsJsonObject("inputs").keySet()).contains(
            "src/generated/resources/data/tfc/tfc", "src/generated/resources/data/tfc/recipe",
            "src/generated/resources/data/c/tags", "src/main/resources/assets/tfc/textures",
            "src/main/resources/data/tfc/loot_table");
        assertThat(m.get("outputs_sha256").getAsString()).hasSize(64);
    }

    @Test
    void seasonalBiomesAreOneVariantPerBiomeAndBandWithColormapColours() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        String base = "datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/seasonal/plains/";
        assertThat(files.keySet().stream().filter(p -> p.startsWith(base))).hasSize(SeasonalColour.BAND_COUNT);
        assertThat(text(files, "datapack/tfp_seasonal_biomes/pack.mcmeta")).contains("\"pack_format\": 61");

        // band t2r3 is centred on temperature index 160 (x) and groundwater index 224 (y) of the fixture colormap
        JsonObject v = JsonParser.parseString(text(files, base + "t2r3.json")).getAsJsonObject();
        int colour = (160 << 16) | (224 << 8) | 0x40;
        JsonObject e = v.getAsJsonObject("effects");
        assertThat(e.get("grass_color").getAsInt()).isEqualTo(colour);
        assertThat(e.get("foliage_color").getAsInt()).isEqualTo(colour);
        assertThat(e.get("water_color").getAsInt()).isEqualTo(colour);
        // the TFC biome's climate and its sky, fog and water-fog colours are kept
        assertThat(e.get("sky_color").getAsInt()).isEqualTo(8709887);
        assertThat(e.get("fog_color").getAsInt()).isEqualTo(12638463);
        assertThat(e.get("water_fog_color").getAsInt()).isEqualTo(329011);
        assertThat(v.get("temperature").getAsFloat()).isEqualTo(0.8f);
        assertThat(v.get("has_precipitation").getAsBoolean()).isTrue();
        // features and spawners are empty: the variant is a palette id, never a generator
        assertThat(v.getAsJsonArray("features")).hasSize(SeasonalBiomes.FEATURE_STEPS);
        assertThat(v.getAsJsonObject("spawners").size()).isZero();
    }

        @Test
    void exportsDataWithRenamedIdsInTfcSchemas() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        String heat = text(files, "data/tfp/tfp/item_heat/hematite.json");
        assertThat(heat).contains("\"item\": \"tfp:ore/normal_hematite\"").doesNotContain("tfc:").doesNotContain("__comment__");
        assertThat(text(files, "data/tfp/recipe/quern/hematite.json"))
            .contains("\"type\": \"tfp:quern\"", "\"tag\": \"c:ores/hematite\"", "\"id\": \"tfp:powder/hematite\"");
        assertThat(text(files, "data/tfp/tags/item/rocks.json")).contains("\"tfp:rock/raw/granite\"", "\"#tfp:other\"",
            "\"minecraft:stone\"");
        assertThat(text(files, "data/c/tags/item/ores/hematite.json")).contains("tfp:ore/normal_hematite");
        assertThat(text(files, "data/tfp/tags/worldgen/biome/is_ocean.json")).contains("tfp:ocean");
        assertThat(files.keySet()).noneMatch(p -> p.contains("loot_table/ignored"));

        JsonObject idMap = JsonParser.parseString(text(files, "data/id_map.json")).getAsJsonObject();
        assertThat(idMap.getAsJsonObject("blocks").get("tfc:rock/raw/granite").getAsString()).isEqualTo("tfp:rock/raw/granite");
        assertThat(idMap.getAsJsonObject("items").get("tfc:ore/normal_hematite").getAsString()).isEqualTo("tfp:ore/normal_hematite");
        assertThat(idMap.getAsJsonObject("fluids").keySet()).containsExactly("tfc:flowing_salt_water",
            "tfc:metal/copper", "tfc:metal/flowing_copper", "tfc:river_water", "tfc:salt_water");
        assertThat(idMap.getAsJsonObject("entity_types").keySet()).containsExactly("tfc:bluegill",
            "tfc:boat/acacia", "tfc:boat/douglas_fir", "tfc:lake_trout", "tfc:seat");
        assertThat(idMap.getAsJsonObject("food_traits").keySet()).containsExactly("tfc:salted", "tfc:vinegar");
        // ids outside our namespace that the data references (found by core's loader)
        assertThat(idMap.getAsJsonObject("external_ids").getAsJsonArray("item").toString()).isEqualTo("[\"minecraft:stone\"]");

        // c:ores/hematite is defined by the exported data, c:feathers is not (NeoForge); #tfp:other is undefined
        // but in our namespace, so it is never declared external
        JsonObject external = JsonParser.parseString(text(files, "data/external_tags.json")).getAsJsonObject();
        assertThat(external.getAsJsonObject("namespaces").keySet()).containsExactly("c", "minecraft");
        assertThat(external.getAsJsonObject("tags").toString()).isEqualTo("{\"item\":[\"c:feathers\"]}");
    }

    @Test
    void renameOnlyTouchesNamespacePrefixes() {
        assertThat(Json.renameId("tfc:a")).isEqualTo("tfp:a");
        assertThat(Json.renameId("#tfc:a")).isEqualTo("#tfp:a");
        assertThat(Json.renameId("notfc:a")).isEqualTo("notfc:a");
        assertThat(Json.renameId("minecraft:tfc")).isEqualTo("minecraft:tfc");
        assertThat(Json.renameId("tfc.tooltip")).isEqualTo("tfc.tooltip");
    }

    @Test
    void copiesFamilyAssetsWithRenamedRefsAndReferencedClosure() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        assertThat(text(files, RP + "models/block/rock/raw/granite.json")).contains("tfp:block/rock/raw/granite")
            .doesNotContain("tfc:");
        assertThat(text(files, RP + "models/item/rock/raw/granite.json")).contains("\"parent\": \"tfp:block/rock/raw/granite\"");
        // referenced from family models, outside the family folders
        assertThat(files).containsKeys(RP + "textures/item/loose_rock/granite.png", RP + "textures/block/grass_top.png",
            RP + "textures/block/rock/raw/granite.png.mcmeta");
        // blockstates are consumed, not copied; the tfc:block/ore parent is replaced by flattening
        assertThat(files.keySet()).noneMatch(p -> p.contains("/blockstates/"));
        assertThat(files).doesNotContainKey(RP + "models/block/ore.json");
    }

    @Test
    void flattensOreOverlayOntoRock() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        JsonObject model = JsonParser.parseString(text(files, RP + "models/block/ore/normal_hematite/granite.json"))
            .getAsJsonObject();
        assertThat(model.get("parent").getAsString()).isEqualTo("minecraft:block/cube_all");
        assertThat(model.getAsJsonObject("textures").get("all").getAsString()).isEqualTo("tfp:block/ore/normal_hematite/granite");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(files.get(RP + "textures/block/ore/normal_hematite/granite.png")));
        assertThat(img.getColorModel().hasAlpha()).isFalse();
        assertThat(img.getRGB(0, 0)).isEqualTo(Fixture.ORE);   // opaque overlay pixel wins
        assertThat(img.getRGB(15, 0)).isEqualTo(Fixture.ROCK); // transparent overlay pixel keeps the rock
    }

    @Test
    void alphaCompositingIsSourceOver() {
        assertThat(OreFlattener.over(0x80FF0000, 0xFF0000FF)).isEqualTo(0xFF80007F);
        assertThat(OreFlattener.over(0x00FFFFFF, 0xFF123456)).isEqualTo(0xFF123456);
        assertThat(OreFlattener.over(0xFF654321, 0xFF123456)).isEqualTo(0xFF654321);
    }

    @Test
    void emitsCraftEngineEntriesForCubeBlocksOnly() throws IOException {
        Datagen.Result r = run();
        SortedMap<String, byte[]> files = r.tree().files();
        String rock = text(files, AssetPipeline.CONFIG + "rock/granite.yml");
        assertThat(rock).contains("tfp:rock/raw/granite:", "appearances:\n        template: tfp:visual/rock/raw/granite\n",
            "template: tfp:loot_table/rock_to_loose", "loot_loose_rock: tfp:rock/loose/granite",
            "template: tfp:loot_table/gravel", "rock_hardness: 6.7", "item_lang_key: block.tfp.rock.raw.granite")
            .doesNotContain("granite_slab").doesNotContain("\t");
        assertThat(text(files, AssetPipeline.CONFIG + "ore/normal_hematite.yml"))
            .contains("loot_item: tfp:ore/normal_hematite", "state:\n      template: tfp:visual/ore/normal_hematite/granite\n");
        assertThat(text(files, AssetPipeline.CONFIG + "soil/alfisol.yml"))
            .contains("tfp:dirt/alfisol:", "tfp:clay/alfisol:", "loot_item: minecraft:clay_ball", "loot_count_max: 3");
        assertThat(text(files, AssetPipeline.CONFIG + "drops.yml"))
            .contains("tfp:ore/normal_hematite:", "item_lang_key: item.tfp.ore.normal_hematite",
                "tfp:rock/loose/granite:", "item_lang_key: block.tfp.rock.loose.granite");
        assertThat(r.report().emittedCount()).isEqualTo(7);
        JsonObject summary = JsonParser.parseString(text(files, "reports/datagen-summary.json")).getAsJsonObject();
        assertThat(summary.getAsJsonObject("ce_blocks_skipped").keySet())
            .containsExactlyInAnyOrder("grass/alfisol", "rock/cobble/granite_slab");
    }

    @Test
    void generatedYamlParsesAndOnlyCallsDefinedTemplates() throws IOException {
        CeYamlCheck.assertValid(run().tree().files());
    }

    @Test
    void yamlCheckRejectsMissingArgumentsAndUndefinedTemplates() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        String rock = AssetPipeline.CONFIG + "rock/granite.yml";
        SortedMap<String, byte[]> missingArg = new java.util.TreeMap<>(files);
        missingArg.put(rock, text(files, rock).replace("        rock_map_color: 60\n", "").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CeYamlCheck.assertValid(missingArg)).hasMessageContaining("rock_map_color");
        SortedMap<String, byte[]> unknown = new java.util.TreeMap<>(files);
        unknown.put(rock, text(files, rock).replace("tfp:visual/rock/cobble/granite", "tfp:visual/rock/cobble/granit").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CeYamlCheck.assertValid(unknown)).hasMessageContaining("undefined template");
        SortedMap<String, byte[]> badVariant = new java.util.TreeMap<>(files);
        badVariant.put(rock, text(files, rock).replace("appearance: axis_z", "appearance: axis_w").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CeYamlCheck.assertValid(badVariant)).hasMessageContaining("axis_w");
    }

    @Test
    void writesAllocationsReportsSuppressionListsAndDatapack() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        VisualContentCheck.assertComplete(files);
        assertThat(text(files, "reports/visual-allocation-level-3a-1.21.md"))
            .contains("(default for this family)", "| solid |", "Reserve target 500");
    }

    @Test
    void rawRockVisualsMatchTheRandomPillarExpansionInEveryAllocation() throws IOException {
        CeYamlCheck.assertRandomPillarVisualsMatch(run().tree().files(), "rock/raw/granite");
    }

    @Test
    void langKeepsOnlyFamilyKeysRenamed() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        JsonObject en = JsonParser.parseString(text(files, RP + "lang/en_us.json")).getAsJsonObject();
        assertThat(en.keySet()).containsExactly("block.tfp.rock.raw.granite", "item.tfp.ore.normal_hematite");
        JsonObject tr = JsonParser.parseString(text(files, RP + "lang/tr_tr.json")).getAsJsonObject();
        assertThat(tr.get("block.tfp.rock.raw.granite").getAsString()).isEqualTo("Ham Granit");
        assertThat(tr.keySet()).hasSize(1);
    }

    @Test
    void writesEmptyThirdPartyListAndPackYml() throws IOException {
        SortedMap<String, byte[]> files = run().tree().files();
        assertThat(text(files, "third-party.json")).isEqualTo("[]\n");
        assertThat(text(files, AssetPipeline.PACK + "pack.yml")).contains("namespace: tfp", "enable: true");
    }

    @Test
    void pinnedCommitMatchesTheLedger() throws IOException {
        String repo = System.getProperty("tfp.repoRoot", "../..");
        String ledger = Files.readString(Path.of(repo, "docs/porting/ledger.md"));
        Matcher m = Pattern.compile("\\b[0-9a-f]{40}\\b").matcher(ledger);
        assertThat(m.find()).isTrue();
        assertThat(TfcCheckout.PINNED_COMMIT).isEqualTo(m.group());
    }

    @Test
    void manifestAggregateIsOrderIndependent() {
        assertThat(Manifest.aggregate(Map.of("a", "1", "b", "2"))).isEqualTo(Manifest.aggregate(Map.of("b", "2", "a", "1")));
        assertThat(Manifest.inputGroup("src/main/resources/assets/tfc/textures/block/x.png"))
            .isEqualTo("src/main/resources/assets/tfc/textures");
    }
}
