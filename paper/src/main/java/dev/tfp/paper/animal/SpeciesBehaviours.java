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
 *
 * <p>One record per check (PAR-FAUNA-28): {@link AnimalService} reads the record once and hands it to every hook; a hook
 * that changes the carrier's own record calls {@link #update} instead of writing the PDC, a later hook of the same check
 * sees the change through {@link #current}, and the service writes the record once after the last hook, only when
 * something changed. Outside a dispatch (a self-test calling a hook directly) {@link #update} writes at once.
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

    /** The record of the check being dispatched on this thread, and whether a hook changed it. */
    static final class Pending {
        final SpeciesBehaviour.Check check;
        AnimalRecord current;
        boolean changed;

        Pending(SpeciesBehaviour.Check check, boolean changed) {
            this.check = check;
            this.current = check.record();
            this.changed = changed;
        }
    }

    /** Checks run synchronously on the carrier's owning thread, so the dispatch in progress is per thread. */
    private static final ThreadLocal<Pending> DISPATCH = new ThreadLocal<>();

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
        dispatchCheck(check, false);
    }

    /**
     * Runs every check hook of the species with one shared record and returns the record to write (with the age step's
     * {@code changed} flag folded in), so the caller writes at most once. Hooks run in registration order.
     */
    Pending dispatchCheck(SpeciesBehaviour.Check check, boolean changed) {
        Pending pending = new Pending(check, changed);
        Pending outer = DISPATCH.get();
        DISPATCH.set(pending);
        try {
            for (SpeciesBehaviour b : all(check.record().species())) {
                try {
                    b.onCheck(check);
                } catch (RuntimeException ex) {
                    logger.log(Level.SEVERE, "Species behaviour for " + check.record().species() + " failed in its check", ex);
                }
            }
        } finally {
            if (outer == null) {
                DISPATCH.remove();
            } else {
                DISPATCH.set(outer);
            }
        }
        return pending;
    }

    /** The carrier's record as the earlier hooks of {@code check} left it ({@code check.record()} outside a dispatch). */
    public static AnimalRecord current(SpeciesBehaviour.Check check) {
        Pending p = DISPATCH.get();
        return p != null && p.check == check ? p.current : check.record();
    }

    /**
     * Replaces the carrier's record for the rest of {@code check}; the service writes it once after the last hook.
     * Outside a dispatch of {@code check} (a hook called directly) it is written to the carrier's PDC at once. Owning
     * thread.
     */
    public static void update(SpeciesBehaviour.Check check, AnimalRecord next) {
        Pending p = DISPATCH.get();
        if (p != null && p.check == check) {
            if (!next.equals(p.current)) {
                p.current = next;
                p.changed = true;
            }
            return;
        }
        AnimalPdc.write(check.entity().getPersistentDataContainer(), next);
    }

    /** Entry points for code outside the package (tests, self-tests) to drive the dispatch. */
    public void dispatchAdd(Mob mob, AnimalRecord record) {
        onAdd(mob, mob, record);
    }

    public List<String> registeredSpecies() {
        return bySpecies.keySet().stream().sorted().toList();
    }
}
