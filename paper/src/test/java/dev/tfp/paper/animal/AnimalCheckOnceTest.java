/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-28: one staggered check reads the animal record once and writes it at most once, after every hook. */
class AnimalCheckOnceTest {
    private static final String SPECIES = "tfc:test_once";
    private static final long NOW = 10_000L;

    /** Counts the record reads and writes of one carrier. */
    private static final class CountingStore implements AnimalService.RecordStore {
        AnimalRecord stored;
        int reads;
        final List<AnimalRecord> writes = new ArrayList<>();

        CountingStore(AnimalRecord stored) {
            this.stored = stored;
        }

        @Override
        public Optional<AnimalRecord> read(Entity e) {
            reads++;
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(Entity e, AnimalRecord record) {
            writes.add(record);
            stored = record;
        }
    }

    /** A hook that may change the record and logs what it saw. */
    private static final class Hook implements SpeciesBehaviour {
        final List<String> seen = new ArrayList<>();
        final java.util.function.UnaryOperator<AnimalRecord> change;

        Hook(java.util.function.UnaryOperator<AnimalRecord> change) {
            this.change = change;
        }

        @Override
        public Set<String> species() {
            return Set.of(SPECIES);
        }

        @Override
        public void onCheck(Check check) {
            AnimalRecord current = SpeciesBehaviours.current(check);
            seen.add(current.variant());
            AnimalRecord next = change.apply(current);
            if (next != current) {
                SpeciesBehaviours.update(check, next);
            }
        }
    }

    private static Entity entity() {
        return (Entity) java.lang.reflect.Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "test entity";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static AnimalService service(GlobalClockHolder clock) {
        clock.publish(NOW, NOW, 8, true);
        return new AnimalService(null, clock, () -> {
            throw new AssertionError("no model work expected");
        }, Logger.getAnonymousLogger());
    }

    private static AnimalRecord variant(AnimalRecord r, String variant) {
        return new AnimalRecord(r.species(), r.state(), r.geneticSize(), variant, r.baby(), r.growTick());
    }

    private static AnimalRecord record(String variant) {
        return new AnimalRecord(SPECIES, AnimalState.spawned(false, 0, 0), 0, variant);
    }

    @Test
    void twoHooksChangingTheRecordGiveOneReadAndOneWrite() {
        AnimalService animals = service(new GlobalClockHolder());
        Hook first = new Hook(r -> variant(r, "a"));
        Hook second = new Hook(r -> variant(r, r.variant() + "b"));
        animals.behaviours().register(first);
        animals.behaviours().register(second);
        AnimalRecord start = record("");
        CountingStore store = new CountingStore(start);

        AnimalService.CheckOutcome out = animals.checkBody(entity(), AnimalCheck.ageAt(start, NOW), store);

        assertThat(out.ran()).isTrue();
        assertThat(store.reads).isEqualTo(1);
        assertThat(second.seen).containsExactly("a"); // the later hook sees the earlier hook's change
        assertThat(store.writes).containsExactly(variant(start, "ab"));
    }

    @Test
    void ageStepAndHookChangeShareTheOneWrite() {
        AnimalService animals = service(new GlobalClockHolder());
        animals.behaviours().register(new Hook(r -> variant(r, "x")));
        // a wild baby whose grow-up tick has passed: the age step changes the record too
        AnimalRecord baby = new AnimalRecord(SPECIES, AnimalState.spawned(true, 0, 0), 0, "", true, 100L);
        CountingStore store = new CountingStore(baby);
        LivestockAge age = AnimalCheck.decide(baby, NOW, 0, null).age();

        animals.checkBody(entity(), age, store);

        assertThat(store.reads).isEqualTo(1);
        assertThat(store.writes).hasSize(1);
        assertThat(store.writes.getFirst().baby()).isFalse();
        assertThat(store.writes.getFirst().variant()).isEqualTo("x");
    }

    @Test
    void nothingChangedMeansNoWrite() {
        AnimalService animals = service(new GlobalClockHolder());
        Hook idle = new Hook(r -> r);
        animals.behaviours().register(idle);
        animals.behaviours().register(new Hook(r -> variant(r, r.variant()))); // an equal record is no change
        AnimalRecord start = record("same");
        CountingStore store = new CountingStore(start);

        animals.checkBody(entity(), AnimalCheck.ageAt(start, NOW), store);

        assertThat(store.reads).isEqualTo(1);
        assertThat(store.writes).isEmpty();
        assertThat(idle.seen).containsExactly("same");
    }

    @Test
    void noRecordStopsWithoutWriting() {
        AnimalService animals = service(new GlobalClockHolder());
        CountingStore store = new CountingStore(null);

        AnimalService.CheckOutcome out = animals.checkBody(entity(), null, store);

        assertThat(out.stop()).isTrue();
        assertThat(store.reads).isEqualTo(1);
        assertThat(store.writes).isEmpty();
    }
}
