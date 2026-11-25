/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-34 on MockBukkit: the livestock check leaves the horse family to the mount module (no pairing, no birth) and
 * pairs camels only when both are unridden.
 */
class LivestockBehaviourMockBukkitTest extends MockServerTest {
    private static final long DAY = LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;
    private final long now = 400 * DAY;
    private long calendar = 5 * DAY;
    private World world;
    private LivestockBehaviour behaviour;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        AnimalService animals = new AnimalService(null, new GlobalClockHolder(), () -> null, Logger.getAnonymousLogger());
        behaviour = new LivestockBehaviour(animals, null, () -> new ClockSnapshot(1, now, calendar, 8, true));
    }

    private AnimalState state(boolean male) {
        return new AnimalState(male, 0.5f, now, 0, AnimalState.NO_OLD_TICK, now, AnimalState.NEVER, 0, false,
                AnimalState.NOT_PREGNANT);
    }

    private <T extends LivingEntity> T animal(Class<T> type, String species, double x, AnimalState state) {
        T e = world.spawn(new Location(world, x, 64, 0), type);
        AnimalPdc.write(e.getPersistentDataContainer(), new AnimalRecord(species, state, 0, "", false,
                AnimalRecord.NO_GROW_TICK));
        return e;
    }

    private void check(LivingEntity e) {
        AnimalRecord r = AnimalPdc.read(e.getPersistentDataContainer()).orElseThrow();
        behaviour.onCheck(new SpeciesBehaviour.Check(e, r, LivestockAge.ADULT, now));
    }

    private AnimalState read(LivingEntity e) {
        return AnimalPdc.read(e.getPersistentDataContainer()).orElseThrow().state();
    }

    @Test
    void aReadyHorsePairIsNotPairedByTheLivestockCheck() {
        Horse mare = animal(Horse.class, "tfc:horse", 0, state(false));
        animal(Horse.class, "tfc:horse", 2, state(true));
        mare.setTamed(true);
        check(mare);
        assertThat(read(mare).fertilized()).as("the mount module pairs horses, with its tamed and unridden gate").isFalse();
    }

    @Test
    void aRiddenOrUntamedHorsePairIsNotFertilizedByTheLivestockCheck() {
        Horse mare = animal(Horse.class, "tfc:horse", 0, state(false));
        Horse stallion = animal(Horse.class, "tfc:horse", 2, state(true));
        Player rider = server.addPlayer();
        stallion.addPassenger(rider);
        check(mare);
        assertThat(read(mare).fertilized()).isFalse();
        check(stallion);
        assertThat(read(stallion).fertilized()).isFalse();
    }

    @Test
    void aHorseAndDonkeyPairIsNotPairedAndAPregnantMareIsNotDeliveredByTheLivestockCheck() {
        Horse mare = animal(Horse.class, "tfc:horse", 0, state(false));
        animal(Donkey.class, "tfc:donkey", 2, state(true));
        check(mare);
        assertThat(read(mare).fertilized()).isFalse();
        calendar = 1000 * DAY;
        AnimalState pregnant = state(false).withFertilized(true).withPregnantDay(5);
        Horse due = animal(Horse.class, "tfc:horse", 20, pregnant);
        check(due);
        assertThat(read(due).fertilized()).as("birth is the mount module's: the mare keeps her pregnancy here").isTrue();
        assertThat(read(due)).isEqualTo(pregnant);
    }

    @Test
    void anUnriddenCamelPairIsPairedAndARiddenOneIsNot() {
        Camel female = animal(Camel.class, "tfc:dromedary_camel", 0, state(false));
        Camel male = animal(Camel.class, "tfc:dromedary_camel", 2, state(true));
        Player rider = server.addPlayer();
        male.addPassenger(rider);
        check(female);
        assertThat(read(female).fertilized()).as("a ridden camel does not mate").isFalse();
        male.removePassenger(rider);
        check(female);
        assertThat(read(female).fertilized()).isTrue();
        assertThat(read(male).fertilized()).isFalse();
    }

    @Test
    void otherLivestockStillMateOnTheLivestockCheck() {
        Cow cow = animal(Cow.class, "tfc:cow", 0, state(false));
        animal(Cow.class, "tfc:cow", 2, state(true));
        check(cow);
        assertThat(read(cow).fertilized()).isTrue();
    }
}
