/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.RamRules;
import dev.tfp.core.prey.Sighting;
import dev.tfp.core.prey.SmallPrey;
import dev.tfp.core.prey.WildPrey;
import dev.tfp.paper.animal.AnimalPdc;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Reads live entities into the immutable facts the {@code dev.tfp.core.prey} rules decide on (the snapshot half of
 * "decide, then apply"). Owning thread only; the tag test itself is thread-safe.
 */
final class PreySenses {
    static final String LAND_PREDATORS = SpeciesTags.LAND_PREDATORS;
    static final String RAMMING_ANIMALS = SpeciesTags.RAMMING_ANIMALS;
    static final String NOT_RAMMED = SpeciesTags.NOT_RAMMED;

    private final SpeciesTags tags;
    /** Sightings built, for the load tests. */
    long sightingsBuilt;

    PreySenses(BiPredicate<String, String> speciesInTag) {
        this.tags = new SpeciesTags(speciesInTag);
    }

    static Position at(Entity e) {
        Location l = e.getLocation();
        return new Position(l.getX(), l.getY(), l.getZ());
    }

    static Optional<AnimalRecord> record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer());
    }

    /** Our species id for one of our animals, else the vanilla type key (cached, see {@link SpeciesCache}). */
    static String species(Entity e) {
        return SpeciesCache.of(e);
    }

    private int flags(Entity e) {
        return tags.flags(SpeciesCache.ours(e));
    }

    boolean landPredator(Entity e) {
        return (flags(e) & SpeciesTags.LAND_PREDATOR) != 0;
    }

    static boolean creativeOrSpectator(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    /**
     * Whether a sighting of {@code e} could start a flee for any {@link dev.tfp.core.prey.Avoidance}: a player, a land
     * predator or a ramming animal. The cheap pre-filter before the line of sight and the {@link Sighting}; the rule
     * itself stays {@code Avoidance.avoids}.
     */
    boolean mayAvoid(Entity e) {
        return (kind(e) & SpeciesTags.MAY_AVOID) != 0;
    }

    /** The kind bits ({@link SpeciesTags#kind}). */
    int kind(Entity e) {
        return tags.kind(e);
    }

    Sighting sighting(LivingEntity e) {
        sightingsBuilt++;
        int f = flags(e);
        return new Sighting(species(e), e instanceof Player, creativeOrSpectator(e), (f & SpeciesTags.LAND_PREDATOR) != 0,
                (f & SpeciesTags.RAMMER) != 0, at(e));
    }

    /** {@link #sighting} built once per entity and tick (stamp), shared by every animal that sees it. */
    Sighting sighting(LivingEntity e, long stamp) {
        return Sightings.of(e, stamp, this::sighting);
    }

    static boolean baby(Entity e, long now) {
        if (!SpeciesCache.knownGrown(e, now, false)) {
            Optional<AnimalRecord> r = record(e);
            if (r.isPresent() && r.get().wildBabyAt(now)) {
                return true;
            }
            if (r.isPresent()) {
                SpeciesCache.grown(e, now, false); // the flag only clears (PAR-FAUNA-33)
            }
        }
        return e instanceof Ageable a && !a.isAdult();
    }

    static boolean male(Entity e) {
        return record(e).map(r -> r.state().male()).orElse(false);
    }

    RamRules.Candidate candidate(LivingEntity e, long now) {
        boolean rammer = WildPrey.kind(species(e)).filter(k -> k == WildPrey.Kind.RAMMING).isPresent();
        double max = e.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : e.getAttribute(Attribute.MAX_HEALTH).getValue();
        return new RamRules.Candidate(rammer, (flags(e) & SpeciesTags.NOT_RAMMED_BY_RAMMERS) != 0, male(e), baby(e, now),
                (float) (e.getHealth() / Math.max(1e-6, max)), creativeOrSpectator(e));
    }

    SmallPrey.Threat threat(LivingEntity e) {
        boolean sneaking = e instanceof HumanEntity h && h.isSneaking();
        return new SmallPrey.Threat(at(e), e.getVelocity().length(), sneaking, e.isInWater(), e.isSleeping());
    }
}
