/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/Seat.java @ c3b4d9536 (sit, getSittingEntity, stopRiding)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.ProductKind;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.livestock.products.ProductState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.ProductPdc;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

/**
 * PAR-FAUNA-07: the sitting hen on Paper. TFC's {@code Seat} entity makes the hen ride a marker at the nest; here the hen
 * is moved onto the nest block and held there ({@link Mob#setAware} off) until she lays or is released. The hold lives in
 * memory only: a restart frees every hen (a stale seat is cleared by the nest on its next callback, ledger).
 */
public final class PaperNestHens implements NestHens {
    /** How far from the nest centre a hen still counts as sitting in it. */
    public static final double SEAT_REACH = 2.5;

    private final AnimalService animals;
    private final Scheduler scheduler;
    private final Set<UUID> held = ConcurrentHashMap.newKeySet();

    public PaperNestHens(AnimalService animals, Scheduler scheduler) {
        this.animals = animals;
        this.scheduler = scheduler;
    }

    static Location center(Block nest) {
        return nest.getLocation().add(0.5, 0.0, 0.5);
    }

    /** True while {@code id} is held by a nest (any thread). */
    public boolean holds(UUID id) {
        return held.contains(id);
    }

    private Optional<Entity> entity(Block nest, String id) {
        Location c = center(nest);
        for (Entity e : nest.getWorld().getNearbyEntities(c, SEAT_REACH, SEAT_REACH, SEAT_REACH)) {
            if (e.getUniqueId().toString().equals(id) && !e.isDead()) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Hen> find(Block nest, String id) {
        Optional<Entity> found = entity(nest, id);
        if (found.isEmpty()) {
            held.remove(parse(id));
            return Optional.empty();
        }
        Entity e = found.get();
        Optional<AnimalRecord> record = animals.record(e);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProductSpec> spec = ProductPdc.specOf(record.get().species());
        Optional<LivestockSpecies> species = animals.table().find(record.get().species()).flatMap(Carrier::livestock);
        if (spec.isEmpty() || species.isEmpty() || spec.get().kind() != ProductKind.EGG) {
            return Optional.empty();
        }
        return Optional.of(new Hen(id, record.get().species(), species.get(), spec.get(), record.get().state(),
            ProductPdc.read(e)));
    }

    @Override
    public void laid(Block nest, Hen hen, AnimalState after, ProductState product) {
        Optional<Entity> found = entity(nest, hen.id());
        held.remove(parse(hen.id()));
        found.ifPresent(e -> scheduler.entity(e, () -> {
            animals.record(e).ifPresent(r -> AnimalPdc.write(e.getPersistentDataContainer(), r.withState(after)));
            ProductPdc.write(e, product);
            stand(e);
        }, () -> { }));
    }

    @Override
    public void release(Block nest, String id) {
        held.remove(parse(id));
        entity(nest, id).ifPresent(e -> scheduler.entity(e, () -> stand(e), () -> { }));
    }

    /** Sits {@code hen} in {@code nest}: moves her onto it and holds her (TFC {@code Seat.sit}). */
    public void sit(Entity hen, Block nest) {
        held.add(hen.getUniqueId());
        scheduler.entity(hen, () -> {
            hen.teleportAsync(center(nest));
            if (hen instanceof Mob m) {
                m.setAware(false);
            }
        }, () -> held.remove(hen.getUniqueId()));
    }

    private static void stand(Entity e) {
        if (e instanceof Mob m) {
            m.setAware(true);
        }
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return new UUID(0, 0);
        }
    }
}
