/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A condition on a loot entry, the subset of Minecraft loot conditions that TFC's plant, leaf, twig and fallen-leaf tables use:
 * {@code match_tool}, {@code random_chance}, {@code block_state_property}, {@code any_of}, {@code all_of} and {@code inverted}.
 * {@code survives_explosion} is not represented because it only applies to explosions.
 */
public sealed interface HarvestCondition {

    /** {@code match_tool}: the held tool has the tag. */
    record HasTool(ToolTag tag) implements HarvestCondition {
        public HasTool {
            Objects.requireNonNull(tag, "tag");
        }
    }

    /** {@code random_chance}: passes when the next roll is below {@code chance}. Consumes one roll. */
    record Chance(double chance) implements HarvestCondition {
        public Chance {
            if (!(chance >= 0.0 && chance <= 1.0)) {
                throw new IllegalArgumentException("chance must be within [0, 1], was " + chance);
            }
        }
    }

    /** {@code block_state_property}: every listed property of the block state has the listed value. */
    record StateIs(Map<String, String> properties) implements HarvestCondition {
        public StateIs {
            properties = Map.copyOf(properties);
            if (properties.isEmpty()) {
                throw new IllegalArgumentException("a state condition needs at least one property");
            }
        }
    }

    /** {@code any_of}: passes when one of the terms passes; terms are tried in order. */
    record AnyOf(List<HarvestCondition> terms) implements HarvestCondition {
        public AnyOf {
            terms = List.copyOf(terms);
        }
    }

    /** {@code all_of}: passes when every term passes; terms are tried in order. */
    record AllOf(List<HarvestCondition> terms) implements HarvestCondition {
        public AllOf {
            terms = List.copyOf(terms);
        }
    }

    /** {@code inverted}: passes when the term does not. */
    record Not(HarvestCondition term) implements HarvestCondition {
        public Not {
            Objects.requireNonNull(term, "term");
        }
    }
}
