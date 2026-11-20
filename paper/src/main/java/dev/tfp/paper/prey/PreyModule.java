/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

/**
 * Prey AI (PAR-FAUNA-09): TFC's wild prey (deer family, game birds, boar, moose, wildebeest, bison) run from players, land
 * predators and ramming animals ({@link PreyFleeGoal}), replacing the carrier's vanilla goals with the flee goal plus the
 * carrier's idle goals (wandering and looking stay, vanilla panic is replaced by the flee). Ramming charges and the
 * small prey (fox, rabbit, armadillo, panda, frog) are not part of this slice.
 */
public final class PreyModule implements FeatureModule, Listener {
    /** TFC's {@code Prey}, {@code WingedPrey} and {@code RammingPrey} species. */
    static final Set<String> SPECIES = Set.of("tfc:deer", "tfc:caribou", "tfc:bongo", "tfc:gazelle", "tfc:grouse",
            "tfc:pheasant", "tfc:turkey", "tfc:peafowl", "tfc:boar", "tfc:moose", "tfc:wildebeest", "tfc:bison");

    private ModuleContext ctx;
    private Tags tags;
    private final Map<UUID, PreyFleeGoal> goals = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "prey";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        animals.behaviours().register(new Ai());
        context.listen(this);
    }

    /** Whether {@code e} is one of our animals whose species is in the entity tag {@code tag} ({@code tfp:land_predators}). */
    private boolean speciesIn(Entity e, String tag) {
        Optional<AnimalRecord> r = AnimalPdc.read(e.getPersistentDataContainer());
        if (r.isEmpty()) {
            return false;
        }
        String id = "tfp:" + r.get().species().substring(r.get().species().indexOf(':') + 1);
        return tags.is(TagType.ENTITY_TYPE, id, tag);
    }

    private final class Ai implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return SPECIES;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            PreyFleeGoal goal = new PreyFleeGoal(mob, ctx.clock()::get, e -> speciesIn(e, "tfp:land_predators"),
                    e -> speciesIn(e, "tfp:ramming_animals"));
            goals.put(mob.getUniqueId(), goal);
            return List.of(new GoalSpec(1, goal));
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        PreyFleeGoal goal = goals.get(event.getEntity().getUniqueId());
        if (goal != null) {
            Entity damager = event.getDamager() instanceof org.bukkit.entity.Projectile p
                    && p.getShooter() instanceof Entity shooter ? shooter : event.getDamager();
            goal.hurtBy(damager);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        goals.remove(event.getEntity().getUniqueId());
    }
}
