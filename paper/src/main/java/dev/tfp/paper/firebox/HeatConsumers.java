/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/IHeatConsumer.java @ c3b4d9536 (setTemperature),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (provideHeatTo)
 */
package dev.tfp.paper.firebox;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;

/**
 * PAR-HEAT-12: TFC's {@code BlockCapabilities.HEAT} ({@code IHeatConsumer}) for blocks that take heat from a heat source
 * below them: a device registers a {@link Consumer} for its block id (or id prefix, the longest wins), and a source (the
 * firebox, later the charcoal forge) hands it the temperatures it gave over a span of ticks
 * ({@code HeatCapability.provideHeatTo}). Provided by {@link FireboxModule}; consumers take it with
 * {@code services().get(HeatConsumers.class)} (the crucible, PAR-METAL-12).
 *
 * <p>Thread-safe registry. {@link Consumer#heat} runs on the region thread that owns the consumer block (the source is the
 * block below, in the same chunk column).
 */
public final class HeatConsumers {
    /** TFC {@code IHeatConsumer.setTemperature}, given once per constant run of ticks. */
    @FunctionalInterface
    public interface Consumer {
        /**
         * Every tick from {@code fromTick} to {@code toTick} (inclusive, player ticks) the source below gave
         * {@code temperature} to {@code block}.
         */
        void heat(Block block, long fromTick, long toTick, float temperature);
    }

    private final Map<String, Consumer> byId = new ConcurrentHashMap<>();

    /** Registers {@code consumer} for {@code blockId} and its sub-paths; closing removes it. */
    public AutoCloseable register(String blockId, Consumer consumer) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(consumer, "consumer");
        if (byId.putIfAbsent(blockId, consumer) != null) {
            throw new IllegalArgumentException("a heat consumer is already registered for " + blockId);
        }
        return () -> byId.remove(blockId, consumer);
    }

    /** The consumer of {@code blockId}: the id itself, then each parent path. */
    public Optional<Consumer> find(String blockId) {
        String id = blockId;
        while (true) {
            Consumer c = byId.get(id);
            if (c != null) {
                return Optional.of(c);
            }
            int slash = id.lastIndexOf('/');
            if (slash <= id.indexOf(':')) {
                return Optional.empty();
            }
            id = id.substring(0, slash);
        }
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
