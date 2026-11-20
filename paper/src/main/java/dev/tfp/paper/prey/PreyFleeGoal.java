/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java,
 * AvoidPredatorAndRammersBehavior.java @ c3b4d9536
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalGoal;
import dev.tfp.paper.animal.AnimalPdc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A prey animal runs from what it avoids ({@link Sighting#avoided}: a player, a land predator, a ramming animal): the nearest
 * avoided sighting starts a retreat of 7 to 22 seconds ({@link PreyFlee}), during which it walks {@code 15} blocks away from
 * the threat, and stops once the threat is further than 20 blocks or the retreat time is over. Being hurt starts the
 * same retreat from the attacker ({@link #hurtBy}).
 */
final class PreyFleeGoal extends AnimalGoal {
    static final double SIGHT = 16.0;
    static final double SPEED = 1.35;
    private final Supplier<ClockSnapshot> clock;
    private final Predicate<Entity> landPredator;
    private final Predicate<Entity> rammer;
    private FleeState state = FleeState.IDLE;
    private Entity threat;
    /** Set from the damage event; picked up at the next question. */
    private volatile Entity attacker;

    PreyFleeGoal(Mob mob, Supplier<ClockSnapshot> clock, Predicate<Entity> landPredator, Predicate<Entity> rammer) {
        super(mob, "prey_flee", 5, GoalType.MOVE);
        this.clock = clock;
        this.landPredator = landPredator;
        this.rammer = rammer;
    }

    void hurtBy(Entity by) {
        attacker = by;
    }

    private static Position at(Entity e) {
        Location l = e.getLocation();
        return new Position(l.getX(), l.getY(), l.getZ());
    }

    @Override
    protected boolean wants() {
        long now = clock.get().playerTicks();
        Entity hit = attacker;
        if (hit != null) {
            attacker = null;
            if (hit.isValid() && !hit.getType().equals(mob.getType())) {
                begin(hit, now);
                return true;
            }
        }
        if (state.isActive(now) && threat != null && threat.isValid()) {
            return true;
        }
        List<Sighting> sightings = new ArrayList<>();
        List<Entity> sources = new ArrayList<>();
        for (Entity e : mob.getNearbyEntities(SIGHT, SIGHT / 2, SIGHT)) {
            if (e == mob || !Bukkit.isOwnedByCurrentRegion(e)) {
                continue;
            }
            boolean player = e instanceof Player;
            boolean spectating = player && (((Player) e).getGameMode() == GameMode.CREATIVE
                    || ((Player) e).getGameMode() == GameMode.SPECTATOR);
            sightings.add(new Sighting(e.getType().getKey().toString(), player, spectating, landPredator.test(e),
                    rammer.test(e), at(e)));
            sources.add(e);
        }
        Optional<Sighting> found = PreyFlee.trigger(false, at(mob), sightings);
        if (found.isEmpty()) {
            return false;
        }
        Entity source = sources.get(sightings.indexOf(found.get()));
        begin(source, now);
        return true;
    }

    private void begin(Entity from, long now) {
        threat = from;
        state = PreyFlee.startFlee(now, from.getType().getKey().toString(),
                PreyFlee.retreatTicks(ThreadLocalRandom.current()));
    }

    @Override
    protected boolean keepsGoing() {
        long now = clock.get().playerTicks();
        return threat != null && threat.isValid() && state.isActive(now)
                && !PreyFlee.wantsToStopFleeing(state, now, at(threat).distanceSq(at(mob)));
    }

    @Override
    protected void step() {
        if (threat == null || mob.getTicksLived() % 10 != 0) {
            return;
        }
        Vector away = mob.getLocation().toVector().subtract(threat.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(ThreadLocalRandom.current().nextDouble() - 0.5, 0, ThreadLocalRandom.current().nextDouble() - 0.5);
        }
        Location target = mob.getLocation().add(away.normalize().multiply(PreyFlee.FLEE_RADIUS_BLOCKS));
        target.setY(mob.getWorld().getHighestBlockYAt(target) + 1);
        mob.getPathfinder().moveTo(target, SPEED);
    }

    @Override
    protected void end() {
        state = FleeState.IDLE;
        threat = null;
        mob.getPathfinder().stopPathfinding();
    }
}
