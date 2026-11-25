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
    static final String LAND_PREDATORS = "tfp:land_predators";
    static final String RAMMING_ANIMALS = "tfp:ramming_animals";

    /** Whether the species id ({@code tfp:wolf}) is in the entity tag. */
    private final BiPredicate<String, String> speciesInTag;

    LivestockSenses(BiPredicate<String, String> speciesInTag) {
        this.speciesInTag = speciesInTag;
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

    /** Our species id for one of our animals, else the vanilla type key. */
    static String species(Entity e) {
        return record(e).map(AnimalRecord::species).orElseGet(() -> e.getType().getKey().toString());
    }

    /** {@code isBaby}: a livestock child by its birth tick, a wild baby by its flag, else the vanilla age. */
    static boolean baby(Entity e, long now) {
        Optional<AnimalRecord> r = record(e);
        if (r.isPresent()) {
            return AnimalCheck.ageAt(r.get(), now) == LivestockAge.CHILD;
        }
        return e instanceof Ageable a && !a.isAdult();
    }

    static boolean creativeOrSpectator(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    private boolean inTag(Entity e, String tag) {
        Optional<AnimalRecord> r = record(e);
        if (r.isEmpty()) {
            return false;
        }
        String s = r.get().species();
        return speciesInTag.test("tfp:" + s.substring(s.indexOf(':') + 1), tag);
    }

    Sighting sighting(LivingEntity e) {
        return new Sighting(species(e), e instanceof Player, creativeOrSpectator(e), inTag(e, LAND_PREDATORS),
                inTag(e, RAMMING_ANIMALS), at(e));
    }
}
