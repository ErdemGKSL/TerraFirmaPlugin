/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A {@link GrowthWorld} in a map for the pure rule tests: unset cells are air. */
final class MapWorld implements GrowthWorld {
    private record Cell(String kind, BlockStateRef state, Set<String> tags) {
    }

    private final Map<List<Integer>, Cell> cells = new HashMap<>();
    private final Map<String, Set<String>> tagIds = new HashMap<>();
    final List<List<Integer>> written = new java.util.ArrayList<>();

    private static List<Integer> key(int x, int y, int z) {
        return List.of(x, y, z);
    }

    /** A full solid block in the given tags. */
    MapWorld solid(int x, int y, int z, String... tags) {
        cells.put(key(x, y, z), new Cell("solid", null, Set.of(tags)));
        return this;
    }

    MapWorld addWater(int x, int y, int z) {
        cells.put(key(x, y, z), new Cell("water", null, Set.of()));
        return this;
    }

    MapWorld custom(int x, int y, int z, BlockStateRef state) {
        cells.put(key(x, y, z), new Cell("custom", state, Set.of()));
        return this;
    }

    MapWorld custom(int x, int y, int z, String id) {
        return custom(x, y, z, BlockStateRef.parse(id));
    }

    /** Custom block ids that are in a tag. */
    MapWorld tag(String tag, String... ids) {
        tagIds.computeIfAbsent(tag, t -> new HashSet<>()).addAll(List.of(ids));
        return this;
    }

    /** A filled box of solid blocks. */
    MapWorld floor(int x0, int z0, int x1, int z1, int y, String... tags) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                solid(x, y, z, tags);
            }
        }
        return this;
    }

    MapWorld waterBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    addWater(x, y, z);
                }
            }
        }
        return this;
    }

    int count(String idPrefix) {
        return (int) cells.values().stream().filter(c -> c.state() != null && c.state().id().startsWith(idPrefix)).count();
    }

    @Override
    public Optional<BlockStateRef> custom(int x, int y, int z) {
        Cell c = cells.get(key(x, y, z));
        return c == null ? Optional.empty() : Optional.ofNullable(c.state());
    }

    @Override
    public boolean air(int x, int y, int z) {
        return !cells.containsKey(key(x, y, z));
    }

    @Override
    public boolean water(int x, int y, int z) {
        Cell c = cells.get(key(x, y, z));
        return c != null && c.kind().equals("water");
    }

    @Override
    public boolean sturdy(int x, int y, int z, Face face) {
        Cell c = cells.get(key(x, y, z));
        return c != null && c.kind().equals("solid");
    }

    @Override
    public boolean tagged(int x, int y, int z, String tag) {
        Cell c = cells.get(key(x, y, z));
        if (c == null) {
            return false;
        }
        return c.tags().contains(tag) || c.state() != null && tagIds.getOrDefault(tag, Set.of()).contains(c.state().id());
    }

    @Override
    public boolean is(int x, int y, int z, String key) {
        Cell c = cells.get(key(x, y, z));
        return c != null && c.tags().contains(key);
    }

    @Override
    public int minY() {
        return 0;
    }

    @Override
    public int maxY() {
        return 256;
    }

    @Override
    public boolean set(int x, int y, int z, BlockStateRef state) {
        cells.put(key(x, y, z), new Cell("custom", state, Set.of()));
        written.add(key(x, y, z));
        return true;
    }
}
