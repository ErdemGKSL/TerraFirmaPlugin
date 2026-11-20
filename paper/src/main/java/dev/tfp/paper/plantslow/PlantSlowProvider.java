/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (slowEntityFactor),
 *       src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java @ c3b4d9536 (slowEntityFactor)
 */
package dev.tfp.paper.plantslow;

import dev.tfp.core.leaves.LeafRules;
import dev.tfp.core.plantslow.PlantSlowRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.leaves.SlowBlocks;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The slow factors of TFC's plants ({@code tfp:plant/<name>}, from the exported {@code Plant.speedFactor} table), berry bushes
 * ({@code tfp:berry_bush/<species>}, by stage) and thatch ({@code tfp:thatch}), for {@link SlowBlocks}. Until the table is
 * loaded plants answer nothing. Any thread.
 */
public final class PlantSlowProvider implements SlowBlocks.Provider {
    static final String PLANT = "tfp:plant/";
    static final String BUSH = "tfp:berry_bush/";
    static final String THATCH = "tfp:thatch";

    private final AtomicReference<Map<String, Float>> speeds = new AtomicReference<>(Map.of());

    /** Installs the {@code Plant.speedFactor} table, by plant name. */
    public void load(Map<String, Float> table) {
        speeds.set(Map.copyOf(table));
    }

    @Override
    public OptionalDouble factor(BlockStateRef state) {
        String id = state.id();
        if (id.startsWith(PLANT)) {
            Float speed = speeds.get().get(id.substring(PLANT.length()));
            return speed == null ? OptionalDouble.empty() : OptionalDouble.of(PlantSlowRules.plant(speed));
        }
        if (id.startsWith(BUSH) && !id.equals(BUSH + "dead")) {
            return OptionalDouble.of(PlantSlowRules.seasonalBush(parse(state.properties().get("stage")), LeafRulesModifier.LEAVES));
        }
        if (id.equals(THATCH)) {
            String fluid = state.properties().get("fluid");
            return OptionalDouble.of(PlantSlowRules.thatch(fluid != null && !fluid.equals("empty"), PlantSlowRules.THATCH_MULTIPLIER));
        }
        return OptionalDouble.empty();
    }

    private static int parse(String stage) {
        try {
            return stage == null ? 0 : Math.clamp(Integer.parseInt(stage), 0, PlantSlowRules.BUSH_STAGES - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** TFC {@code leavesMovementModifier} default, as the leaves provider uses. */
    private static final class LeafRulesModifier {
        static final float LEAVES = LeafRules.LEAVES_MOVEMENT_MODIFIER;
    }
}
