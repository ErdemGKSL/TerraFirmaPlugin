/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ThermometerBlockEntity.java @ c3b4d9536 (updatePower: the
 *       IHeatable behind an attached thermometer), src/main/java/net/dries007/tfc/common/blockentities/CalendarClockBlockEntity.java
 *       @ c3b4d9536 (serverTick: the IRecipeTimer behind or two behind a clock in timer mode)
 */
package dev.tfp.paper.instrument;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.block.Block;

/**
 * What the thermometer and the calendar clock read from the device they are mounted on (TFC {@code IHeatable} and
 * {@code IRecipeTimer}). The devices (firebox, crucible, blast furnace, barrel, bloomery) register their own source when
 * their module loads; until one does an attached thermometer reads 0 and a clock has no timer mode, as in TFC with nothing
 * behind it. Methods run on the region thread of the instrument's block.
 */
public interface InstrumentSources {
    /** TFC {@code IRecipeTimer}: how long the running recipe lasts and what is left of it, in calendar ticks. */
    record Timer(int durationTicks, long remainingTicks) {
    }

    /** The heat of the device at {@code device} (TFC {@code IHeatable.getTemperature}), empty when it is not heatable. */
    Optional<Float> deviceTemperature(Block device);

    /** The recipe timer of the device at {@code device}, empty when it has none. */
    Optional<Timer> timer(Block device);

    /** Nothing behind the instrument. */
    InstrumentSources NONE = new InstrumentSources() {
        @Override
        public Optional<Float> deviceTemperature(Block device) {
            return Optional.empty();
        }

        @Override
        public Optional<Timer> timer(Block device) {
            return Optional.empty();
        }
    };

    /** The sources of every device module, asked in the order they registered; the first answer wins. */
    final class Registry implements InstrumentSources {
        private final List<InstrumentSources> sources = new CopyOnWriteArrayList<>();

        public void register(InstrumentSources source) {
            sources.add(source);
        }

        @Override
        public Optional<Float> deviceTemperature(Block device) {
            for (InstrumentSources s : sources) {
                Optional<Float> t = s.deviceTemperature(device);
                if (t.isPresent()) {
                    return t;
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Timer> timer(Block device) {
            for (InstrumentSources s : sources) {
                Optional<Timer> t = s.timer(device);
                if (t.isPresent()) {
                    return t;
                }
            }
            return Optional.empty();
        }
    }
}
