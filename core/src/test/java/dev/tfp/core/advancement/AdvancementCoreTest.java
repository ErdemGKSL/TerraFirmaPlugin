/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.advancement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvancementCoreTest {
    @Test
    void parsesAndPrintsAdvancementAndCriterion() {
        CriterionRef r = CriterionRef.parse("tfp:story/find_rock#rock_knapping");
        assertThat(r.advancement()).isEqualTo("tfp:story/find_rock");
        assertThat(r.criterion()).isEqualTo("rock_knapping");
        assertThat(r).hasToString("tfp:story/find_rock#rock_knapping").isEqualTo(ItemCriteria.FIND_ROCK.criterion());
    }

    @Test
    void refusesMalformedReferences() {
        for (String bad : List.of("", "#x", "tfp:a#", "tfp:a#b#c", "noNamespace#x", "tfp:Story/x#y", "tfp:a#b c")) {
            assertThatThrownBy(() -> CriterionRef.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void looseRockEarnsFindRockOnceAndOtherItemsNothing() {
        Set<String> rockTags = Set.of("tfp:rock_knapping", "c:stones/loose");
        assertThat(ItemCriteria.DEFAULT.earned(rockTags::contains, r -> false))
            .containsExactly(new CriterionRef("tfp:story/find_rock", "rock_knapping"));
        // Already earned: nothing again (the slot-change listener asks on every change).
        assertThat(ItemCriteria.DEFAULT.earned(rockTags::contains, r -> true)).isEmpty();
        // A stick is not in #tfc:rock_knapping.
        assertThat(ItemCriteria.DEFAULT.earned(Set.of("tfp:sticks")::contains, r -> false)).isEmpty();
    }

    @Test
    void tableKeepsOrderAndIsImmutable() {
        CriterionRef a = CriterionRef.parse("tfp:story/a#x");
        CriterionRef b = CriterionRef.parse("tfp:story/b#y");
        ItemCriteria t = new ItemCriteria(List.of(new ItemCriteria.Entry(b, "tfp:t"), new ItemCriteria.Entry(a, "tfp:t")));
        assertThat(t.earned("tfp:t"::equals, r -> false)).containsExactly(b, a);
        assertThatThrownBy(() -> t.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
