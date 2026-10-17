/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The server block data of the placeholder rocks, resolved once per CraftEngine state table (M0-CE-01 decision). Pure:
 * the caller passes whether CraftEngine is loaded and a lookup, so the refuse rule runs without a server.
 *
 * <p>The generator must not write vanilla stone in place of a rock it could not resolve, so if CraftEngine has not loaded
 * or any rock id is unknown, the palette is {@linkplain #resolved() refused} and the chunk is not generated.
 *
 * @param <T>        the block data type (Bukkit {@code BlockData} in the generator, anything in tests)
 * @param generation the state-table generation the palette was resolved from
 */
public final class RockPalette<T> {
    private final long generation;
    private final List<T> blocks;
    private final String refusal;

    private RockPalette(long generation, List<T> blocks, String refusal) {
        this.generation = generation;
        this.blocks = blocks;
        this.refusal = refusal;
    }

    /**
     * Resolves {@code ids} in order: the result has one block per id, or a refusal naming what is missing.
     *
     * @param generation the state-table generation the lookup belongs to
     * @param loaded     whether CraftEngine has loaded its configuration
     * @param ids        the custom block ids, in palette order
     * @param lookup     the block data of an id, or empty if it is unknown
     */
    public static <T> RockPalette<T> resolve(long generation, boolean loaded, List<String> ids, Function<String, Optional<T>> lookup) {
        if (!loaded) {
            return new RockPalette<>(generation, null, "CraftEngine has not loaded its configuration");
        }
        List<T> blocks = new ArrayList<>(ids.size());
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            Optional<T> data = lookup.apply(id);
            if (data == null || data.isEmpty()) {
                missing.add(id);
            } else {
                blocks.add(data.get());
            }
        }
        if (!missing.isEmpty()) {
            return new RockPalette<>(generation, null, "unknown custom block(s) " + missing);
        }
        return new RockPalette<>(generation, List.copyOf(blocks), null);
    }

    public long generation() {
        return generation;
    }

    /** True when every rock resolved and the chunk may be generated. */
    public boolean resolved() {
        return blocks != null;
    }

    /** Why the palette is refused, or null when it is resolved. */
    public String refusal() {
        return refusal;
    }

    /** The block data at {@code index} (see {@link dev.tfp.core.worldgen.chunk.PlaceholderRocks#index}). Requires {@link #resolved()}. */
    public T block(int index) {
        if (blocks == null) {
            throw new IllegalStateException("palette refused: " + refusal);
        }
        return blocks.get(index);
    }
}
