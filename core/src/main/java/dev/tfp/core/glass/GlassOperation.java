/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassOperation.java @ c3b4d9536 (the operation registry, the
 * powder map and the minimum temperature of each operation)
 */
package dev.tfp.core.glass;

import dev.tfp.core.heat.HeatLevel;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One glassworking operation: a blowpipe action, a tool action, a powder added to the batch, or a pour. Ids are TFC's registry
 * keys ({@code tfc:blow}, {@code tfc:copper}, ...). Each operation has a minimum temperature; a step is only applied to a hot
 * enough blowpipe. Powders have a set of item ids that add them to the batch.
 */
public enum GlassOperation {
    BLOW("tfc:blow", false, minHot(), "tfc:blowpipe_with_glass", "tfc:ceramic_blowpipe_with_glass"),
    ROLL("tfc:roll", false, minHot(), "tfc:wool_cloth"),
    STRETCH("tfc:stretch", false, minHot(), "tfc:blowpipe_with_glass", "tfc:ceramic_blowpipe_with_glass"),
    PINCH("tfc:pinch", false, minHot(), "tfc:jacks"),
    FLATTEN("tfc:flatten", false, minHot(), "tfc:paddle"),
    /** Sawing has no temperature requirement in TFC. */
    SAW("tfc:saw", false, 0f, "tfc:gem_saw"),

    SODA_ASH("tfc:soda_ash", true, minHot(), "tfc:powder/soda_ash"),
    AMETHYST("tfc:amethyst", true, minHot(), "tfc:powder/amethyst"),
    SULFUR("tfc:sulfur", true, minHot(), "tfc:powder/sulfur"),
    IRON("tfc:iron", true, minHot(), "tfc:powder/hematite", "tfc:powder/limonite", "tfc:powder/magnetite"),
    RUBY("tfc:ruby", true, minHot(), "tfc:powder/ruby"),
    LAPIS_LAZULI("tfc:lapis_lazuli", true, minHot(), "tfc:powder/lapis_lazuli"),
    PYRITE("tfc:pyrite", true, minHot(), "tfc:powder/pyrite"),
    SAPPHIRE("tfc:sapphire", true, minHot(), "tfc:powder/sapphire"),
    GOLD("tfc:gold", true, minHot(), "tfc:powder/native_gold"),
    GRAPHITE("tfc:graphite", true, minHot(), "tfc:powder/graphite"),
    COPPER("tfc:copper", true, minHot(), "tfc:powder/native_copper", "tfc:powder/malachite", "tfc:powder/tetrahedrite"),
    NICKEL("tfc:nickel", true, minHot(), "tfc:powder/garnierite"),
    TIN("tfc:tin", true, minHot(), "tfc:powder/cassiterite"),
    SILVER("tfc:silver", true, minHot(), "tfc:powder/native_silver"),

    TABLE_POUR("tfc:table_pour", false, minHot(), "tfc:blowpipe_with_glass", "tfc:ceramic_blowpipe_with_glass"),
    BASIN_POUR("tfc:basin_pour", false, minHot(), "tfc:blowpipe_with_glass", "tfc:ceramic_blowpipe_with_glass");

    private static final Map<String, GlassOperation> BY_ID = Stream.of(values())
        .collect(Collectors.toUnmodifiableMap(GlassOperation::id, op -> op));

    private static final Map<String, GlassOperation> BY_POWDER = Stream.of(values())
        .filter(GlassOperation::isPowder)
        .flatMap(op -> op.items.stream().map(item -> Map.entry(item, op)))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final String id;
    private final boolean powder;
    private final float minTemperature;
    private final Set<String> items;

    GlassOperation(String id, boolean powder, float minTemperature, String... items) {
        this.id = id;
        this.powder = powder;
        this.minTemperature = minTemperature;
        this.items = Set.of(items);
    }

    private static float minHot() {
        return HeatLevel.FAINT_RED.min();
    }

    /** The TFC registry id, for example {@code tfc:blow}. */
    public String id() {
        return id;
    }

    /** Whether this operation is a powder that is added to the batch from a powder item. */
    public boolean isPowder() {
        return powder;
    }

    /** The temperature a blowpipe must be above to take this operation (TFC's strict {@code temperature > min}). */
    public float minTemperature() {
        return minTemperature;
    }

    /** The item ids that perform this operation, as TFC lists them. */
    public Set<String> items() {
        return items;
    }

    /** The operation with the given registry id, if any. */
    public static Optional<GlassOperation> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** The powder operation for a powder item id, if any. Non-powder items return empty. */
    public static Optional<GlassOperation> byPowder(String item) {
        return Optional.ofNullable(BY_POWDER.get(item));
    }
}
