/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (canCollapse, canStartCollapse,
 * tryTriggerCollapse, collapseBlock), src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java
 * (canFallThrough), src/main/java/net/dries007/tfc/util/Helpers.java (uniqueRandomSample)
 */
package dev.tfp.core.collapse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.tfp.core.world.BlockPos;

/**
 * The pure collapse rules. Mining a block asks {@link #trigger}, which decides from a snapshot and scripted random draws whether a
 * real collapse starts, a fake one shows, or nothing happens. {@link #collapse} gives the rubble of one collapsing block. Nothing
 * here writes blocks or spawns entities; the Paper runner applies the decision on the owning region.
 *
 * <p>Deliberate differences from TFC are listed in the ledger: candidates are taken in sorted order, and the caller checks that
 * the area is loaded and that the level is server side (TFC checks these in {@code tryTriggerCollapse}).
 */
public final class CollapseRules {
    /** TFC {@code collapseTriggerChance} default. */
    public static final double DEFAULT_TRIGGER_CHANCE = 0.1;
    /** TFC {@code collapseFakeTriggerChance} default. */
    public static final double DEFAULT_FAKE_TRIGGER_CHANCE = 0.35;

    /** One block of a collapse: the block at {@code pos} becomes {@code result} and falls; {@code breakBelow} removes the block below first. */
    public record CollapseStep(BlockPos pos, String result, boolean breakBelow) {}

    private final CollapseKinds kinds;
    private final SupportTable supports;
    private final double triggerChance;
    private final double fakeTriggerChance;

    public CollapseRules(CollapseKinds kinds, SupportTable supports, double triggerChance, double fakeTriggerChance) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.supports = Objects.requireNonNull(supports, "supports");
        if (!(triggerChance >= 0 && triggerChance <= 1) || !(fakeTriggerChance >= 0 && fakeTriggerChance <= 1)) {
            throw new IllegalArgumentException("Chances must be in [0, 1]");
        }
        this.triggerChance = triggerChance;
        this.fakeTriggerChance = fakeTriggerChance;
    }

    /** TFC's {@code canCollapse}: the kind has a recipe or is in {@code can_collapse}. */
    public boolean canCollapse(String kind) {
        return kinds.rubble(kind).isPresent();
    }

    /** The rubble yield of one collapsed block of this kind, or empty when the kind does not collapse. */
    public Optional<String> rubble(String kind) {
        return kinds.rubble(kind);
    }

    /**
     * TFC {@code TFCFallingBlockEntity.canFallThrough} for a fall straight down: the block {@code state} does not have a sturdy upper
     * face, the falling block is at least as tough, and the block can be broken.
     */
    public boolean canFallThrough(CollapseCell state, CollapseCell falling) {
        return !state.sturdyUp()
            && kinds.toughness(falling.kind()) >= kinds.toughness(state.kind())
            && !kinds.unbreakable(state.kind());
    }

    /**
     * TFC {@code canStartCollapse}: the block at {@code pos} can start a collapse. It must be a start kind, and either fall into the
     * block below, or the block below is not a full block, or is a non-solid supporter.
     */
    public boolean canStartCollapse(CollapseView view, BlockPos pos) {
        CollapseCell state = view.cellAt(pos);
        if (!kinds.canStart(state.kind())) {
            return false;
        }
        CollapseCell below = view.cellAt(pos.offset(0, -1, 0));
        return canFallThrough(below, state) || !below.fullBlock() || kinds.notSolidSupporting(below.kind());
    }

    /**
     * TFC {@code collapseBlock} for one block: empty when the kind does not collapse. {@code destroyBlockBelow} is passed when a
     * collapse starts here; the block below is broken when the falling block cannot fall through it as bedrock.
     */
    public Optional<CollapseStep> collapse(CollapseView view, BlockPos pos, boolean destroyBlockBelow) {
        CollapseCell state = view.cellAt(pos);
        Optional<String> result = kinds.rubble(state.kind());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        BlockPos below = pos.offset(0, -1, 0);
        CollapseCell bedrock = new CollapseCell(CollapseKinds.BEDROCK, true, true);
        boolean breakBelow = destroyBlockBelow && !canFallThrough(view.cellAt(below), bedrock);
        return Optional.of(new CollapseStep(pos, result.get(), breakBelow));
    }

    /**
     * TFC {@code tryTriggerCollapse} for the block mined at {@code pos}. The random draws happen in TFC's order: the real check, the
     * fake check (only when not real), then the three radius components. The search box is 9 x 5 x 9 at most.
     *
     * <p>A kind that cannot trigger returns {@link TriggerDecision.None} without drawing anything (TFC checks the tag in the caller).
     */
    public TriggerDecision trigger(CollapseView view, BlockPos pos, CollapseRandom random) {
        if (!kinds.canTrigger(view.cellAt(pos).kind())) {
            return new TriggerDecision.None();
        }
        boolean real = random.nextFloat() < triggerChance;
        boolean fake = !real && random.nextFloat() < fakeTriggerChance;
        if (!real && !fake) {
            return new TriggerDecision.None();
        }
        int radX = (random.nextInt(5) + 4) / 2;
        int radY = (random.nextInt(3) + 2) / 2;
        int radZ = (random.nextInt(5) + 4) / 2;

        List<BlockPos> starts = new ArrayList<>();
        BlockPos from = pos.offset(-radX, -radY, -radZ);
        BlockPos to = pos.offset(radX, radY, radZ);
        for (BlockPos candidate : supports.unsupported(view, from, to)) {
            if (candidate.equals(pos) || !canStartCollapse(view, candidate)) {
                continue;
            }
            if (fake) {
                starts.add(candidate);
                continue;
            }
            return new TriggerDecision.Real(candidate);
        }
        if (starts.isEmpty()) {
            return new TriggerDecision.None();
        }
        List<BlockPos> display = starts.size() < 4
            ? starts
            : uniqueRandomSample(new ArrayList<>(starts), Math.min(12, 3 + random.nextInt(starts.size() - 3)), random);
        return new TriggerDecision.Fake(starts, display);
    }

    /** TFC {@code Helpers.uniqueRandomSample}: a partial Fisher-Yates from the end; returns the last {@code n} entries. */
    static List<BlockPos> uniqueRandomSample(List<BlockPos> list, int n, CollapseRandom random) {
        int length = list.size();
        if (length < n) {
            throw new IllegalArgumentException("Cannot select n=" + n + " from a list of size = " + length);
        }
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
        return List.copyOf(list.subList(length - n, length));
    }
}
