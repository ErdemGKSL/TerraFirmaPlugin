/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java @ c3b4d9536 (randomTick: the head grows a block),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java @ c3b4d9536 (the body the old head becomes),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the head and body of each column plant)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FARM-25: the head of a growing column ({@code TopPlantBlock}): the weeping vines hang down, the twisting plants and the
 * kelp grow up. Pure decisions; {@link ColumnSweeper} reads and writes the world.
 */
public final class ColumnGrowth {
    /** The head age at which a column stops ({@link PlantGrowth#TOP_MAX_AGE}). */
    public static final int MAX_AGE = PlantGrowth.TOP_MAX_AGE;

    /**
     * A column plant.
     *
     * @param body the body block the old head turns into
     * @param dy   the growth direction: -1 weeping, +1 twisting and kelp
     * @param water whether it grows through water (kelp) instead of air
     */
    public record Kind(String body, int dy, boolean water) {
    }

    /** TFC's column heads ({@code WEEPING_TOP}, {@code TWISTING_TOP}, {@code TWISTING_TOP_SHORT}, {@code TWISTING_SOLID_TOP}, {@code KELP_TOP}). */
    public static final Map<String, Kind> HEADS = heads();

    private ColumnGrowth() {
    }

    private static Map<String, Kind> heads() {
        java.util.LinkedHashMap<String, Kind> m = new java.util.LinkedHashMap<>();
        for (String n : List.of("hanging_vines", "spanish_moss", "liana", "flame_vine")) {
            m.put("tfp:plant/" + n, new Kind("tfp:plant/" + n + "_plant", -1, false));
        }
        for (String n : List.of("arundo", "dry_phragmite", "tree_fern", "cycad")) {
            m.put("tfp:plant/" + n, new Kind("tfp:plant/" + n + "_plant", 1, false));
        }
        for (String n : List.of("winged_kelp", "leafy_kelp")) {
            m.put("tfp:plant/" + n, new Kind("tfp:plant/" + n + "_plant", 1, true));
        }
        return Map.copyOf(m);
    }

    /** The kind of a head block id, if it is one. */
    public static Optional<Kind> kind(String id) {
        return Optional.ofNullable(HEADS.get(id));
    }

    /** The head's {@code age} (0 when missing). */
    public static int age(BlockStateRef head) {
        try {
            return Integer.parseInt(head.properties().getOrDefault("age", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The cells a head at {@code age} can still grow into: TFC stops at age 25. */
    public static int room(int age) {
        return Math.max(0, MAX_AGE - age);
    }

    /**
     * The blocks the column grows over the Poisson random ticks of {@code elapsed} game ticks, within {@code free} free cells
     * ({@link PlantGrowth#lazyTopGrowth} at the long growth chance).
     */
    public static int grown(int age, int free, long elapsed, int randomTickSpeed, RandomGenerator random) {
        return PlantGrowth.lazyTopGrowth(age, Math.min(free, room(age)), elapsed, randomTickSpeed, PlantGrowth.LONG_GROWTH_CHANCE, random);
    }

    /** The body state the old head turns into: the head's {@code fluid} is kept, the age is dropped. */
    public static BlockStateRef body(Kind kind, BlockStateRef head) {
        BlockStateRef body = BlockStateRef.of(kind.body());
        String fluid = head.properties().get("fluid");
        return fluid == null ? body : body.with("fluid", fluid);
    }

    /** The new head: {@code age + grown} ({@code state.cycle(AGE)} per block), the fluid kept. */
    public static BlockStateRef head(BlockStateRef head, int grown) {
        return head.with("age", Integer.toString(Math.min(MAX_AGE, age(head) + grown)));
    }
}
