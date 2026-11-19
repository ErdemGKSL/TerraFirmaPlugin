/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-21: a fake species behaviour runs its goal and its check hook through the registry, with no AnimalService edit. */
class SpeciesBehavioursTest {
    private static final AnimalRecord RECORD = new AnimalRecord("tfc:cow", AnimalState.spawned(true, 0, 0), 0, "");

    private static final class Goals implements SpeciesBehaviours.GoalSink {
        final List<String> log = new ArrayList<>();

        @Override
        public void removeAll(Mob mob) {
            log.add("removeAll");
        }

        @Override
        public void add(Mob mob, int priority, Goal<Mob> goal) {
            log.add("add " + priority + " " + goal.getKey().getNamespacedKey().getKey());
        }
    }

    private static Mob mock(Class<Mob> type) {
        return (Mob) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Goal<Mob> goal(String name) {
        return new Goal<>() {
            @Override
            public boolean shouldActivate() {
                return true;
            }

            @Override
            public GoalKey<Mob> getKey() {
                return GoalKey.of(Mob.class, new NamespacedKey("tfp", name));
            }

            @Override
            public EnumSet<GoalType> getTypes() {
                return EnumSet.of(GoalType.MOVE);
            }
        };
    }

    private static class Fake implements SpeciesBehaviour {
        final List<String> events = new ArrayList<>();
        boolean keep;

        @Override
        public Set<String> species() {
            return Set.of("tfc:cow", "tfc:sheep");
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            return List.of(new GoalSpec(1, goal("graze")), new GoalSpec(3, goal("wander")));
        }

        @Override
        public boolean keepVanillaGoals() {
            return keep;
        }

        @Override
        public void onAdd(org.bukkit.entity.Entity entity, AnimalRecord record) {
            events.add("add " + record.species());
        }

        @Override
        public void onCheck(Check check) {
            events.add("check " + check.age());
        }
    }

    @Test
    void addReplacesVanillaGoalsAndRunsTheAddHook() {
        Goals sink = new Goals();
        SpeciesBehaviours reg = new SpeciesBehaviours(Logger.getAnonymousLogger(), sink);
        Fake fake = new Fake();
        reg.register(fake);

        reg.dispatchAdd(mock(Mob.class), RECORD);

        assertThat(sink.log).containsExactly("removeAll", "add 1 graze", "add 3 wander");
        assertThat(fake.events).containsExactly("add tfc:cow");
    }

    @Test
    void keepVanillaGoalsSkipsTheRemoval() {
        Goals sink = new Goals();
        SpeciesBehaviours reg = new SpeciesBehaviours(Logger.getAnonymousLogger(), sink);
        Fake fake = new Fake();
        fake.keep = true;
        reg.register(fake);

        reg.dispatchAdd(mock(Mob.class), RECORD);

        assertThat(sink.log).containsExactly("add 1 graze", "add 3 wander");
    }

    @Test
    void checkHookReceivesTheStageAndClock() {
        SpeciesBehaviours reg = new SpeciesBehaviours(Logger.getAnonymousLogger(), new Goals());
        Fake fake = new Fake();
        reg.register(fake);

        reg.onCheck(new SpeciesBehaviour.Check(mock(Mob.class), RECORD, LivestockAge.ADULT, 5L));

        assertThat(fake.events).containsExactly("check ADULT");
    }

    @Test
    void unregisteredSpeciesKeepsItsVanillaGoals() {
        Goals sink = new Goals();
        SpeciesBehaviours reg = new SpeciesBehaviours(Logger.getAnonymousLogger(), sink);
        reg.register(new Fake());

        reg.dispatchAdd(mock(Mob.class), new AnimalRecord("tfc:pig", RECORD.state(), 0, ""));

        assertThat(sink.log).isEmpty();
    }

    @Test
    void aSpeciesHasOneBehaviourAndAFailureIsContained() {
        SpeciesBehaviours reg = new SpeciesBehaviours(Logger.getAnonymousLogger(), new Goals());
        reg.register(new Fake());
        assertThatThrownBy(() -> reg.register(new Fake())).isInstanceOf(IllegalStateException.class);
        assertThat(reg.registeredSpecies()).containsExactly("tfc:cow", "tfc:sheep");

        SpeciesBehaviours bad = new SpeciesBehaviours(Logger.getAnonymousLogger(), new Goals());
        bad.register(new Fake() {
            @Override
            public void onCheck(Check check) {
                throw new IllegalStateException("boom");
            }
        });
        bad.onCheck(new SpeciesBehaviour.Check(mock(Mob.class), RECORD, LivestockAge.ADULT, 5L)); // must not throw
    }
}
