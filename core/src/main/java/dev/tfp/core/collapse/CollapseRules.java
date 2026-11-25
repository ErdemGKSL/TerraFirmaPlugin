/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (canCollapse, canStartCollapse,
 * tryTriggerCollapse, startCollapse, collapseBlock), src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java
 * (canFallThrough, canFallInDirection), src/main/java/net/dries007/tfc/util/Helpers.java (uniqueRandomSample),
 * src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java (the collapse propagation in tick)
 */
package dev.tfp.core.collapse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    /** TFC {@code collapsePropagateChance} default. */
    public static final double DEFAULT_PROPAGATE_CHANCE = 0.55;
    /** TFC {@code collapseMinRadius} default. */
    public static final int DEFAULT_MIN_RADIUS = 3;
    /** TFC {@code collapseRadiusVariance} default. */
    public static final int DEFAULT_RADIUS_VARIANCE = 16;
    /** {@link #start} scans the layers from 4 below to 4 above the centre. */
    public static final int START_BELOW = 4;
    public static final int START_ABOVE = 4;
    /** TFC's {@code WorldTracker.tick} advances the collapses in progress when {@code random.nextInt(10) == 0}. */
    public static final int PROPAGATE_ONE_IN = 10;

    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::x)
        .thenComparingInt(BlockPos::y)
        .thenComparingInt(BlockPos::z);

    /**
     * TFC {@code Collapse}: a collapse in progress. {@code next} are the positions checked on the next propagation (above the
     * blocks that fell last time), {@code radiusSquared} the reach, which shrinks by 0.8 every step.
     */
    public record InProgress(BlockPos center, List<BlockPos> next, double radiusSquared) {
        public InProgress {
            Objects.requireNonNull(center, "center");
            next = List.copyOf(next);
        }
    }

    /** What one phase of a collapse decided: the blocks that fall now, and the collapse to continue later (if any). */
    public record Phase(List<CollapseStep> steps, Optional<InProgress> followUp) {
        public Phase {
            steps = List.copyOf(steps);
            Objects.requireNonNull(followUp, "followUp");
        }
    }

    /** One block of a collapse: the block at {@code pos} becomes {@code result} and falls; {@code breakBelow} removes the block below first. */
    public record CollapseStep(BlockPos pos, String result, boolean breakBelow) {}

    private final CollapseKinds kinds;
    private final SupportTable supports;
    private final double triggerChance;
    private final double fakeTriggerChance;
    private final double propagateChance;
    private final int minRadius;
    private final int radiusVariance;

    public CollapseRules(CollapseKinds kinds, SupportTable supports, double triggerChance, double fakeTriggerChance) {
        this(kinds, supports, triggerChance, fakeTriggerChance, DEFAULT_PROPAGATE_CHANCE, DEFAULT_MIN_RADIUS,
            DEFAULT_RADIUS_VARIANCE);
    }

    /** All of TFC's mining-collapse options ({@code collapse*} in the server config). */
    public CollapseRules(CollapseKinds kinds, SupportTable supports, double triggerChance, double fakeTriggerChance,
                         double propagateChance, int minRadius, int radiusVariance) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.supports = Objects.requireNonNull(supports, "supports");
        if (!(triggerChance >= 0 && triggerChance <= 1) || !(fakeTriggerChance >= 0 && fakeTriggerChance <= 1)
            || !(propagateChance >= 0 && propagateChance <= 1)) {
            throw new IllegalArgumentException("Chances must be in [0, 1]");
        }
        if (minRadius < 1 || radiusVariance < 1) {
            throw new IllegalArgumentException("Radius and variance must be at least 1");
        }
        this.triggerChance = triggerChance;
        this.fakeTriggerChance = fakeTriggerChance;
        this.propagateChance = propagateChance;
        this.minRadius = minRadius;
        this.radiusVariance = radiusVariance;
    }

    /** The largest radius {@link #drawRadius} can return. */
    public int maxRadius() {
        return minRadius + radiusVariance - 1;
    }

    public SupportTable supports() {
        return supports;
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

    /** TFC {@code startCollapse}'s first draw: {@code collapseMinRadius + nextInt(collapseRadiusVariance)}. */
    public int drawRadius(CollapseRandom random) {
        return minRadius + random.nextInt(radiusVariance);
    }

    /**
     * TFC {@code startCollapse} after its radius draw. Every column of the square of {@code radius} around {@code center} is scanned
     * upwards from 4 below to 4 above; the first collapsible block above a non-full block that is closer than the radius falls with
     * chance {@code collapsePropagateChance}, breaking the block below when it cannot fall through it, and the column stops. Columns
     * go in {@code BlockPos.betweenClosed} order (x fastest, then z). Supports are ignored, as in TFC.
     *
     * <p>Columns do not read each other's blocks, so deciding all of them on one snapshot gives the same result as TFC's writes in
     * place. The follow-up holds the positions above the fallen blocks and {@code radius * radius}.
     */
    public Phase start(CollapseView view, BlockPos center, int radius, CollapseRandom random) {
        int radiusSquared = radius * radius;
        List<CollapseStep> steps = new ArrayList<>();
        List<BlockPos> secondary = new ArrayList<>();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                boolean foundEmpty = false;
                for (int y = 0; y <= START_BELOW + START_ABOVE; y++) {
                    BlockPos at = new BlockPos(x, center.y() - START_BELOW + y, z);
                    CollapseCell state = view.cellAt(at);
                    if (foundEmpty && canCollapse(state.kind())
                        && distSqr(at, center) < radiusSquared && random.nextFloat() < propagateChance) {
                        Optional<CollapseStep> step = collapse(view, at, true);
                        if (step.isPresent()) {
                            steps.add(step.get());
                            secondary.add(at.offset(0, 1, 0));
                            break;
                        }
                    }
                    foundEmpty = !state.fullBlock();
                }
            }
        }
        Optional<InProgress> followUp = secondary.isEmpty()
            ? Optional.empty()
            : Optional.of(new InProgress(center, secondary, radiusSquared));
        return new Phase(steps, followUp);
    }

    /**
     * One step of TFC's {@code WorldTracker.tick} for a collapse in progress: each next position that can collapse, can fall into
     * the block below, is closer than the radius and passes {@code collapsePropagateChance} falls (without breaking the block
     * below). The positions above the fallen blocks are next, with the radius squared times 0.8; none left ends the collapse.
     *
     * <p>TFC collects the next positions in a hash set; they are sorted here (x, y, z) so that the draws are reproducible.
     */
    public Phase propagate(CollapseView view, InProgress collapse, CollapseRandom random) {
        List<CollapseStep> steps = new ArrayList<>();
        LinkedHashSet<BlockPos> updated = new LinkedHashSet<>();
        for (BlockPos at : collapse.next()) {
            CollapseCell state = view.cellAt(at);
            if (canCollapse(state.kind())
                && canFallThrough(view.cellAt(at.offset(0, -1, 0)), state)
                && distSqr(at, collapse.center()) < collapse.radiusSquared()
                && random.nextFloat() < propagateChance) {
                Optional<CollapseStep> step = collapse(view, at, false);
                if (step.isPresent()) {
                    steps.add(step.get());
                    updated.add(at.offset(0, 1, 0));
                }
            }
        }
        if (updated.isEmpty()) {
            return new Phase(steps, Optional.empty());
        }
        List<BlockPos> next = new ArrayList<>(updated);
        next.sort(ORDER);
        return new Phase(steps, Optional.of(new InProgress(collapse.center(), next, collapse.radiusSquared() * 0.8)));
    }

    /**
     * Ticks until TFC's tracker next advances a collapse: it does so on a tick when {@code nextInt(10) == 0}, so the wait is
     * geometric with mean 10. Drawn tick by tick, so the result is exactly the first tick that passes.
     */
    public static long propagationDelay(CollapseRandom random) {
        long ticks = 1;
        while (random.nextInt(PROPAGATE_ONE_IN) != 0) {
            ticks++;
        }
        return ticks;
    }

    /** Vanilla {@code Vec3i.distSqr}: the squared distance between the two block corners. */
    static double distSqr(BlockPos a, BlockPos b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
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
