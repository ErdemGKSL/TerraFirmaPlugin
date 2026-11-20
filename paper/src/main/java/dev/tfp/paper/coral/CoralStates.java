/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java @ c3b4d9536 (scanForWater reads
 *       the fluid state of each neighbour), src/main/java/net/dries007/tfc/common/blocks/plant/coral/LivingCoralPlantBlock.java
 *       @ c3b4d9536 (tick: deadBlock with fluid empty, facing kept)
 */
package dev.tfp.paper.coral;

import dev.tfp.core.coral.CoralRules;
import dev.tfp.core.coral.CoralRules.Coral;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The coral and sea pickle states in our vocabulary ({@link BlockStateRef}): which states hold water, the facing of a wall
 * fan, the dead state of a living coral. Pure and thread-safe.
 */
public final class CoralStates {
    /** Vanilla blocks whose fluid state is always water (TFC {@code tfc:any_infinite_water} on vanilla water). */
    static final Set<String> ALWAYS_WATER = Set.of("minecraft:water", "minecraft:bubble_column", "minecraft:seagrass",
        "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant");

    /** Every block id the coral rules handle: the 30 corals and the sea pickle. */
    public static final Set<String> IDS = ids();

    private CoralStates() {}

    private static Set<String> ids() {
        final Set<String> s = new HashSet<>();
        CoralRules.all().forEach(c -> s.add(c.id()));
        s.add(CoralRules.SEA_PICKLE);
        return Set.copyOf(s);
    }

    /**
     * Whether the fluid of a state is water (any level: TFC's tag lists flowing water too), or null when unknown. A
     * waterlogged block holds water; a CraftEngine block with TFC's {@code fluid} property holds it unless {@code empty}.
     */
    public static Boolean water(BlockStateRef state) {
        if (state == null) {
            return null;
        }
        if ("true".equals(state.properties().get(CoralRules.WATERLOGGED))) {
            return true;
        }
        final String fluid = state.properties().get("fluid");
        if (fluid != null) {
            return !"empty".equals(fluid);
        }
        return ALWAYS_WATER.contains(state.id());
    }

    /** Whether a state is a living coral. */
    public static boolean living(BlockStateRef state) {
        return state != null && Coral.parse(state.id()).map(c -> c.type().living()).orElse(false);
    }

    /** The facing of a wall fan state (north when absent, TFC's default state). */
    public static Direction facing(BlockStateRef state) {
        final String f = state.properties().get(CoralRules.FACING);
        return f == null ? Direction.NORTH : Direction.valueOf(f.toUpperCase(Locale.ROOT));
    }

    /** The dead state of a living coral: the dead block, not waterlogged, the facing kept. */
    public static Optional<BlockStateRef> dead(BlockStateRef living) {
        return Coral.parse(living.id()).filter(c -> c.type().living()).map(c -> {
            final Coral dead = CoralRules.dieInto(c);
            final Map<String, String> props = new TreeMap<>();
            if (dead.type().wall()) {
                props.put(CoralRules.FACING, facing(living).name().toLowerCase(Locale.ROOT));
            }
            props.put(CoralRules.WATERLOGGED, "false");
            return BlockStateRef.of(dead.id(), props);
        });
    }
}
