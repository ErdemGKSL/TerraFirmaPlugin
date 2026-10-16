/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.JsonDataException;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.recipe.RecipeSchemas;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataLoaderTest {
    @TempDir
    Path tmp;

    static final KnownIds IDS = KnownIds.of(
            Map.of("item", Set.of("tfp:clay_ball", "tfp:food/banana", "minecraft:stick"),
                    "fluid", Set.of("minecraft:water"),
                    "food_trait", Set.of("tfp:pickled")),
            Map.of("item", Set.of("minecraft:logs")));

    Path write(Path root, String path, String json) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
        return file;
    }

    RegistrySnapshot load(Path... roots) {
        return DataLoader.standard(IDS).load(List.of(roots));
    }

    static final String QUERN = "{\"type\": \"tfc:quern\", \"ingredient\": {\"item\": \"tfp:food/banana\"}, \"result\": {\"id\": \"tfp:clay_ball\"}}";

    @Test
    void loadsRecipesDefinitionsAndTags() throws IOException {
        Path root = tmp.resolve("data");
        write(root, "tfp/recipe/quern/banana.json", QUERN);
        write(root, "tfp/tags/item/fruits.json", "{\"values\": [\"tfp:food/banana\", \"#minecraft:logs\"]}");
        write(root, "tfp/tfp/item_heat/clay.json", "{\"ingredient\": {\"item\": \"tfp:clay_ball\"}, \"heat_capacity\": 1.5}");
        write(root, "tfp/loot_table/blocks/x.json", "this folder belongs to the datapack and is not parsed");
        write(root, "tfp/tags/damage_type/x.json", "not parsed either");

        RegistrySnapshot s = load(root);
        assertThat(s.get(DefinitionKinds.RECIPE).ids()).containsExactly("tfp:quern/banana");
        SchemaRecord heat = s.get(DefinitionKinds.key("item_heat")).getOrThrow("tfp:clay");
        assertThat(heat.require("heat_capacity", Float.class)).isEqualTo(1.5f);
        assertThat(s.tags().contains(TagType.ITEM, "tfp:fruits", "tfp:food/banana")).isTrue();
        assertThat(s.get(RegistryKey.of("never_registered", SchemaRecord.class)).size()).isZero();
        assertThatThrownBy(() -> s.get(RegistryKey.of("recipe", String.class))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownTypesKeysKindsAndFoldersFail() throws IOException {
        Path root = tmp.resolve("data");
        write(root, "tfp/recipe/a.json", "{\"type\": \"tfc:teleport\"}");
        write(root, "tfp/recipe/b.json", "{\"type\": \"tfc:quern\", \"ingredient\": {\"type\": \"tfc:shiny\"}, \"result\": {\"id\": \"tfp:clay_ball\"}}");
        write(root, "tfp/recipe/c.json", "{\"type\": \"tfc:quern\", \"ingredient\": {\"item\": \"tfp:clay_ball\"}, \"result\": {\"id\": \"tfp:clay_ball\"}, \"bonus\": 1}");
        write(root, "tfp/tfp/mystery/x.json", "{}");
        write(root, "tfp/spells/x.json", "{}");
        write(root, "tfp/recipe/notes.txt", "hi");
        write(root, "tfp/recipe/bad.json", "{\"type\": \"tfc:quern\",}");

        assertThatThrownBy(() -> load(root))
                .isInstanceOfSatisfying(DataLoadException.class, e -> assertThat(e.errors()).containsExactlyInAnyOrder(
                        "data/tfp/recipe/a.json $.type: unknown recipe type 'tfc:teleport' (known: " + RecipeSchemas.TYPES.types().stream().sorted().toList() + ")",
                        "data/tfp/recipe/b.json $.ingredient.type: unknown ingredient type 'tfc:shiny' (known: [neoforge:compound, neoforge:difference, neoforge:intersection, tfc:and, tfc:fluid_content, tfc:has_trait, tfc:heat, tfc:lacks_trait, tfc:not_rotten, tfc:rotten])",
                        "data/tfp/recipe/c.json $: unknown key(s) [bonus]",
                        "data/tfp/tfp/mystery/x.json: unknown data kind 'mystery'",
                        "data/tfp/spells/x.json: unknown data folder 'spells'",
                        "data/tfp/recipe/notes.txt: not a .json file",
                        "data/tfp/recipe/bad.json: malformed JSON: " + malformedMessage("{\"type\": \"tfc:quern\",}")));
    }

    @Test
    void unresolvedIdsAndTagsFail() throws IOException {
        Path root = tmp.resolve("data");
        write(root, "tfp/recipe/a.json", "{\"type\": \"tfc:quern\", \"ingredient\": {\"tag\": \"tfp:nope\"}, \"result\": {\"id\": \"tfp:ghost\"}}");
        write(root, "tfp/recipe/b.json", """
                {"type": "tfc:barrel_sealed", "duration": 10, "input_fluid": {"fluid": "tfp:lava_soup"},
                 "input_item": {"type": "tfc:has_trait", "trait": "tfp:cursed"}}""");
        write(root, "tfp/recipe/c.json", """
                {"type": "tfc:knapping", "knapping_type": "tfp:clay", "pattern": ["#"], "result": {"id": "minecraft:stick"}}""");
        write(root, "tfp/tags/item/t.json", "{\"values\": [\"tfp:unknown_item\"]}");

        assertThatThrownBy(() -> load(root))
                .isInstanceOfSatisfying(DataLoadException.class, e -> assertThat(e.errors()).containsExactlyInAnyOrder(
                        "unknown item id 'tfp:unknown_item' in tag #tfp:t [data/tfp/tags/item/t.json]",
                        "data/tfp/recipe/a.json $.ingredient.tag: unknown item tag '#tfp:nope'",
                        "data/tfp/recipe/a.json $.result.id: unknown item 'tfp:ghost'",
                        "data/tfp/recipe/b.json $.input_fluid.fluid: unknown fluid 'tfp:lava_soup'",
                        "data/tfp/recipe/b.json $.input_item.trait: unknown food_trait 'tfp:cursed'",
                        "data/tfp/recipe/c.json $.knapping_type: unknown knapping_type 'tfp:clay'"));
    }

    @Test
    void definitionReferencesResolveAgainstLoadedRegistries() throws IOException {
        Path root = tmp.resolve("data");
        write(root, "tfp/recipe/c.json", """
                {"type": "tfc:knapping", "knapping_type": "tfp:clay", "pattern": ["##", "# "], "result": {"id": "minecraft:stick"}}""");
        write(root, "tfp/tfp/knapping_type/clay.json", """
                {"click_sound": "tfp:item.knapping.clay", "consume_after_complete": true, "has_off_texture": true,
                 "icon": {"id": "tfp:clay_ball"}, "input": {"count": 5, "item": "tfp:clay_ball"}, "spawns_particles": false}""");
        RegistrySnapshot s = load(root);
        assertThat(s.get(DefinitionKinds.key(RecipeSchemas.KNAPPING_TYPE)).contains("tfp:clay")).isTrue();
    }

    @Test
    void duplicatesAcrossRootsFailButTagsMerge() throws IOException {
        Path a = tmp.resolve("a");
        Path b = tmp.resolve("b");
        write(a, "tfp/recipe/x.json", QUERN);
        write(b, "tfp/recipe/x.json", QUERN);
        write(a, "tfp/tags/item/fruits.json", "{\"values\": [\"tfp:food/banana\"]}");
        write(b, "tfp/tags/item/fruits.json", "{\"values\": [\"minecraft:stick\"]}");
        assertThatThrownBy(() -> load(a, b))
                .isInstanceOfSatisfying(DataLoadException.class, e -> assertThat(e.errors()).containsExactly(
                        "duplicate recipe 'tfp:x' in a/tfp/recipe/x.json and b/tfp/recipe/x.json"));
        Files.delete(b.resolve("tfp/recipe/x.json"));
        assertThat(load(a, b).tags().members(TagType.ITEM, "tfp:fruits")).containsExactly("tfp:food/banana", "minecraft:stick");
    }

    @Test
    void falseConditionsSkipFilesAndDisableRecipes() throws IOException {
        Path root = tmp.resolve("data");
        // TFC's data/minecraft/recipe/anvil.json: removes the vanilla recipe.
        write(root, "minecraft/recipe/anvil.json", "{\"neoforge:conditions\": [{\"type\": \"neoforge:false\"}]}");
        write(root, "tfp/recipe/kept.json", "{\"neoforge:conditions\": [{\"type\": \"neoforge:not\", \"value\": {\"type\": \"neoforge:false\"}}], "
                + QUERN.substring(1));
        RegistrySnapshot s = load(root);
        assertThat(s.disabledRecipes()).containsExactly("minecraft:anvil");
        assertThat(s.get(DefinitionKinds.RECIPE).ids()).containsExactly("tfp:kept");
        write(root, "tfp/recipe/mod.json", "{\"neoforge:conditions\": [{\"type\": \"neoforge:mod_loaded\", \"modid\": \"x\"}]}");
        assertThatThrownBy(() -> load(root)).hasMessageContaining("unsupported load condition type 'neoforge:mod_loaded'");
    }

    static final String ID_MAP = """
            {"comment": "generated", "namespaces": {"tfc": "tfp"},
             "blocks": {"tfc:rock/raw/granite": "tfp:rock/raw/granite"},
             "items": {"tfc:clay_ball": "tfp:clay_ball"},
             "fluids": {"tfc:vinegar": "tfp:vinegar"},
             "entity_types": {"tfc:cow": "tfp:cow"},
             "food_traits": {"tfc:salted": "tfp:salted"},
             "external_ids": {"item": ["minecraft:stick"], "fluid": ["minecraft:milk"]}}""";
    static final String EXTERNAL_TAGS = """
            {"comment": "generated", "namespaces": {"c": "NeoForge", "minecraft": "vanilla"},
             "tags": {"item": ["c:feathers", "minecraft:logs"]}}""";

    static KnownIds idMap(String idMap, String externalTags) {
        return KnownIds.fromIdMap(StrictJson.parse(idMap, "id_map.json"), DecodeContext.root("id_map.json"),
                StrictJson.parse(externalTags, "external_tags.json"), DecodeContext.root("external_tags.json"));
    }

    @Test
    void idMapChecksEveryRegistryAndExternalTagsExactly() throws IOException {
        // Shape of tools/datagen's data/id_map.json and data/external_tags.json (M1-DGN-02).
        KnownIds ids = idMap(ID_MAP, EXTERNAL_TAGS);
        assertThat(ids.hasId("item", "tfp:clay_ball")).isTrue();
        assertThat(ids.hasId("item", "tfp:ghost")).isFalse();
        assertThat(ids.hasId(TagType.BLOCK, "tfp:rock/raw/granite")).isTrue();
        assertThat(ids.hasId("fluid", "tfp:vinegar")).isTrue();
        assertThat(ids.hasId("fluid", "tfp:ghost")).isFalse();
        assertThat(ids.hasId("entity_type", "tfp:cow")).isTrue();
        assertThat(ids.hasId("entity_type", "tfp:vinegar")).isFalse();
        assertThat(ids.hasId("food_trait", "tfp:salted")).isTrue();
        assertThat(ids.hasId("food_trait", "tfp:ghost")).isFalse();
        assertThat(ids.hasId("unknown_registry", "tfp:cow")).isFalse();
        assertThat(ids.hasId("item", "minecraft:stick")).as("listed vanilla id").isTrue();
        assertThat(ids.hasId("item", "minecraft:diamond")).as("unlisted vanilla id").isFalse();
        assertThat(ids.hasId("fluid", "minecraft:milk")).as("NeoForge-provided fluid").isTrue();
        assertThat(ids.hasExternalTag(TagType.ITEM, "c:feathers")).isTrue();
        assertThat(ids.hasExternalTag(TagType.ITEM, "minecraft:logs")).isTrue();
        assertThat(ids.hasExternalTag(TagType.ITEM, "c:ingots")).as("unlisted c: tag").isFalse();
        assertThat(ids.hasExternalTag(TagType.ITEM, "minecraft:planks")).as("unlisted vanilla tag").isFalse();
        assertThat(ids.hasExternalTag(TagType.BLOCK, "c:feathers")).as("listed for items only").isFalse();
        assertThatThrownBy(() -> idMap(ID_MAP.replace("\"food_traits\"", "\"traits\""), EXTERNAL_TAGS))
                .hasMessageContaining("traits");
        assertThatThrownBy(() -> idMap(ID_MAP, EXTERNAL_TAGS.replace("c:feathers", "tfp:feathers")))
                .hasMessageContaining("not in a declared external namespace");
        assertThatThrownBy(() -> idMap(ID_MAP, EXTERNAL_TAGS.replace("c:feathers", "forge:feathers")))
                .hasMessageContaining("not in a declared external namespace");
        assertThatThrownBy(() -> idMap(ID_MAP.replace("minecraft:stick", "tfp:stick"), EXTERNAL_TAGS))
                .hasMessageContaining("is in one of our namespaces");

        Path root = tmp.resolve("data");
        write(root, KnownIds.ID_MAP_FILE, "{}");
        write(root, KnownIds.EXTERNAL_TAGS_FILE, "{}");
        write(root, "tfp/recipe/a.json", "{\"type\": \"tfc:quern\", \"ingredient\": {\"item\": \"tfp:clay_ball\"}, \"result\": {\"id\": \"tfp:ghost\"}}");
        assertThatThrownBy(() -> DataLoader.standard(ids).load(List.of(root)))
                .isInstanceOfSatisfying(DataLoadException.class, e -> assertThat(e.errors())
                        .containsExactly("data/tfp/recipe/a.json $.result.id: unknown item 'tfp:ghost'"));
    }

    static String malformedMessage(String json) {
        try {
            StrictJson.parse(json, "x");
            throw new AssertionError("expected malformed JSON");
        } catch (JsonDataException e) {
            return e.problem().substring("malformed JSON: ".length());
        }
    }
}
