/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (IHeatable.getTemperature,
 *       IRecipeTimer.getRecipeDuration / getRemainingTime), src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java
 *       @ c3b4d9536 (IHeatable.getTemperature)
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firebox.FireboxData;
import dev.tfp.core.firebox.FireboxSim;
import dev.tfp.paper.device.DeviceService;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * PAR-CLIM-28: what a thermometer or clock reads from the devices of the plugin. The instrument module asks the device
 * service for the caught-up record of the block behind (the device kinds are the sources, so no device module has to
 * register itself and the load order of the modules does not matter). Firebox (heat, kiln timer) and crucible (heat) are
 * wired; the blast furnace and the bloomery add a case here when their modules exist, the barrel's sealed-recipe timer with
 * its recipe lookup (ledger). Region thread of the instrument.
 */
final class DeviceSources implements InstrumentSources {
    private final DeviceService devices;

    DeviceSources(DeviceService devices) {
        this.devices = devices;
    }

    private Optional<DeviceRecord<?>> record(Block device) {
        return devices.catchUp(device);
    }

    @Override
    public Optional<Float> deviceTemperature(Block device) {
        return record(device).flatMap(r -> temperature(r.state()));
    }

    @Override
    public Optional<Timer> timer(Block device) {
        return record(device).flatMap(r -> timer(r.state(), r.lastUpdateTick()));
    }

    /** TFC {@code IHeatable.getTemperature} of the state of a device kind; empty for a kind that is not heatable. */
    static Optional<Float> temperature(Object state) {
        if (state instanceof FireboxData f) {
            return Optional.of(f.temperature());
        }
        if (state instanceof CrucibleData c) {
            return Optional.of(c.temperature().temperature());
        }
        return Optional.empty();
    }

    /** TFC {@code FireboxBlockEntity.getRecipeDuration / getRemainingTime}: the kiln timer while the kiln is heating. */
    static Optional<Timer> timer(Object state, long now) {
        if (state instanceof FireboxData f) {
            boolean heating = FireboxSim.isHeating(f);
            return Optional.of(new Timer(heating ? FireboxSim.timeToHeat(f.heatingCount(), f.temperature()) : 0,
                heating ? FireboxSim.timeLeft(f, now) : 0));
        }
        return Optional.empty();
    }
}
