/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536
 */
package dev.tfp.core.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-04: the heating recipes of the datagen output decode into {@link HeatingRecipe} with TFC's temperature and
 * result. The rows are TFC's generated recipes at c3b4d9536 ({@code data/tfc/recipe/heating}), exported by a scratch
 * script outside the repo: every 44th file in sorted path order (20 rows), plus five chosen for their shape (a recipe with
 * no result, an item count of 2, a chance modifier, a bloom, a pottery firing).
 */
class HeatingRecipeGoldenTest {
    /** One TFC row; ids in our namespace ({@code tfc:} becomes {@code tfp:}). */
    record Row(String path, float temperature, String item, int count, String fluid, int amount, boolean durability,
            List<String> modifiers) {
    }

    static Row row(String path, float temperature, String item, int count, String fluid, int amount, boolean durability,
            List<String> modifiers) {
        return new Row(path, temperature, tfp(item), count, tfp(fluid), amount, durability, modifiers);
    }

    private static String tfp(String id) {
        return id == null ? null : id.replaceFirst("^tfc:", "tfp:");
    }

    static final List<Row> ROWS = List.of(
            row("anemometer", 930.0f, null, 0, "tfc:metal/brass", 200, false, List.of()),
            row("ceramic/large_vessel/orange", 1399.0f, "tfc:ceramic/large_vessel/orange", 1, null, 0, false, List.of()),
            row("food/cooked_burbot", 200.0f, "tfc:food/cooked_burbot", 1, null, 0, false, List.of("copy_food")),
            row("food/rice_bread", 200.0f, "tfc:food/rice_bread", 1, null, 0, false, List.of("copy_food")),
            row("metal/axe_head/bronze", 950.0f, null, 0, "tfc:metal/bronze", 100, false, List.of()),
            row("metal/block/nickel", 1453.0f, null, 0, "tfc:metal/nickel", 100, false, List.of()),
            row("metal/chain/wrought_iron", 1535.0f, null, 0, "tfc:metal/cast_iron", 6, false, List.of()),
            row("metal/double_ingot/tin", 230.0f, null, 0, "tfc:metal/tin", 200, false, List.of()),
            row("metal/exposed_grate/copper", 1080.0f, null, 0, "tfc:metal/copper", 100, false, List.of()),
            row("metal/hammer/copper", 1080.0f, null, 0, "tfc:metal/copper", 100, true, List.of()),
            row("metal/horse_armor/bronze", 950.0f, null, 0, "tfc:metal/bronze", 1200, true, List.of()),
            row("metal/javelin_head/black_steel", 1485.0f, null, 0, "tfc:metal/black_steel", 100, false, List.of()),
            row("metal/mace_head/black_bronze", 1070.0f, null, 0, "tfc:metal/black_bronze", 200, false, List.of()),
            row("metal/pickaxe_head/black_steel", 1485.0f, null, 0, "tfc:metal/black_steel", 100, false, List.of()),
            row("metal/saw/bismuth_bronze", 985.0f, null, 0, "tfc:metal/bismuth_bronze", 100, true, List.of()),
            row("metal/shears/wrought_iron", 1535.0f, null, 0, "tfc:metal/cast_iron", 200, true, List.of()),
            row("metal/shovel_head/red_steel", 1540.0f, null, 0, "tfc:metal/red_steel", 100, false, List.of()),
            row("metal/unfinished_boots/copper", 1080.0f, null, 0, "tfc:metal/copper", 200, false, List.of()),
            row("metal/weathered_block/bronze_slab", 950.0f, null, 0, "tfc:metal/bronze", 50, false, List.of()),
            row("ore/poor_native_silver", 961.0f, null, 0, "tfc:metal/silver", 15, false, List.of()),
            row("burn_meat", 900.0f, null, 0, null, 0, false, List.of()),
            row("torch_from_stick", 60.0f, "tfc:torch", 2, null, 0, false, List.of()),
            row("powder/kaolinite", 500.0f, "tfc:powder/kaolinite", 1, null, 0, false, List.of("chance")),
            row("raw_iron_bloom", 1535.0f, null, 0, "tfc:metal/cast_iron", 100, false, List.of()),
            row("ceramic/bowl", 1399.0f, "tfc:ceramic/bowl", 1, null, 0, false, List.of()));

    private static Registry<SchemaRecord> recipes;

    @BeforeAll
    static void load() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        recipes = snapshot.get(DefinitionKinds.RECIPE);
    }

    @Test
    void allTfcHeatingRecipesDecode() {
        long heating = recipes.asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith("tfp:heating/"))
                .peek(e -> HeatingRecipe.of(e.getValue()))
                .count();
        assertThat(heating).as("TFC ships 884 heating recipes").isEqualTo(884);
    }

    @Test
    void sampledRecipesMatchTfc() {
        assertThat(ROWS).hasSize(25);
        for (Row row : ROWS) {
            String id = "tfp:heating/" + row.path();
            HeatingRecipe r = HeatingRecipe.of(recipes.get(id).orElseThrow(() -> new AssertionError("missing " + id)));
            assertThat(r.temperature()).as(id + " temperature").isEqualTo(row.temperature());
            assertThat(r.useDurability()).as(id + " use_durability").isEqualTo(row.durability());
            assertThat(r.resultFluid()).as(id + " fluid").isEqualTo(row.fluid() == null ? Optional.empty()
                    : Optional.of(new FluidAmount(row.fluid(), row.amount())));
            assertThat(r.resultItem().stack().map(ItemStackTemplate::id)).as(id + " item")
                    .isEqualTo(Optional.ofNullable(row.item()));
            if (row.item() != null) {
                assertThat(r.resultItem().stack().orElseThrow().count()).as(id + " count").isEqualTo(row.count());
            }
            assertThat(r.resultItem().modifiers().stream().map(HeatingRecipeGoldenTest::modifierName).toList())
                    .as(id + " modifiers").isEqualTo(row.modifiers());
            assertThat(r.hasItemResult()).as(id + " has item").isEqualTo(row.item() != null);
        }
    }

    /** TFC {@code isValidTemperature}, {@code assembleFluid} durability scaling and {@code assembleStacked}'s count cap. */
    @Test
    void temperatureFluidAndStackRules() {
        HeatingRecipe hammer = HeatingRecipe.of(recipes.get("tfp:heating/metal/hammer/copper").orElseThrow());
        assertThat(hammer.isValidTemperature(1079.99f)).isFalse();
        assertThat(hammer.isValidTemperature(1080f)).as("inclusive").isTrue();
        assertThat(hammer.assembleFluid(0, 0)).contains(new FluidAmount("tfp:metal/copper", 100));
        assertThat(hammer.assembleFluid(0, 600)).contains(new FluidAmount("tfp:metal/copper", 100));
        // floor(100 x (1 - 199/600)) = floor(66.83) = 66
        assertThat(hammer.assembleFluid(199, 600)).contains(new FluidAmount("tfp:metal/copper", 66));
        assertThat(hammer.assembleFluid(600, 600)).as("a fully worn tool melts into nothing").isEmpty();

        HeatingRecipe block = HeatingRecipe.of(recipes.get("tfp:heating/metal/block/nickel").orElseThrow());
        assertThat(block.assembleFluid(5, 10)).as("no use_durability: full amount")
                .contains(new FluidAmount("tfp:metal/nickel", 100));
        HeatingRecipe burn = HeatingRecipe.of(recipes.get("tfp:heating/burn_meat").orElseThrow());
        assertThat(burn.assembleFluid(0, 0)).isEmpty();
        assertThat(burn.hasItemResult()).isFalse();

        assertThat(HeatingRecipe.stackedCount(2 * 40, 64, 64)).isEqualTo(64);
        assertThat(HeatingRecipe.stackedCount(2 * 40, 64, 16)).isEqualTo(16);
        assertThat(HeatingRecipe.stackedCount(6, 64, 64)).isEqualTo(6);
        assertThat(HeatingRecipe.stackedCount(6, 1, 64)).isEqualTo(1);
    }

    private static String modifierName(ItemStackModifier m) {
        return Map.of(ItemStackModifier.CopyFood.class, "copy_food", ItemStackModifier.Chance.class, "chance")
                .getOrDefault(m.getClass(), m.getClass().getSimpleName());
    }
}
