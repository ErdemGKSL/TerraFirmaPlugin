/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.intox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-SURV-08: TFC's {@code alcohol.json} from the generated content gives 4000 ticks per drink of every alcohol. */
class IntoxicantsTest {
    /** TFC's {@code Alcohol} enum, renamed. */
    static final List<String> ALCOHOLS = List.of("tfp:beer", "tfp:cider", "tfp:rum", "tfp:sake", "tfp:vodka",
            "tfp:whiskey", "tfp:corn_whiskey", "tfp:rye_whiskey");

    private static Intoxicants generated() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        return Intoxicants.fromRegistry(snapshot);
    }

    @Test
    void everyTfcAlcoholIntoxicatesForFourThousandTicks() throws IOException {
        Intoxicants i = generated();
        assertThat(i.size()).as("TFC's five drinkables").isEqualTo(5);
        for (String alcohol : ALCOHOLS) {
            assertThat(i.ticksPerDrink(alcohol)).as(alcohol).isEqualTo(4000);
        }
        assertThat(i.ticksPerDrink("minecraft:water")).isZero();
        assertThat(i.ticksPerDrink("tfp:salt_water")).isZero();
        assertThat(i.ticksPerDrink("tfp:vinegar")).as("vinegar is not an alcohol").isZero();
        assertThat(i.ticksPerDrink("minecraft:lava")).isZero();
    }

    @Test
    void theFirstCoveringDefinitionWins() {
        Intoxicants i = new Intoxicants(List.of(new Intoxicants.Entry(new FluidIngredient.Single("x:a"), 0),
                new Intoxicants.Entry(new FluidIngredient.Single("x:a"), 9),
                new Intoxicants.Entry(new FluidIngredient.Single("x:b"), 7)), TagRegistry.EMPTY);
        assertThat(i.ticksPerDrink("x:a")).as("TFC Drinkable.get takes the first").isZero();
        assertThat(i.ticksPerDrink("x:b")).isEqualTo(7);
        assertThat(Intoxicants.EMPTY.ticksPerDrink("tfp:beer")).isZero();
    }
}
