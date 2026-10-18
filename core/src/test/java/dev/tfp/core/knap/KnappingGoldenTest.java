/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/KnappingPattern.java @ c3b4d9536 (matches), file: src/main/java/net/dries007/tfc/common/recipes/KnappingRecipe.java @ c3b4d9536 (matches, get)
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the knapping rules at c3b4d9536. The MATCH, PICK and ITEM rows were written by a scratch oracle that ports TFC's
 * {@code KnappingPattern.matches} and recipe pick from the TFC source and the JSON, not from these classes. The grids are the
 * recipes' own patterns embedded in the grid (with their mirror images and offsets), every single-cell flip of those, and seeded
 * random grids.
 */
class KnappingGoldenTest {

    private static final KnappingFixture.Data DATA = KnappingFixture.load();

    @Test
    void transcribedCountsMatchTfc() {
        assertThat(DATA.types()).hasSize(5);
        assertThat(DATA.recipes()).hasSize(101);
        assertThat(DATA.tagLines()).isEqualTo(9);
        assertThat(DATA.matches()).hasSize(1828);
        assertThat(DATA.picks()).hasSize(253);
        assertThat(DATA.items()).hasSize(10);
        assertThat(DATA.matches().stream().filter(m -> !m.ids().isEmpty())).hasSize(117);
    }

    @Test
    void typesCarryTfcConsumptionFlags() {
        KnappingType clay = type("tfc:clay");
        assertThat(clay.amountToConsume()).isEqualTo(5);
        assertThat(clay.consumeAfterComplete()).isTrue();
        KnappingType rock = type("tfc:rock");
        assertThat(rock.inputCount()).isEqualTo(2);
        assertThat(rock.amountToConsume()).isEqualTo(1);
        assertThat(rock.spawnsParticles()).isTrue();
        assertThat(type("tfc:leather").hasOffTexture()).isFalse();
        assertThat(type("tfc:goat_horn").consumeAfterComplete()).isFalse();
    }

    @Test
    void matchRowsAgreeWithTfc() {
        List<String> mismatches = new ArrayList<>();
        for (KnappingFixture.MatchRow row : DATA.matches()) {
            List<String> got = KnappingRules.matching(DATA.recipes(), row.type(), KnappingPattern.grid(row.grid())).stream()
                .map(KnappingRecipe::id).toList();
            if (!got.equals(row.ids())) mismatches.add(row.grid() + " " + row.type() + " want " + row.ids() + " got " + got);
        }
        assertThat(mismatches).as("MATCH rows").isEmpty();
    }

    @Test
    void pickRowsAgreeWithTfc() {
        List<String> mismatches = new ArrayList<>();
        for (KnappingFixture.PickRow row : DATA.picks()) {
            Optional<String> got = KnappingRules.find(DATA.recipes(), row.type(), KnappingPattern.grid(row.grid()), row.item(), DATA.tags())
                .map(KnappingRecipe::id);
            if (!got.equals(Optional.ofNullable(row.id()))) mismatches.add(row.grid() + " " + row.type() + " " + row.item() + " want " + row.id() + " got " + got);
        }
        assertThat(mismatches).as("PICK rows").isEmpty();
    }

    @Test
    void itemRowsAgreeWithTfc() {
        List<String> mismatches = new ArrayList<>();
        for (KnappingFixture.ItemRow row : DATA.items()) {
            Optional<String> got = KnappingRules.typeOf(DATA.types(), row.item(), DATA.tags()).map(KnappingType::id);
            if (!got.equals(Optional.ofNullable(row.type()))) mismatches.add(row.item() + " want " + row.type() + " got " + got);
        }
        assertThat(mismatches).as("ITEM rows").isEmpty();
    }

    @Test
    void everyRecipeMatchesItsOwnPattern() {
        for (KnappingRecipe recipe : DATA.recipes()) {
            KnappingPattern grid = new KnappingPattern();
            grid.setAll(false);
            KnappingPattern p = recipe.pattern();
            for (int y = 0; y < p.getHeight(); y++) {
                for (int x = 0; x < p.getWidth(); x++) grid.set(x, y, p.get(x, y));
            }
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    if (x >= p.getWidth() || y >= p.getHeight()) grid.set(x, y, p.defaultIsOn());
                }
            }
            assertThat(KnappingRules.matching(DATA.recipes(), recipe.typeId(), grid)).as(recipe.id()).contains(recipe);
        }
    }

    @Test
    void recipeResultsCarryTfcComponents() {
        KnappingRecipe admire = DATA.recipes().stream().filter(r -> r.id().equals("admire_goat_horn_goat_horn")).findFirst().orElseThrow();
        assertThat(admire.result().item()).isEqualTo("minecraft:goat_horn");
        assertThat(admire.result().components()).containsEntry("minecraft:instrument", "minecraft:admire_goat_horn");
        KnappingRecipe chestplate = DATA.recipes().stream().filter(r -> r.id().equals("leather_chestplate")).findFirst().orElseThrow();
        assertThat(chestplate.result().components()).isEmpty();
        assertThat(chestplate.ingredient()).isNull();
    }

    private static KnappingType type(String id) {
        return DATA.types().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }
}
