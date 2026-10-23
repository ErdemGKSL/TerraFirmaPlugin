/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.neighbour;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable set of {@link ShapeRule}s in registration order (PAR-INFRA-10). Swapped atomically by the runner when a
 * rule is added or removed, so workers always see a consistent set. The first rule that applies to a state wins.
 *
 * @param <S> block state type
 */
public final class ShapeRules<S> {
    private static final ShapeRules<?> EMPTY = new ShapeRules<>(List.of());

    private final List<ShapeRule<S>> rules;
    private final Set<String> blockIds;
    private final boolean anyBlock;

    private ShapeRules(List<ShapeRule<S>> rules) {
        this.rules = List.copyOf(rules);
        Set<String> ids = new HashSet<>();
        boolean any = false;
        for (ShapeRule<S> r : this.rules) {
            if (r.blockIds().isEmpty()) {
                any = true;
            }
            ids.addAll(r.blockIds());
        }
        this.blockIds = Set.copyOf(ids);
        this.anyBlock = any;
    }

    @SuppressWarnings("unchecked")
    public static <S> ShapeRules<S> empty() {
        return (ShapeRules<S>) EMPTY;
    }

    /** A copy with {@code rule} added last; a rule with the same id is refused. */
    public ShapeRules<S> with(ShapeRule<S> rule) {
        Objects.requireNonNull(rule, "rule");
        if (byId(rule.id()).isPresent()) {
            throw new IllegalArgumentException("a shape rule with id " + rule.id() + " is already registered");
        }
        List<ShapeRule<S>> next = new ArrayList<>(rules);
        next.add(rule);
        return new ShapeRules<>(next);
    }

    /** A copy without the rule of that id (the same set if there is none). */
    public ShapeRules<S> without(String id) {
        List<ShapeRule<S>> next = new ArrayList<>(rules);
        if (!next.removeIf(r -> r.id().equals(id))) {
            return this;
        }
        return new ShapeRules<>(next);
    }

    public Optional<ShapeRule<S>> byId(String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** The first rule that applies to {@code state}, if any. */
    public Optional<ShapeRule<S>> ruleFor(S state) {
        if (state == null) {
            return Optional.empty();
        }
        for (ShapeRule<S> r : rules) {
            if (r.appliesTo(state)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Cheap pre-filter: false means no rule can apply to a block with this id. */
    public boolean mayApply(String blockId) {
        return anyBlock || blockIds.contains(blockId);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public List<ShapeRule<S>> rules() {
        return rules;
    }

    /**
     * The pure decision for one position: the new state of the centre of {@code n}, or empty when no rule applies,
     * the centre is unknown, the rule keeps the state, or the decided state equals the current one.
     */
    public Optional<S> decide(Neighbourhood<S> n) {
        S current = n.center();
        Optional<ShapeRule<S>> rule = ruleFor(current);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        S next = rule.get().decide(n);
        if (next == null || next.equals(current)) {
            return Optional.empty();
        }
        return Optional.of(next);
    }
}
