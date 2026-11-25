/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (serverTick: the egg sound,
 *       Helpers.spawnItem, the hatch)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.animal.Carrier;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** PAR-FAUNA-07: the nest box's world effects on Paper. */
public final class PaperNestEffects implements NestEffects {
    private final Scheduler scheduler;
    private final Supplier<DeviceService> devices;
    private final AnimalService animals;
    private final NestFinder finder;
    private final Logger logger;

    public PaperNestEffects(Scheduler scheduler, Supplier<DeviceService> devices, AnimalService animals, NestFinder finder,
            Logger logger) {
        this.scheduler = scheduler;
        this.devices = devices;
        this.animals = animals;
        this.finder = finder;
        this.logger = logger;
    }

    @Override
    public void laidSound(Block nest) {
        nest.getWorld().playSound(PaperNestHens.center(nest), Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
    }

    @Override
    public void dropEgg(Block nest, ItemStack egg) {
        nest.getWorld().dropItemNaturally(PaperNestHens.center(nest).add(0, 0.3, 0), egg);
    }

    @Override
    public void hatch(Block nest, String species, float familiarity) {
        Optional<Carrier> carrier = animals.table().find(species);
        if (carrier.isEmpty()) {
            logger.warning("nest box: no carrier for the egg species " + species);
            return;
        }
        Location at = PaperNestHens.center(nest);
        animals.spawnOffspring(at, carrier.get(), familiarity).exceptionally(ex -> {
            logger.log(Level.WARNING, "nest box: the chick did not hatch", ex);
            return null;
        });
    }

    @Override
    public void wake(Block nest, long delayTicks) {
        scheduler.regionDelayed(nest.getWorld(), nest.getX() >> 4, nest.getZ() >> 4, Math.max(1L, delayTicks),
            () -> devices.get().neighbourChanged(nest));
    }

    @Override
    public void placed(Block nest) {
        finder.add(nest.getWorld().getUID(), new BlockPos(nest.getX(), nest.getY(), nest.getZ()));
    }

    @Override
    public void removed(Block nest) {
        finder.remove(nest.getWorld().getUID(), new BlockPos(nest.getX(), nest.getY(), nest.getZ()));
    }
}
