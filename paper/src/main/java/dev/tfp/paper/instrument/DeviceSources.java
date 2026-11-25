/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (IHeatable.getTemperature,
 *       IRecipeTimer.getRecipeDuration / getRemainingTime), src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java
 *       @ c3b4d9536 (IHeatable.getTemperature), src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java
 *       @ c3b4d9536 (getTemperature), src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536
 *       (getRecipeDuration, getRemainingTicks), src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java
 *       @ c3b4d9536 (getRecipeDuration, getRemainingTicks)
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.blastfurnace.BlastFurnaceData;
import dev.tfp.core.bloomery.BloomeryData;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firebox.FireboxData;
import dev.tfp.core.firebox.FireboxSim;
import dev.tfp.paper.device.DeviceService;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.block.Block;

/**
 * PAR-CLIM-28: what a thermometer or clock reads from the devices of the plugin. The instrument module asks the device
 * service for the caught-up record of the block behind (the device kinds are the sources, so no device module has to
 * register itself and the load order of the modules does not matter). Firebox (heat, kiln timer), crucible (heat), blast
 * furnace (heat), bloomery (burn timer) and barrel (sealed-recipe timer, PAR-CLIM-29) are wired. Region thread of the
 * instrument.
 */
final class DeviceSources implements InstrumentSources {
    private final DeviceService devices;
    private final Function<DeviceRecord<BarrelData>, Optional<Integer>> barrelDuration;

    /**
     * @param barrelDuration the duration of the sealed recipe that matches the contents of a barrel record (TFC
     *                       {@code SealedBarrelRecipe.getDuration}), empty with no matching recipe
     */
    DeviceSources(DeviceService devices, Function<DeviceRecord<BarrelData>, Optional<Integer>> barrelDuration) {
        this.devices = devices;
        this.barrelDuration = barrelDuration;
    }

    DeviceSources(DeviceService devices) {
        this(devices, r -> Optional.empty());
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
        return record(device).flatMap(r -> r.state() instanceof BarrelData b
            ? Optional.of(barrelTimer(b, barrelDuration.apply(castBarrel(r)), r.lastUpdateTick()))
            : timer(r.state(), r.lastUpdateTick()));
    }

    /** TFC {@code IHeatable.getTemperature} of the state of a device kind; empty for a kind that is not heatable. */
    static Optional<Float> temperature(Object state) {
        if (state instanceof FireboxData f) {
            return Optional.of(f.temperature());
        }
        if (state instanceof CrucibleData c) {
            return Optional.of(c.temperature().temperature());
        }
        if (state instanceof BlastFurnaceData b) {
            return Optional.of(b.state().temperature());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static DeviceRecord<BarrelData> castBarrel(DeviceRecord<?> r) {
        return (DeviceRecord<BarrelData>) r;
    }

    /**
     * TFC {@code BarrelBlockEntity.getRecipeDuration / getRemainingTicks}: the duration of the matching sealed recipe and
     * {@code duration - (now - recipeTick)}. A barrel that is not sealed (our {@code recipeTick} is unset) reads zero.
     */
    static Timer barrelTimer(BarrelData data, Optional<Integer> duration, long now) {
        if (!data.sealed() || data.recipeTick() < 0 || duration.isEmpty()) {
            return new Timer(0, 0);
        }
        return new Timer(duration.get(), duration.get() - (now - data.recipeTick()));
    }

    /** TFC {@code FireboxBlockEntity.getRecipeDuration / getRemainingTime}: the kiln timer while the kiln is heating. */
    static Optional<Timer> timer(Object state, long now) {
        if (state instanceof FireboxData f) {
            boolean heating = FireboxSim.isHeating(f);
            return Optional.of(new Timer(heating ? FireboxSim.timeToHeat(f.heatingCount(), f.temperature()) : 0,
                heating ? FireboxSim.timeLeft(f, now) : 0));
        }
        if (state instanceof BloomeryData b) {
            // TFC getRecipeDuration / getRemainingTicks; the duration is the one stored when it was lit
            return Optional.of(b.lit() ? new Timer(b.duration(), b.duration() - (now - b.litTick())) : new Timer(0, 0));
        }
        return Optional.empty();
    }
}
