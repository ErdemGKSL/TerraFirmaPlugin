/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/RottenFunction.java @ c3b4d9536
 * (also CopyFluidFunction.java and ApplyStackSizeFunction.java of the same package)
 */
package dev.tfp.paper.loot;

import java.util.List;
import java.util.Objects;

/**
 * A loot function with its own conditions (vanilla's {@code LootItemConditionalFunction}: when the conditions fail the stack
 * passes unchanged). Count functions run in the evaluator; the item-data functions become {@link StackOp}s on the drop and
 * are applied to the real stack on the region thread ({@link LootStackHooks}).
 */
public sealed interface LootFunction {

    List<LootCondition> conditions();

    /** {@code minecraft:set_count}: {@code count = (add ? count : 0) + value.getInt()}. */
    record SetCount(LootNumber count, boolean add, List<LootCondition> conditions) implements LootFunction {
        public SetCount {
            Objects.requireNonNull(count, "count");
            conditions = List.copyOf(conditions);
        }
    }

    /** {@code minecraft:explosion_decay}: with a radius, each item survives when {@code nextFloat() <= 1 / radius}. */
    record ExplosionDecay(List<LootCondition> conditions) implements LootFunction {
        public ExplosionDecay {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * {@code minecraft:limit_count}: clamps the count into {@code [min, max]} (either bound open), min evaluated first.
     * With both bounds this is vanilla's {@code Mth.clamp(v, min, max)} = {@code Math.min(Math.max(v, min), max)}, so
     * {@code max} wins when {@code min > max}.
     */
    record LimitCount(LootNumber min, LootNumber max, List<LootCondition> conditions) implements LootFunction {
        public LimitCount {
            conditions = List.copyOf(conditions);
        }
    }

    /** A function that changes the stack's data, not its count: kept as an op on the drop. */
    record Data(StackOp op, List<LootCondition> conditions) implements LootFunction {
        public Data {
            Objects.requireNonNull(op, "op");
            conditions = List.copyOf(conditions);
        }
    }

    /** The item-data functions. */
    sealed interface StackOp {
        /** {@code minecraft:copy_components} from {@code source} (TFC: {@code block_entity}), only the {@code include} ids. */
        record CopyComponents(String source, List<String> include) implements StackOp {
            public CopyComponents {
                Objects.requireNonNull(source, "source");
                include = List.copyOf(include);
            }
        }

        /** {@code tfc:copy_fluid}: fill the item with the block entity's fluid. */
        record CopyFluid() implements StackOp {}

        /** {@code tfc:rotten}: {@code FoodCapability.setRotten}. */
        record Rotten() implements StackOp {}

        /** {@code tfc:apply_stack_size}: {@code ItemSizeManager.get(stack).modifyWeight(stack)}. */
        record ApplyStackSize() implements StackOp {}
    }
}
