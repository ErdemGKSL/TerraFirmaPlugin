/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.prey.Sightings;
import dev.tfp.paper.prey.SpeciesCache;
import dev.tfp.paper.prey.SpeciesTags;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Reads live entities into the immutable facts the {@code dev.tfp.core} rules decide on (the snapshot half of "decide,
 * then apply"). Owning thread only; the tag test itself is thread-safe.
 */
final class LivestockSenses {
    static final String LAND_PREDATORS = SpeciesTags.LAND_PREDATORS;
    static final String RAMMING_ANIMALS = SpeciesTags.RAMMING_ANIMALS;

    private final SpeciesTags tags;
    /** Sightings built, for the load tests. */
    long sightingsBuilt;

    LivestockSenses(BiPredicate<String, String> speciesInTag) {
        this.tags = new SpeciesTags(speciesInTag);
    }

    static Position at(Entity e) {
        Location l = e.getLocation();
        return new Position(l.getX(), l.getY(), l.getZ());
    }

    static double distanceSq(Entity a, Entity b) {
        return at(a).distanceSq(at(b));
    }

    static Optional<AnimalRecord> record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer());
    }

    /** Our species id for one of our animals, else the vanilla type key (cached, see {@link SpeciesCache}). */
    static String species(Entity e) {
        return SpeciesCache.of(e);
    }

    /** {@code isBaby}: a livestock child by its birth tick, a wild baby by its flag, else the vanilla age. */
    static boolean baby(Entity e, long now) {
        if (SpeciesCache.knownGrown(e, now, true)) {
            return false; // read grown before: ages only grow (PAR-FAUNA-33)
        }
        Optional<AnimalRecord> r = record(e);
        if (r.isPresent()) {
            boolean child = AnimalCheck.ageAt(r.get(), now) == LivestockAge.CHILD;
            if (!child) {
                SpeciesCache.grown(e, now, true);
            }
            return child;
        }
        return e instanceof Ageable a && !a.isAdult();
    }

    static boolean creativeOrSpectator(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    private int flags(Entity e) {
        return tags.flags(SpeciesCache.ours(e));
    }

    /**
     * Whether a sighting of {@code e} could start the avoid: a player, a land predator or a ramming animal. The cheap
     * pre-filter before the line of sight and the {@link Sighting}; the rule itself stays {@code Avoidance.avoids}.
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
}
