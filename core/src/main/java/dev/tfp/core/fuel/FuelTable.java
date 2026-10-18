/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tfc/fuel/*.json @ c3b4d9536 (duration, temperature, purity)
 */
package dev.tfp.core.fuel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The fuel table of TFC's {@code fuel/} data at c3b4d9536 (50 files). Each entry is keyed by the data file path under
 * {@code fuel/}, for instance {@code oak_logs} or {@code groundcover/pinecone}. The item or tag each file matches is data
 * glue (the ingredient), and lives with the item data, not here: this table has only the values TFC burns with.
 */
public final class FuelTable {
    private FuelTable() {
    }

    private static Fuel entry(String id, int duration, float temperature, float purity) {
        return new Fuel(id, duration, temperature, purity);
    }

    /** Every fuel, in TFC's data file order. */
    public static final List<Fuel> ALL = List.of(
        entry("acacia_logs", 1000, 650.0f, 0.95f),
        entry("acacia_planks", 400, 700.0f, 0.95f),
        entry("ash_logs", 1250, 696.0f, 0.95f),
        entry("ash_planks", 500, 746.0f, 0.95f),
        entry("aspen_logs", 1000, 611.0f, 0.95f),
        entry("aspen_planks", 400, 661.0f, 0.95f),
        entry("birch_logs", 1750, 652.0f, 0.95f),
        entry("birch_planks", 700, 702.0f, 0.95f),
        entry("blackwood_logs", 1750, 720.0f, 0.95f),
        entry("blackwood_planks", 700, 770.0f, 0.95f),
        entry("charcoal", 1800, 1350.0f, 1.0f),
        entry("chestnut_logs", 1500, 651.0f, 0.95f),
        entry("chestnut_planks", 600, 701.0f, 0.95f),
        entry("coal", 2000, 1415.0f, 1.0f),
        entry("douglas_fir_logs", 1500, 707.0f, 1.0f),
        entry("douglas_fir_planks", 600, 757.0f, 1.0f),
        entry("dried_cactus_wood", 603, 1000.0f, 0.95f),
        entry("groundcover/driftwood", 400, 650.0f, 0.4f),
        entry("groundcover/pinecone", 220, 150.0f, 0.15f),
        entry("hickory_logs", 2000, 762.0f, 0.8f),
        entry("hickory_planks", 800, 812.0f, 0.8f),
        entry("kapok_logs", 1000, 645.0f, 0.95f),
        entry("kapok_planks", 400, 695.0f, 0.95f),
        entry("leaves", 600, 100.0f, 0.25f),
        entry("mangrove_logs", 1000, 655.0f, 0.95f),
        entry("mangrove_planks", 400, 705.0f, 0.95f),
        entry("maple_logs", 2000, 745.0f, 0.95f),
        entry("maple_planks", 800, 795.0f, 0.95f),
        entry("oak_logs", 2250, 728.0f, 1.0f),
        entry("oak_planks", 900, 778.0f, 1.0f),
        entry("ore/lignite", 2200, 1350.0f, 1.0f),
        entry("palm_logs", 1250, 730.0f, 0.95f),
        entry("palm_planks", 500, 780.0f, 0.95f),
        entry("paper", 150, 199.0f, 0.7f),
        entry("peat", 2500, 600.0f, 1.0f),
        entry("pine_logs", 1250, 627.0f, 0.6f),
        entry("pine_planks", 500, 677.0f, 0.6f),
        entry("rosewood_logs", 1500, 640.0f, 0.95f),
        entry("rosewood_planks", 600, 690.0f, 0.95f),
        entry("sequoia_logs", 1750, 612.0f, 0.7f),
        entry("sequoia_planks", 700, 662.0f, 0.7f),
        entry("spruce_logs", 1500, 608.0f, 0.6f),
        entry("spruce_planks", 600, 658.0f, 0.6f),
        entry("stick_bundle", 600, 900.0f, 0.8f),
        entry("sycamore_logs", 1750, 653.0f, 0.95f),
        entry("sycamore_planks", 700, 703.0f, 0.95f),
        entry("white_cedar_logs", 1500, 625.0f, 0.95f),
        entry("white_cedar_planks", 600, 675.0f, 0.95f),
        entry("willow_logs", 1000, 603.0f, 0.95f),
        entry("willow_planks", 400, 653.0f, 0.95f)
    );

    private static final Map<String, Fuel> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Fuel::id, Function.identity()));

    /** The fuel from the data file {@code id} (for instance {@code oak_logs}), if it exists. */
    public static Optional<Fuel> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
