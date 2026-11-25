/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/LayEggBehavior.java @ c3b4d9536
 *       (checkExtraStartConditions: isReadyForAnimalProduct, isTargetAt: a nest box with an open slot, afterReached: sit)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.ProductKind;
import dev.tfp.core.livestock.products.ProductRules;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.nestbox.NestBoxRules;
import dev.tfp.core.nestbox.NestBoxState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.ProductPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/**
 * PAR-FAUNA-07: a hen that is ready for her egg looks for a nest box with a free slot and sits in it. TFC runs this as a
 * brain behaviour (sensor, path, {@code Seat.sit}); here the animal's staggered check ({@link AnimalService#PERIOD}) finds
 * the nearest known nest box ({@link NestFinder}) and, on the nest's region thread, seats her if the nest is free and has a
 * slot. She is moved there, not walked (ledger). Species: every oviparous carrier.
 */
public final class NestSeeker implements SpeciesBehaviour {
    /** TFC searches 48 blocks along a path; we take the nearest nest box in this horizontal reach. */
    public static final double RADIUS = 16.0;
    public static final double VERTICAL = 4.0;
    /** Ticks from seating to the nest's first look at her (the teleport lands). */
    static final long SIT_TICKS = 5L;

    private final AnimalService animals;
    private final Scheduler scheduler;
    private final NestFinder finder;
    private final PaperNestHens hens;
    private final Supplier<DeviceService> devices;
    private final Supplier<DeviceKind<NestBoxState>> kind;
    private final NestBoxDevice.Services services;

    public NestSeeker(AnimalService animals, Scheduler scheduler, NestFinder finder, PaperNestHens hens,
            Supplier<DeviceService> devices, Supplier<DeviceKind<NestBoxState>> kind, NestBoxDevice.Services services) {
        this.animals = animals;
        this.scheduler = scheduler;
        this.finder = finder;
        this.hens = hens;
        this.devices = devices;
        this.kind = kind;
        this.services = services;
    }

    @Override
    public Set<String> species() {
        return dev.tfp.core.animal.CarrierTable.get().all().stream().filter(c -> c.livestock().isPresent())
            .map(Carrier::id)
            .filter(id -> ProductPdc.specOf(id).map(s -> s.kind() == ProductKind.EGG).orElse(false))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean keepVanillaGoals() {
        return true;
    }

    @Override
    public void onCheck(Check c) {
        Entity hen = c.entity();
        ClockSnapshot now = services.clock().get();
        if (!now.isSet() || hens.holds(hen.getUniqueId())) {
            return;
        }
        Optional<ProductSpec> spec = ProductPdc.specOf(c.record().species());
        Optional<LivestockSpecies> species = animals.table().find(c.record().species()).flatMap(Carrier::livestock);
        if (spec.isEmpty() || species.isEmpty()
            || !ProductRules.isReady(spec.get(), species.get(), c.record().state(), ProductPdc.read(hen), now.playerTicks())) {
            return;
        }
        var at = hen.getLocation();
        Optional<BlockPos> nest = finder.nearest(hen.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), RADIUS, VERTICAL);
        if (nest.isEmpty()) {
            return;
        }
        World world = hen.getWorld();
        BlockPos p = nest.get();
        scheduler.region(world, p.x() >> 4, p.z() >> 4, () -> seat(hen, world.getBlockAt(p.x(), p.y(), p.z())));
    }

    /** On the nest's region thread: sit the hen if the nest is free and has a slot. */
    void seat(Entity hen, Block nest) {
        DeviceService service = devices.get();
        boolean[] seated = {false};
        boolean[] open = {true};
        Optional<?> stored = service.edit(nest, kind.get(), (record, now) -> {
            var next = NestBoxDevice.process(record, now, nest, service.stacks(), services);
            open[0] = NestBoxRules.hasOpenSlot(NestBoxDevice.slots(next, service.stacks(), services));
            if (next.state().occupied() || !open[0]) {
                return next;
            }
            seated[0] = true;
            return next.withState(next.state().seat(hen.getUniqueId().toString(), now));
        });
        BlockPos at = new BlockPos(nest.getX(), nest.getY(), nest.getZ());
        if (stored.isEmpty()) {
            finder.remove(nest.getWorld().getUID(), at); // not a nest box any more
        } else {
            finder.markOpen(nest.getWorld().getUID(), at, open[0]); // PAR-FAUNA-27: a full nest no longer holds the hen back
        }
        if (seated[0]) {
            hens.sit(hen, nest);
            services.effects().wake(nest, SIT_TICKS); // she needs a moment to arrive; the nest takes it from there
        }
    }
}
