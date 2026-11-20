/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.animal.CarrierTable;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.livestock.LivestockFood;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Livestock AI (PAR-FAUNA-08): TFC's livestock follow a player holding their food and children follow an adult of their
 * species, on top of the carrier's vanilla goals (wandering, looking, panic), which stay. {@code /tfp debug pen} spawns a
 * male and a female adult of each of the 18 livestock species in a grid in front of the player (the roster showcase).
 */
public final class LivestockAiModule implements FeatureModule {
    private ModuleContext ctx;
    private Tags tags;

    @Override
    public String id() {
        return "livestockai";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        animals.behaviours().register(new Ai(animals));
    }

    private final class Ai implements SpeciesBehaviour {
        private final AnimalService animals;

        Ai(AnimalService animals) {
            this.animals = animals;
        }

        @Override
        public Set<String> species() {
            return CarrierTable.get().all().stream().filter(c -> c.livestock().isPresent()).map(Carrier::id)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            Carrier carrier = animals.table().find(record.species()).orElseThrow();
            String tag = LivestockFood.tag(carrier);
            return List.of(new GoalSpec(2, new TemptByFoodGoal(mob, stack -> tags.is(stack, tag))),
                    new GoalSpec(4, new FollowAdultGoal(mob, ctx.clock()::get)));
        }
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("pen", node -> node.executes(tree.run(c -> {
            if (!(c.getSource().getSender() instanceof Player player)) {
                return CompletableFuture.completedFuture(Reply.error("debug.census.player_only"));
            }
            return pen(player);
        })));
    }

    /** One male and one female adult of each livestock species, three blocks apart, in rows in front of the player. */
    private CompletableFuture<Reply> pen(Player player) {
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        Location origin = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(6));
        List<Carrier> species = animals.table().all().stream().filter(c -> c.livestock().isPresent()).toList();
        List<CompletableFuture<Entity>> spawned = new ArrayList<>();
        int i = 0;
        for (Carrier c : species) {
            for (boolean male : new boolean[] {false, true}) {
                Location at = origin.clone().add((i % 6) * 3.0, 0, (i / 6) * 3.0);
                at.setY(player.getWorld().getHighestBlockYAt(at) + 1);
                spawned.add(animals.spawn(at, c, AnimalSpawnRoll.AgeHint.ADULT, male));
                i++;
            }
        }
        return CompletableFuture.allOf(spawned.toArray(CompletableFuture[]::new)).handle((v, e) ->
                e == null ? Reply.ok("debug.pen.done", spawned.size(), species.size())
                        : Reply.error("debug.pen.failed", String.valueOf(e.getMessage())));
    }
}
