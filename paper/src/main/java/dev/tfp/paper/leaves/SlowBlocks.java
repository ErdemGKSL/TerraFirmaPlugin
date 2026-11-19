/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ISlowEntities.java @ c3b4d9536
 */
package dev.tfp.paper.leaves;

import dev.tfp.core.leaves.LeafRules;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TFC's {@code ISlowEntities} as a service: blocks that slow the entities inside them. Providers answer for the block ids they
 * know (leaves and fallen leaves here, plants and thatch from their own items) and the slowest answer wins, as in TFC's
 * {@code slowEntityInsideBlocks}. Register in {@code enable}; read from the region thread of the entity.
 */
public final class SlowBlocks {
    /** A source of slow factors. */
    @FunctionalInterface
    public interface Provider {
        /** The factor in {@code [0, 1]} for the state (lower is slower), empty when the block does not slow. Any thread. */
        OptionalDouble factor(BlockStateRef state);
    }

    private final List<Provider> providers = new CopyOnWriteArrayList<>();

    public void register(Provider provider) {
        providers.add(provider);
    }

    /** The slowest factor any provider gives, {@link LeafRules#NO_SLOW} when none. */
    public float factor(BlockStateRef state) {
        float factor = LeafRules.NO_SLOW;
        for (Provider p : providers) {
            OptionalDouble f = p.factor(state);
            if (f.isPresent()) {
                factor = Math.min(factor, (float) f.getAsDouble());
            }
        }
        return factor;
    }

    /** TFC leaves and fallen leaves with TFC's default {@code leavesMovementModifier}. */
    public static Provider leaves(float leavesModifier) {
        return state -> {
            String id = state.id();
            if (id.startsWith("tfp:wood/leaves/")) {
                return OptionalDouble.of(LeafRules.leavesFactor(waterlogged(state.properties()), leavesModifier));
            }
            if (id.startsWith("tfp:wood/fallen_leaves/")) {
                int layers = Optional.ofNullable(state.properties().get("layers")).map(Integer::parseInt).orElse(1);
                return OptionalDouble.of(LeafRules.fallenLeavesFactor(layers, waterlogged(state.properties()), leavesModifier));
            }
            return OptionalDouble.empty();
        };
    }

    private static boolean waterlogged(Map<String, String> properties) {
        String fluid = properties.get("fluid");
        return fluid != null && !fluid.equals("empty");
    }
}
