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
    static final String LAND_PREDATORS = "tfp:land_predators";
    static final String RAMMING_ANIMALS = "tfp:ramming_animals";
    static final String NOT_RAMMED = "tfp:not_rammed_by_rammers";

    /** Whether the species id ({@code tfc:wolf}) is in the entity tag. */
    private final BiPredicate<String, String> speciesInTag;

    PreySenses(BiPredicate<String, String> speciesInTag) {
        this.speciesInTag = speciesInTag;
    }

    static Position at(Entity e) {
        Location l = e.getLocation();
        return new Position(l.getX(), l.getY(), l.getZ());
    }

    static Optional<AnimalRecord> record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer());
    }

    /** Our species id for one of our animals, else the vanilla type key. */
    static String species(Entity e) {
        return record(e).map(AnimalRecord::species).orElseGet(() -> e.getType().getKey().toString());
    }

    private boolean inTag(Entity e, String tag) {
        Optional<AnimalRecord> r = record(e);
        if (r.isEmpty()) {
            return false;
        }
        String s = r.get().species();
        return speciesInTag.test("tfp:" + s.substring(s.indexOf(':') + 1), tag);
    }

    boolean landPredator(Entity e) {
        return inTag(e, LAND_PREDATORS);
    }

    static boolean creativeOrSpectator(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    Sighting sighting(LivingEntity e) {
        return new Sighting(species(e), e instanceof Player, creativeOrSpectator(e), landPredator(e),
                inTag(e, RAMMING_ANIMALS), at(e));
    }

    static boolean baby(Entity e, long now) {
        Optional<AnimalRecord> r = record(e);
        if (r.isPresent() && r.get().wildBabyAt(now)) {
            return true;
        }
        return e instanceof Ageable a && !a.isAdult();
    }

    static boolean male(Entity e) {
        return record(e).map(r -> r.state().male()).orElse(false);
    }

    RamRules.Candidate candidate(LivingEntity e, long now) {
        boolean rammer = WildPrey.kind(species(e)).filter(k -> k == WildPrey.Kind.RAMMING).isPresent();
        double max = e.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : e.getAttribute(Attribute.MAX_HEALTH).getValue();
        return new RamRules.Candidate(rammer, inTag(e, NOT_RAMMED), male(e), baby(e, now),
                (float) (e.getHealth() / Math.max(1e-6, max)), creativeOrSpectator(e));
    }

    SmallPrey.Threat threat(LivingEntity e) {
        boolean sneaking = e instanceof HumanEntity h && h.isSneaking();
        return new SmallPrey.Threat(at(e), e.getVelocity().length(), sneaking, e.isInWater(), e.isSleeping());
    }
}
