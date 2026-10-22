/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.persist.DataMap;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * The per-block data of device blocks (PAR-INFRA-02): CraftEngine's block-entity record of a {@code tfp:device} block
 * (M0-CE-02's store; it drops with the block). Implemented by {@code integration/craftengine}; feature code never sees
 * CraftEngine. The maps are copies in both directions.
 *
 * <p>Threads: region thread of the block only.
 */
public interface DeviceStore {
    /** Used while CraftEngine is absent: no device data anywhere. */
    DeviceStore ABSENT = new DeviceStore() {
        @Override
        public Optional<DataMap> read(Block block) {
            return Optional.empty();
        }

        @Override
        public boolean write(Block block, DataMap data) {
            return false;
        }
    };

    /**
     * The stored record map of the device at {@code block}: an empty map when the device has never been written (fresh
     * or generated), {@link Optional#empty()} when there is no device block entity there (vanilla block, a custom block
     * without {@code tfp:device}, or its saved data is unreadable and kept as it is).
     */
    Optional<DataMap> read(Block block);

    /** Replaces the record map and marks the chunk for saving. False when there is no device block entity there. */
    boolean write(Block block, DataMap data);
}
