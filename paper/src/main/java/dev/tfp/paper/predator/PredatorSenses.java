/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (getAttackTarget), same
 * commit; with vanilla 1.21.1 LivingEntity.canAttack and the NEAREST_VISIBLE_ATTACKABLE_PLAYER sensor
 */
package dev.tfp.paper.predator;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.predator.HuntedPrey;
import dev.tfp.core.predator.PredatorRules;
import dev.tfp.core.predator.PreyCandidate;
import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalPdc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Reads live entities into the immutable facts the {@code dev.tfp.core.predator} rules decide on. Owning thread only.
 * Cheap tests (player or one of our animals of a hunted type) come before any record is built.
 */
final class PredatorSenses {
    private PredatorSenses() {
    }

    static Position at(Entity e) {
        Location l = e.getLocation();
        return new Position(l.getX(), l.getY(), l.getZ());
    }

    static Optional<AnimalRecord> record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer());
    }

    static String species(Entity e) {
        return record(e).map(AnimalRecord::species).orElseGet(() -> e.getType().getKey().toString());
    }

    static boolean baby(Entity e, long now) {
        Optional<AnimalRecord> r = record(e);
        if (r.isPresent() && r.get().wildBabyAt(now)) {
            return true;
        }
        return e instanceof Ageable a && !a.isAdult();
    }

    static boolean creativeOrSpectator(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    /**
     * Vanilla {@code LivingEntity.canAttack}: never a player on peaceful, never a creative or spectator player, never an
     * invulnerable entity, never a dead one.
     */
    static boolean canAttack(LivingEntity target, boolean peaceful) {
        if (!PredatorBrain.alive(target) || target.isInvulnerable()) {
            return false;
        }
        return !(target instanceof Player) || !(peaceful || creativeOrSpectator(target));
    }

    /**
     * The attack candidates among the visible entities: attackable players, and our animals of a
     * {@code hunted_by_land_predators} type. Anything else can never be chosen, so it is not read.
     *
     * @param seen     the visible entities
     * @param peaceful the difficulty is peaceful (players are not attackable)
     * @param out      the entities, in the same order as the returned candidates
     */
    static List<PreyCandidate> candidates(List<LivingEntity> seen, boolean peaceful, List<LivingEntity> out) {
        List<PreyCandidate> list = new ArrayList<>();
        for (LivingEntity e : seen) {
            if (e instanceof Player) {
                if (!canAttack(e, peaceful)) {
                    continue;
                }
                list.add(new PreyCandidate(new Sighting("minecraft:player", true, false, false, false, at(e)), e.isInWater()));
                out.add(e);
                continue;
            }
            if (!AnimalPdc.has(e.getPersistentDataContainer())) {
                continue;
            }
            String species = species(e);
            if (!HuntedPrey.isHuntedByLandPredator(species) || !canAttack(e, peaceful)) {
                continue;
            }
            list.add(new PreyCandidate(new Sighting(species, false, false, false, false, at(e)), e.isInWater()));
            out.add(e);
        }
        return list;
    }

    /** The target's state as {@code stopAttackingIf} reads it. */
    static PredatorRules.Target target(Mob mob, LivingEntity t, boolean peaceful) {
        if (t == null) {
            return new PredatorRules.Target(false, false, false, false);
        }
        boolean dead = t.isDead() || t.getHealth() <= 0;
        return new PredatorRules.Target(canAttack(t, peaceful), t.isValid() && !dead, dead,
                t.getWorld().equals(mob.getWorld()));
    }
}
