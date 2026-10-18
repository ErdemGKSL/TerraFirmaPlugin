/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/KnappingContainer.java @ c3b4d9536 (the consumption rules it applies on click, reset, removal)
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnappingSessionTest {

    private static final KnappingFixture.Data DATA = KnappingFixture.load();

    private static KnappingType type(String id) {
        return DATA.types().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void nonConsumingTypeRemovesOnFirstClickOnly() {
        KnappingSession s = new KnappingSession(type("tfc:rock"), false);
        assertThat(s.click(0)).isEqualTo(1);
        assertThat(s.click(1)).isZero();
        assertThat(s.takeResult()).isZero();
        assertThat(s.close(true)).isZero();
    }

    @Test
    void creativeSkipsTheFirstClickRemoval() {
        KnappingSession s = new KnappingSession(type("tfc:leather"), true);
        assertThat(s.click(0)).isZero();
        assertThat(s.takeResult()).isZero();
    }

    @Test
    void consumingTypeRemovesOnceWhenTheResultIsTaken() {
        KnappingSession s = new KnappingSession(type("tfc:clay"), false);
        assertThat(s.click(0)).isZero();
        assertThat(s.click(7)).isZero();
        assertThat(s.takeResult()).isEqualTo(5);
        assertThat(s.takeResult()).isZero();
        assertThat(s.close(true)).isZero();
    }

    @Test
    void consumingTypeRemovesOnCloseOnlyWithAResultOnTheOutput() {
        KnappingSession none = new KnappingSession(type("tfc:clay"), false);
        none.click(0);
        assertThat(none.close(false)).isZero();
        KnappingSession some = new KnappingSession(type("tfc:clay"), false);
        some.click(0);
        assertThat(some.close(true)).isEqualTo(5);
        assertThat(some.close(true)).isZero();
    }

    @Test
    void creativeDoesNotSkipTheCompletionRemoval() {
        // TFC's consumeIngredientStackAfterComplete has no creative check
        KnappingSession s = new KnappingSession(type("tfc:clay"), true);
        assertThat(s.click(0)).isZero();
        assertThat(s.close(true)).isEqualTo(5);
    }

    @Test
    void takeResultClearsTheGridAndRequestsAReset() {
        KnappingSession s = new KnappingSession(type("tfc:clay"), false);
        s.click(3);
        assertThat(s.requiresReset()).isFalse();
        s.takeResult();
        assertThat(s.requiresReset()).isTrue();
        assertThat(KnappingFixture.cells(s.grid())).isEqualTo(".".repeat(25));
    }

    @Test
    void knappingTheLeatherChestplateGivesTheRecipe() {
        KnappingSession s = new KnappingSession(type("tfc:leather"), false);
        // leather chestplate: "#...#" then four full rows; the three cells of the top row between the sleeves are clicked
        assertThat(s.click(KnappingRules.buttonIndex(1, 0))).isEqualTo(1);
        s.click(KnappingRules.buttonIndex(2, 0));
        s.click(KnappingRules.buttonIndex(3, 0));
        Optional<KnappingRecipe> recipe = KnappingRules.find(DATA.recipes(), s.type().id(), s.grid(), "minecraft:leather", DATA.tags());
        assertThat(recipe).map(KnappingRecipe::id).contains("leather_chestplate");
        assertThat(recipe.orElseThrow().result().item()).isEqualTo("minecraft:leather_chestplate");
        // The chestplate has no ingredient, so any item of the type gives it
        assertThat(KnappingRules.find(DATA.recipes(), s.type().id(), s.grid(), "minecraft:stone", DATA.tags())).map(KnappingRecipe::id).contains("leather_chestplate");
    }
}
