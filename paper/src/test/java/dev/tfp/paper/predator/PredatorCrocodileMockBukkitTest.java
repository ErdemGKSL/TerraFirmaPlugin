/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.predator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-30: the crocodile has no aggressive swim goal (AmphibiousPredatorAi); every other predator keeps it. */
class PredatorCrocodileMockBukkitTest extends MockServerTest {
    private World world;
    private PredatorModule module;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new PredatorModule();
        module.initForTest((PredatorBody) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PredatorBody.class}, (proxy, method, args) -> null), () -> 1_000L, new Random(3L));
    }

    private Set<String> goalNames(String species) {
        Cow cow = world.spawn(new Location(world, 0, 64, 0), Cow.class);
        AnimalRecord record = new AnimalRecord(species, AnimalState.spawned(true, 0, 0), 0, "", false,
            AnimalRecord.NO_GROW_TICK);
        AnimalPdc.write(cow.getPersistentDataContainer(), record);
        Set<String> names = new HashSet<>();
        for (SpeciesBehaviour.GoalSpec g : module.behaviour().goals(cow, record)) {
            names.add(g.goal().getKey().getNamespacedKey().getKey());
        }
        return names;
    }

    @Test
    void theCrocodileHasNoSwimGoalButKeepsTheRest() {
        assertThat(goalNames("tfc:crocodile")).containsExactlyInAnyOrder(
            "predator_fight", "predator_retreat", "predator_rest", "predator_hunt");
    }

    @Test
    void everyOtherPredatorKeepsTheSwimGoal() {
        for (String id : List.of("tfc:wolf", "tfc:grizzly_bear", "tfc:lion", "tfc:hyena")) {
            assertThat(goalNames(id)).as(id).contains("predator_swim");
        }
    }
}
