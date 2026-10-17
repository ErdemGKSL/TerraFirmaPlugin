/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

/**
 * The state of one block a plant or coral feature places: the block id in TFC's naming with our namespace
 * ({@code tfp:plant/allium}, {@code tfp:wild_crop/wheat}, {@code minecraft:tube_coral_block}), what it is, its half, age,
 * fluid and one kind-specific value. Immutable.
 *
 * @param block the block id
 * @param kind  what the block is
 * @param type  the plant's block type for {@link Kind#PLANT}, else null
 * @param part  the half of a two-block plant or crop, or {@link Part#SINGLE}
 * @param age   the {@code age} property (0..3) of a plant that has one, else -1
 * @param fluid the fluid the block holds (a water plant filled with the water where it stands)
 * @param extra the sea pickle count (1..4), the horizontal facing of a wall fan (0 north, 1 east, 2 south, 3 west), the
 *              direction of a wild spreading crop's fruit (same order); -1 for the others
 */
public record PlantState(String block, Kind kind, PlantType type, Part part, int age, Fluid fluid, int extra) {
    /** Our namespace, which datagen gives the TFC ids. */
    public static final String NS = "tfp:";

    /** What a placed block is. */
    public enum Kind {
        /** A TFC plant ({@code tfp:plant/<name>}). */
        PLANT,
        /** A wild crop ({@code tfp:wild_crop/<crop>}). */
        WILD_CROP,
        /** The fruit block next to a wild spreading crop ({@code tfp:pumpkin}, {@code tfp:melon}). */
        WILD_CROP_FRUIT,
        /** A vanilla coral block ({@code minecraft:<colour>_coral_block}). */
        CORAL_BLOCK,
        /** A TFC coral plant ({@code tfp:coral/<colour>_coral}). */
        CORAL,
        /** A TFC coral fan ({@code tfp:coral/<colour>_coral_fan}). */
        CORAL_FAN,
        /** A TFC coral wall fan ({@code tfp:coral/<colour>_coral_wall_fan}), facing {@link #extra}. */
        CORAL_WALL_FAN,
        /** TFC's sea pickle ({@code tfp:sea_pickle}), {@link #extra} pickles. */
        SEA_PICKLE
    }

    /** The half of a two-block plant ({@code part} lower/upper, a double crop's bottom/top). */
    public enum Part { SINGLE, LOWER, UPPER }

    /** The fluid of a position or a fluid-logged block: none, fresh water ({@code minecraft:water}), TFC's salt water. */
    public enum Fluid { EMPTY, WATER, SALT_WATER }

    public PlantState {
        if (kind == Kind.PLANT && type == null) {
            throw new IllegalArgumentException("a plant needs its block type: " + block);
        }
        if (age < -1 || age > 3) {
            throw new IllegalArgumentException("age must be in [-1, 3]: " + age);
        }
    }

    /** A plant or crop in its default state: single, no age, no fluid. */
    static PlantState of(String block, Kind kind, PlantType type) {
        return new PlantState(block, kind, type, Part.SINGLE, -1, Fluid.EMPTY, -1);
    }

    PlantState withPart(Part part) {
        return new PlantState(block, kind, type, part, age, fluid, extra);
    }

    PlantState withAge(int age) {
        return new PlantState(block, kind, type, part, age, fluid, extra);
    }

    PlantState withFluid(Fluid fluid) {
        return new PlantState(block, kind, type, part, age, fluid, extra);
    }

    PlantState withExtra(int extra) {
        return new PlantState(block, kind, type, part, age, fluid, extra);
    }

    /** Whether the block is one of the corals ({@code minecraft:corals}: plants and fans, not wall fans or coral blocks). */
    public boolean isCoral() {
        return kind == Kind.CORAL || kind == Kind.CORAL_FAN;
    }
}
