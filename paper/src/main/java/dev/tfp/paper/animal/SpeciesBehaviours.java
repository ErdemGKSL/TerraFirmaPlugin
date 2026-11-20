/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.animal.AnimalRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

/**
 * The registry of {@link SpeciesBehaviour}s and the dispatch {@link AnimalService} calls into (PAR-FAUNA-21). A species
 * without a behaviour keeps its carrier's vanilla goals. A failing behaviour is logged and never stops the animal's
 * check, so one feature's bug cannot freeze the age step of every animal.
 */
public final class SpeciesBehaviours {
    /** Where goals are written: Paper's {@code MobGoals} in the server, a recorder in tests. */
    public interface GoalSink {
        void removeAll(Mob mob);

        void add(Mob mob, int priority, Goal<Mob> goal);
    }

    /** Paper's goal API. */
    public static final GoalSink PAPER = new GoalSink() {
        @Override
        public void removeAll(Mob mob) {
            Bukkit.getMobGoals().removeAllGoals(mob);
        }

        @Override
        public void add(Mob mob, int priority, Goal<Mob> goal) {
            Bukkit.getMobGoals().addGoal(mob, priority, goal);
        }
    };

    private final Map<String, List<SpeciesBehaviour>> bySpecies = new ConcurrentHashMap<>();
    private final Logger logger;
    private final GoalSink sink;

    public SpeciesBehaviours(Logger logger) {
        this(logger, PAPER);
    }

    public SpeciesBehaviours(Logger logger, GoalSink sink) {
        this.logger = logger;
        this.sink = sink;
    }

    /**
     * Registers {@code behaviour} for each of its species. A species may have several behaviours (livestock rules and
     * products, say): their goals are all installed, their hooks all run, in registration order. The vanilla goals are
     * removed unless every behaviour of the species keeps them.
     */
    public void register(SpeciesBehaviour behaviour) {
        for (String id : behaviour.species()) {
            bySpecies.compute(id, (k, list) -> {
                List<SpeciesBehaviour> next = new java.util.ArrayList<>(list == null ? List.of() : list);
                if (next.contains(behaviour)) {
                    throw new IllegalStateException("behaviour " + behaviour + " is already registered for " + id);
                }
                next.add(behaviour);
                return List.copyOf(next);
            });
        }
    }

    /** The first behaviour of {@code species}, if any. */
    public Optional<SpeciesBehaviour> find(String species) {
        return Optional.ofNullable(bySpecies.get(species)).flatMap(l -> l.stream().findFirst());
    }

    public List<SpeciesBehaviour> all(String species) {
        return bySpecies.getOrDefault(species, List.of());
    }

    /** Replaces the carrier's goals by the species' (add-to-world), then runs the add hooks. No behaviour: nothing changes. */
    void onAdd(Mob mob, Entity entity, AnimalRecord record) {
        List<SpeciesBehaviour> list = all(record.species());
        if (list.isEmpty()) {
            return;
        }
        try {
            if (list.stream().anyMatch(b -> !b.keepVanillaGoals())) {
                sink.removeAll(mob);
            }
            for (SpeciesBehaviour b : list) {
                for (SpeciesBehaviour.GoalSpec g : b.goals(mob, record)) {
                    sink.add(mob, g.priority(), g.goal());
                }
            }
            for (SpeciesBehaviour b : list) {
                b.onAdd(entity, record);
            }
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Species behaviour for " + record.species() + " failed on add", ex);
        }
    }

    void onCheck(SpeciesBehaviour.Check check) {
        for (SpeciesBehaviour b : all(check.record().species())) {
            try {
                b.onCheck(check);
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Species behaviour for " + check.record().species() + " failed in its check", ex);
            }
        }
    }

    /** Entry points for code outside the package (tests, self-tests) to drive the dispatch. */
    public void dispatchAdd(Mob mob, AnimalRecord record) {
        onAdd(mob, mob, record);
    }

    public List<String> registeredSpecies() {
        return bySpecies.keySet().stream().sorted().toList();
    }
}
