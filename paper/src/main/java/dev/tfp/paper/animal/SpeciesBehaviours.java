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

    private final Map<String, SpeciesBehaviour> bySpecies = new ConcurrentHashMap<>();
    private final Logger logger;
    private final GoalSink sink;

    public SpeciesBehaviours(Logger logger) {
        this(logger, PAPER);
    }

    public SpeciesBehaviours(Logger logger, GoalSink sink) {
        this.logger = logger;
        this.sink = sink;
    }

    /** Registers {@code behaviour} for each of its species; a species that already has one is refused. */
    public void register(SpeciesBehaviour behaviour) {
        for (String id : behaviour.species()) {
            SpeciesBehaviour previous = bySpecies.putIfAbsent(id, behaviour);
            if (previous != null && previous != behaviour) {
                throw new IllegalStateException("species " + id + " already has a behaviour: " + previous);
            }
        }
    }

    public Optional<SpeciesBehaviour> find(String species) {
        return Optional.ofNullable(bySpecies.get(species));
    }

    /** Replaces the carrier's goals by the species' (add-to-world), then runs its add hook. No behaviour: nothing changes. */
    void onAdd(Mob mob, Entity entity, AnimalRecord record) {
        SpeciesBehaviour b = bySpecies.get(record.species());
        if (b == null) {
            return;
        }
        try {
            if (!b.keepVanillaGoals()) {
                sink.removeAll(mob);
            }
            for (SpeciesBehaviour.GoalSpec g : b.goals(mob, record)) {
                sink.add(mob, g.priority(), g.goal());
            }
            b.onAdd(entity, record);
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Species behaviour for " + record.species() + " failed on add", ex);
        }
    }

    void onCheck(SpeciesBehaviour.Check check) {
        SpeciesBehaviour b = bySpecies.get(check.record().species());
        if (b == null) {
            return;
        }
        try {
            b.onCheck(check);
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Species behaviour for " + check.record().species() + " failed in its check", ex);
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
