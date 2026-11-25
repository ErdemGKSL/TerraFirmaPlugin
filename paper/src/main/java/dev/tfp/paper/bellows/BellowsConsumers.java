/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IBellowsConsumer.java @ c3b4d9536 (canAcceptAir, intakeAir)
 */
package dev.tfp.paper.bellows;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;

/**
 * TFC {@code IBellowsConsumer} as a registry (PAR-METAL-11): the blocks a bellows can feed, by CraftEngine block id. The
 * charcoal forge registers itself in {@link BellowsModule}; the blast furnace (PAR-METAL-15) registers its own. Thread-safe.
 */
public final class BellowsConsumers {
    /** One kind of block that takes the air of a bellows. */
    public interface Consumer {
        /** TFC {@code canAcceptAir}: whether the block may take air now. Region thread of {@code block}. */
        default boolean canAcceptAir(Block block) {
            return true;
        }

        /** TFC {@code intakeAir}: adds {@code amount} air ticks. Region thread of {@code block}. */
        void intakeAir(Block block, int amount);
    }

    private final Map<String, Consumer> byId = new ConcurrentHashMap<>();

    /** Registers {@code consumer} for the block id; closing removes it. */
    public AutoCloseable register(String blockId, Consumer consumer) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(consumer, "consumer");
        if (byId.putIfAbsent(blockId, consumer) != null) {
            throw new IllegalArgumentException("a bellows consumer is already registered for " + blockId);
        }
        return () -> byId.remove(blockId, consumer);
    }

    public Optional<Consumer> find(String blockId) {
        return Optional.ofNullable(byId.get(blockId));
    }
}
