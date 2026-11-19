/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.LivestockAge;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

/**
 * What one TFC species (or a family of them) adds to the animal framework (PAR-FAUNA-21): its goals, its per-check hook
 * and its add-to-world hook. FAUNA features (livestock, prey, predators, pets, ...) implement this in their own packages
 * and register it with {@link SpeciesBehaviours#register}, so {@link AnimalService} is never edited per feature.
 *
 * <p>All callbacks run on the animal's owning thread (entity scheduler). Keep them short: decide async, apply sync.
 */
public interface SpeciesBehaviour {
    /** The species ids ({@code tfc:cow}, ...) this behaviour serves. A species has at most one behaviour. */
    Set<String> species();

    /**
     * The goals to run on {@code mob}, in place of the carrier's vanilla goals. Called each time the animal enters a
     * world (spawn, chunk load), because vanilla goals are rebuilt then and ours are not persisted. An empty list leaves
     * the carrier idle (no wandering, no panic), which is the point: TFC's own AI is the only AI.
     */
    default List<GoalSpec> goals(Mob mob, AnimalRecord record) {
        return List.of();
    }

    /** True to keep the carrier's vanilla goals and add {@link #goals} to them (for a species with no TFC AI yet). */
    default boolean keepVanillaGoals() {
        return false;
    }

    /** Runs once per animal each time it enters a world, after its goals were installed. */
    default void onAdd(Entity entity, AnimalRecord record) {
    }

    /** Runs on each staggered check ({@link AnimalService#PERIOD} ticks), after the age and scale step. */
    default void onCheck(Check check) {
    }

    /** One goal with its priority (lower runs first, as in vanilla). */
    record GoalSpec(int priority, Goal<Mob> goal) {
    }

    /**
     * The context of one check.
     *
     * @param entity      the carrier
     * @param record      the record after the age step
     * @param age         the stage now
     * @param playerTicks the player-time clock
     */
    record Check(Entity entity, AnimalRecord record, LivestockAge age, long playerTicks) {
    }
}
