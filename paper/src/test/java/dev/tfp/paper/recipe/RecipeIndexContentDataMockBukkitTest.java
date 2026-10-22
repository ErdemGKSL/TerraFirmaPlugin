/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.TagSnapshot;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-06 on the real datagen output: every recipe type resolves through the index with TFC's counts, and indexed
 * lookups give the same first recipe as a linear scan over every item id the server and our data know.
 */
class RecipeIndexContentDataMockBukkitTest extends MockServerTest {
    /**
     * Recipes per type in TFC's generated data at c3b4d9536 whose load conditions hold (exported from
     * {@code /opt/tfc/src/generated/resources/data} with a scratch script: the {@code type} of every recipe file, files
     * with a false {@code neoforge:conditions} left out, as TFC's loader does).
     */
    static final Map<String, Integer> TFC_COUNTS = new TreeMap<>(Map.ofEntries(
            Map.entry("minecraft:crafting_shaped", 1734),
            Map.entry("minecraft:crafting_shapeless", 587),
            Map.entry("tfc:advanced_shaped_crafting", 372),
            Map.entry("tfc:advanced_shapeless_crafting", 283),
            Map.entry("tfc:alloy", 9),
            Map.entry("tfc:anvil", 281),
            Map.entry("tfc:barrel_instant", 20),
            Map.entry("tfc:barrel_instant_fluid", 2),
            Map.entry("tfc:barrel_sealed", 309),
            Map.entry("tfc:blast_furnace", 1),
            Map.entry("tfc:bloomery", 1),
            Map.entry("tfc:casting", 111),
            Map.entry("tfc:casting_crafting", 1),
            Map.entry("tfc:chisel", 513),
            Map.entry("tfc:collapse", 525),
            Map.entry("tfc:food_combining", 1),
            Map.entry("tfc:glassworking", 60),
            Map.entry("tfc:heating", 884),
            Map.entry("tfc:knapping", 101),
            Map.entry("tfc:landslide", 21),
            Map.entry("tfc:loom", 5),
            Map.entry("tfc:pot", 145),
            Map.entry("tfc:pot_jam", 66),
            Map.entry("tfc:pot_soup", 3),
            Map.entry("tfc:quern", 89),
            Map.entry("tfc:scraping", 7),
            Map.entry("tfc:sewing", 25),
            Map.entry("tfc:welding", 87)));

    /**
     * TFC's overrides of vanilla recipes ({@code data/minecraft/recipe}, 48 shaped and 18 shapeless) that datagen does
     * not export yet (follow-up PAR-INFRA-20); every other recipe of TFC is in our data.
     */
    static final Map<String, Integer> VANILLA_OVERRIDES_NOT_EXPORTED = Map.of(
            "minecraft:crafting_shaped", 48, "minecraft:crafting_shapeless", 18);

    private record Loaded(Path root, Registries registries, Tags tags) {
    }

    private static Loaded load() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, new FakeCustomItems(), new FakeCustomBlocks(), ServerTags.BUKKIT, rules,
                () -> rules, Runnable::run, err -> {
                    throw new AssertionError(err);
                });
        return new Loaded(root, registries, tags);
    }

    private static RecipeIndex index(Loaded l) {
        return RecipeIndex.build(l.registries().get(), new TagMembers(l.tags().snapshot(), RecipeModule.vanillaIds()));
    }

    @Test
    void everyRecipeTypeResolvesThroughTheIndexWithTfcsCounts() {
        Loaded l = load();
        long start = System.nanoTime();
        RecipeIndex idx = index(l);
        long ms = (System.nanoTime() - start) / 1_000_000;
        RegistrySnapshot reg = l.registries().get();

        Map<String, Integer> bySchema = new TreeMap<>();
        for (SchemaRecord r : reg.get(DefinitionKinds.RECIPE).values()) {
            bySchema.merge(r.schema(), 1, Integer::sum);
        }
        assertThat(RecipeTypes.SPECS.keySet()).as("a key spec for every type in the data")
                .containsAll(bySchema.keySet());
        Map<String, Integer> indexed = new TreeMap<>();
        for (String type : idx.types()) {
            indexed.put(type, idx.size(type));
        }
        assertThat(indexed).as("every recipe of the data is indexed").isEqualTo(bySchema);
        assertThat(indexed.keySet()).isEqualTo(TFC_COUNTS.keySet());
        for (Map.Entry<String, Integer> e : TFC_COUNTS.entrySet()) {
            int missing = e.getValue() - indexed.get(e.getKey());
            assertThat(missing).as("TFC recipes of %s missing from the index", e.getKey())
                    .isIn(0, VANILLA_OVERRIDES_NOT_EXPORTED.getOrDefault(e.getKey(), 0));
        }
        assertThat(ms).as("index build time (ms)").isLessThan(20_000);
        System.out.println("[PAR-INFRA-06] recipe index: " + reg.get(DefinitionKinds.RECIPE).size() + " recipes, "
                + idx.types().size() + " types, built in " + ms + " ms");

        // Every keyed recipe is reachable through its key: walk each type's id list against its own keys.
        assertThat(idx.byItem(RecipeTypes.HEATING, "tfp:ore/normal_hematite")).isNotEmpty();
        assertThat(idx.byBlock(RecipeTypes.COLLAPSE, "tfp:rock/raw/granite")).isNotEmpty();
        assertThat(idx.byKey(RecipeTypes.KNAPPING, "tfp:rock")).isNotEmpty();
        assertThat(idx.byFluid(RecipeTypes.BARREL_SEALED, "minecraft:water")).isNotEmpty();
        assertThat(idx.byFluid(RecipeTypes.ALLOY, "tfp:metal/copper")).hasSize(6);
    }

    /** The count test against the TFC checkout itself, when {@code TFC_ROOT} is set (e.g. {@code /opt/tfc}). */
    @Test
    void countsMatchTheTfcCheckout() throws IOException {
        String tfcRoot = System.getenv("TFC_ROOT");
        assumeTrue(tfcRoot != null && !tfcRoot.isBlank(), "TFC_ROOT not set");
        Path data = Path.of(tfcRoot, "src/generated/resources/data");
        assumeTrue(Files.isDirectory(data), "no TFC generated data at " + data);
        RegistrySnapshot tfc = DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c"))).load(List.of(data));
        Map<String, Integer> counts = new TreeMap<>();
        for (SchemaRecord r : tfc.get(DefinitionKinds.RECIPE).values()) {
            counts.merge(r.schema(), 1, Integer::sum);
        }
        assertThat(counts).isEqualTo(TFC_COUNTS);
    }

    /** A linear scan of all recipes of the type (TFC's uncached {@code RecipeManager} query) for an item id. */
    private static Optional<RecipeEntry> linear(RecipeIndex idx, String type, String id, IngredientContext<String> ctx) {
        List<String> fields = RecipeTypes.spec(type).item();
        for (RecipeEntry r : idx.all(type)) {
            for (String f : fields) {
                if (RecipeIndex.accepts(r.recipe().values().get(f), id, ctx)) {
                    return Optional.of(r);
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void indexedLookupsEqualALinearScanForEveryKnownItem() throws IOException {
        Loaded l = load();
        RecipeIndex idx = index(l);
        TagSnapshot snap = l.tags().snapshot();
        TagLookup lookup = ItemIngredientContext.lookup(snap);
        IngredientContext<String> ctx = new IngredientContext<>() {
            @Override
            public String itemId(String stack) {
                return stack;
            }

            @Override
            public TagLookup tags() {
                return lookup;
            }

            @Override
            public FoodHooks<String> food() {
                return new FoodHooks<>() {
                    @Override
                    public boolean isRotten(String stack) {
                        return false;
                    }

                    @Override
                    public boolean hasTrait(String stack, String trait) {
                        return false;
                    }
                };
            }

            @Override
            public HeatHooks<String> heat() {
                return stack -> 1500f;
            }

            @Override
            public FluidHooks<String> fluids() {
                return stack -> Optional.of(new FluidAmount("minecraft:water", 1000));
            }
        };
        Set<String> ids = new TreeSet<>(RecipeModule.vanillaIds().get(TagType.ITEM));
        JsonObject idMap = JsonParser.parseString(Files.readString(l.root().resolve("id_map.json"))).getAsJsonObject();
        idMap.getAsJsonObject("items").entrySet().forEach(e -> ids.add(e.getValue().getAsString()));
        assertThat(ids).hasSizeGreaterThan(3000);

        List<String> types = List.of(RecipeTypes.HEATING, RecipeTypes.QUERN, RecipeTypes.ANVIL, RecipeTypes.CASTING,
                RecipeTypes.SCRAPING, RecipeTypes.LOOM, RecipeTypes.GLASSWORKING, RecipeTypes.WELDING,
                RecipeTypes.KNAPPING, RecipeTypes.BARREL_SEALED, RecipeTypes.POT, RecipeTypes.BLOOMERY,
                RecipeTypes.ADVANCED_SHAPELESS);
        List<String> mismatches = new ArrayList<>();
        int found = 0;
        for (String type : types) {
            for (String id : ids) {
                Optional<RecipeEntry> expected = linear(idx, type, id, ctx);
                Optional<RecipeEntry> actual = idx.first(type, id, ctx);
                if (!expected.map(RecipeEntry::id).equals(actual.map(RecipeEntry::id))) {
                    mismatches.add(type + " " + id + ": linear " + expected.map(RecipeEntry::id) + ", index "
                            + actual.map(RecipeEntry::id));
                }
                if (actual.isPresent()) {
                    found++;
                }
            }
        }
        assertThat(mismatches).isEmpty();
        assertThat(found).isGreaterThan(1000);
    }
}
