/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * PAR-HEAT-03: TFC {@code ForgeEventHandler.onItemExpire}, the cooling of a hot item entity that sits in water, in snow,
 * or on a snow block or ice, replayed lazily. TFC checks a hot item entity every {@code ticksBeforeItemCool} (10) ticks;
 * each check cools it by the surface's amount (on top of the passive cooling of the heat component) and may melt or
 * evaporate the surface with the TFC chance. We replay those checks in one call when someone touches the item entity
 * (pickup, hopper, despawn) from a snapshot of the two blocks the rule reads. Pure: no Bukkit, any thread.
 *
 * <p>The surface is the one at evaluation time for the whole replayed span (a divergence: TFC samples it at each check).
 */
public final class HotItemCooling {
    /** TFC {@code ServerConfig.ticksBeforeItemCool} default: ticks between two cooling checks. */
    public static final int TICKS_BEFORE_ITEM_COOL = 10;
    /** TFC {@code ServerConfig.coolHotItemEntities} default. */
    public static final boolean COOL_HOT_ITEM_ENTITIES = true;

    /** The block the item entity is in, as the rule sees it (TFC checks this one first). */
    public enum At {
        /** A water block itself (TFC {@code isAirOrEmptyFluid}: the fluid's own block), which can evaporate. */
        WATER_BLOCK,
        /** Any other block holding water (waterlogged, kelp, seagrass, bubble column): cools, never evaporates. */
        WATERLOGGED,
        /** A vanilla snow layer ({@code Blocks.SNOW}). */
        SNOW,
        /** Anything else: the block below decides. */
        OTHER
    }

    /** The block under the item entity (read only when {@link At#OTHER}). */
    public enum Below {
        SNOW_BLOCK,
        /** Ice or frosted ice: melts into water. */
        ICE,
        /** TFC sea ice: melts into salt water. Our sea ice is vanilla blue ice (SnowService), so blue ice lands here. */
        SEA_ICE,
        /** Packed ice (TFC also lists blue ice, which is our sea ice). */
        PACKED_ICE,
        OTHER
    }

    /** What the rule reads: the block the item is in (with the snow layer count) and the one below. */
    public record Column(At at, int snowLayers, Below below) {
        public Column {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(below, "below");
        }

        public static Column of(At at, Below below) {
            return new Column(at, 0, below);
        }
    }

    /** The final block a replay leaves at {@code dy} (0 = the item's block, -1 = below), and the block it expects there. */
    public record Change(int dy, Expect expected, Result result, int layers) {
    }

    /** The block a change expects before it is applied (re-validated on the region thread). */
    public enum Expect { WATER_BLOCK, SNOW, SNOW_BLOCK, ICE, SEA_ICE, PACKED_ICE }

    /** The block a change writes. */
    public enum Result { AIR, SNOW, WATER }

    /**
     * The outcome of a replay.
     *
     * @param temperature  the temperature at the last replayed check (the next check starts from it)
     * @param checks       the number of checks replayed ({@code ticksConsumed = checks x 10})
     * @param surfaceCooled whether at least one check took heat off through a surface (for the one sound and smoke)
     * @param zeroAtCheck  the 1-based check at which the item reached 0 °C, or 0 when it did not
     * @param changes      the final block changes, at most one per position
     */
    public record Outcome(float temperature, long checks, boolean surfaceCooled, long zeroAtCheck, List<Change> changes) {
        public Outcome {
            changes = List.copyOf(changes);
        }

        public long ticksConsumed() {
            return checks * TICKS_BEFORE_ITEM_COOL;
        }
    }

    private HotItemCooling() {
    }

    /** TFC's cooling per check for the surface the column gives, 0 when none (water 50, snow 70, snow block 75, ice 100, packed 125). */
    public static float coolAmount(Column column) {
        return switch (column.at()) {
            case WATER_BLOCK, WATERLOGGED -> 50f;
            case SNOW -> 70f;
            case OTHER -> switch (column.below()) {
                case SNOW_BLOCK -> 75f;
                case ICE, SEA_ICE -> 100f;
                case PACKED_ICE -> 125f;
                case OTHER -> 0f;
            };
        };
    }

    /**
     * Replays every whole check in {@code ticks} ticks for an item at {@code temperature} with {@code heatCapacity}.
     * Per check, in TFC order: the passive cooling of the 10 ticks, then the surface's amount (clamped at 0), each
     * surface drawing one random float for its melt or evaporation chance. A check that finds the item at 0 stops the
     * replay (TFC gives the entity its normal lifespan then and checks no more).
     */
    public static Outcome replay(float temperature, float heatCapacity, long ticks, Column column, HeatConfig config,
            RandomGenerator random) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(random, "random");
        long checks = Math.max(0L, ticks / TICKS_BEFORE_ITEM_COOL);
        float passive = heatCapacity > 0f && !Float.isInfinite(heatCapacity)
                ? (float) (TICKS_BEFORE_ITEM_COOL * config.coolingModifier()) / heatCapacity : 0f;

        At at = column.at();
        int layers = column.snowLayers();
        Below below = column.below();
        int atDy = 0; // the item's block: 0, or -1 once it sank into melted ice
        // final state per position, keyed by dy (0 and -1)
        Change[] last = new Change[2];

        float t = Math.max(0f, temperature);
        long done = 0;
        boolean surfaceCooled = false;
        long zeroAt = 0;
        while (done < checks) {
            if (!(t > 0f)) {
                break;
            }
            done++;
            t = Math.max(0f, t - passive);
            float cool = 0f;
            switch (at) {
                case WATER_BLOCK, WATERLOGGED -> {
                    cool = 50f;
                    if (random.nextFloat() < 0.001f && at == At.WATER_BLOCK) {
                        record(last, atDy, Expect.WATER_BLOCK, Result.AIR, 0);
                        at = At.OTHER;
                        below = Below.OTHER; // not in the snapshot (the item would sink further)
                    }
                }
                case SNOW -> {
                    cool = 70f;
                    if (random.nextFloat() < 0.1f) {
                        if (layers > 1) {
                            layers--;
                            record(last, atDy, Expect.SNOW, Result.SNOW, layers);
                        } else {
                            record(last, atDy, Expect.SNOW, Result.AIR, 0);
                            at = At.OTHER;
                        }
                    }
                }
                case OTHER -> {
                    switch (below) {
                        case SNOW_BLOCK -> {
                            cool = 75f;
                            if (random.nextFloat() < 0.1f) {
                                record(last, atDy - 1, Expect.SNOW_BLOCK, Result.AIR, 0);
                                below = Below.OTHER;
                            }
                        }
                        case ICE, SEA_ICE -> {
                            cool = 100f;
                            if (random.nextFloat() < 0.01f) {
                                record(last, atDy - 1, below == Below.ICE ? Expect.ICE : Expect.SEA_ICE, Result.WATER, 0);
                                atDy = -1;
                                at = At.WATER_BLOCK; // the item drops into the new water
                                below = Below.OTHER;
                            }
                        }
                        case PACKED_ICE -> {
                            cool = 125f;
                            if (random.nextFloat() < 0.005f) {
                                record(last, atDy - 1, Expect.PACKED_ICE, Result.WATER, 0);
                                atDy = -1;
                                at = At.WATER_BLOCK;
                                below = Below.OTHER;
                            }
                        }
                        case OTHER -> {
                        }
                    }
                }
            }
            if (cool > 0f) {
                t = Math.max(0f, t - cool);
                surfaceCooled = true;
            }
            if (t == 0f && zeroAt == 0) {
                zeroAt = done;
            }
        }
        List<Change> changes = new ArrayList<>(2);
        for (Change c : last) {
            if (c != null) {
                changes.add(c);
            }
        }
        return new Outcome(t, done, surfaceCooled, zeroAt, changes);
    }

    private static void record(Change[] last, int dy, Expect expected, Result result, int layers) {
        if (dy < -1) {
            return; // below the snapshot; not tracked
        }
        int i = -dy;
        Change prev = last[i];
        // the block the live world held at the start (the first expectation) and the newest result
        last[i] = new Change(dy, prev == null ? expected : prev.expected(), result, layers);
    }

}
