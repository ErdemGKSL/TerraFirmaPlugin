/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java @ c3b4d9536
 */
package dev.tfp.core.falling;

import java.util.OptionalDouble;

/**
 * TFC's falling block rules (PAR-PHYS-01), pure: toughness, {@code canFallThrough}, the on-ground decision of
 * {@code TFCFallingBlockEntity.tick}, the expiry check, and vanilla {@code FallingBlockEntity}'s motion and fall damage
 * that TFC inherits. Any thread.
 */
public final class FallRules {
    /** {@code #tfp:toughness_1..3}: a falling block breaks only blocks of equal or lower toughness. */
    public static final String TOUGHNESS_1 = "tfp:toughness_1";
    public static final String TOUGHNESS_2 = "tfp:toughness_2";
    public static final String TOUGHNESS_3 = "tfp:toughness_3";
    /** TFC's fake toughness of bedrock, used to ask "can anything fall through this at all". */
    public static final int BEDROCK_TOUGHNESS = 4;

    /** {@code CollapseRecipe}: {@code new TFCFallingBlockEntity(..., 2.0f, 20)}. */
    public static final float COLLAPSE_DAMAGE_PER_BLOCK = 2.0f;
    public static final int COLLAPSE_MAX_DAMAGE = 20;
    /** {@code LandslideRecipe}: {@code new TFCFallingBlockEntity(..., 0.8f, 10)}. */
    public static final float LANDSLIDE_DAMAGE_PER_BLOCK = 0.8f;
    public static final int LANDSLIDE_MAX_DAMAGE = 10;

    /** Vanilla {@code FallingBlockEntity.getDefaultGravity}. */
    public static final double GRAVITY = 0.04;
    /** Vanilla's per-tick velocity scale after the move. */
    public static final double DRAG = 0.98;
    /** TFC (and vanilla): dropped as an item after this many ticks in the air. */
    public static final int MAX_AIR_TIME = 600;
    /** TFC (and vanilla): dropped after this many ticks when outside the build height. */
    public static final int OUT_OF_WORLD_TIME = 100;

    private FallRules() {}

    /** TFC {@code getBlockToughness}: bedrock 4, else the highest {@code toughness_N} tag, else 0. */
    public static int toughness(boolean bedrock, boolean toughness3, boolean toughness2, boolean toughness1) {
        if (bedrock) {
            return BEDROCK_TOUGHNESS;
        }
        if (toughness3) {
            return 3;
        }
        if (toughness2) {
            return 2;
        }
        return toughness1 ? 1 : 0;
    }

    /**
     * TFC {@code canFallThrough(level, pos, state, fallingDirection, fallingState)}: the face against the fall is not
     * sturdy, the falling block is at least as tough, and the block is breakable.
     */
    public static boolean canFallThrough(FallCell target, int fallingToughness) {
        return !target.sturdy() && fallingToughness >= target.toughness() && !target.unbreakable();
    }

    /** TFC {@code canPlaceAt}: replaceable, the falling state survives there, and it cannot fall through the block below. */
    public static boolean canPlaceAt(FallCell hit, boolean survives, FallCell below, int toughness) {
        return hit.replaceable() && survives && !canFallThrough(below, toughness);
    }

    /** The cells and flags one on-ground tick of TFC's falling block reads. */
    public record Contact(FallCell at, FallCell below, FallCell above, boolean survivesAt, boolean survivesAbove,
            boolean onBlock, boolean failedBreakCheck, boolean movingPistonAt) {
    }

    /**
     * TFC {@code TFCFallingBlockEntity.tick}, the {@code onGround()} branch, in TFC's order: the break checks (only on a
     * block and not right after a break), the moving-piston bounce, then placing at {@code posAt}, dropping when not on a
     * block, placing above (checked with bedrock toughness), breaking the block above and placing there, else dropping.
     */
    public static Landing decide(Contact c, int fallingToughness) {
        if (c.onBlock() && !c.failedBreakCheck()) {
            if (!c.at().airOrEmptyFluid() && canFallThrough(c.at(), fallingToughness)) {
                return Landing.DESTROY_AT;
            }
            if (!c.below().airOrEmptyFluid() && canFallThrough(c.below(), fallingToughness)) {
                return Landing.DESTROY_BELOW;
            }
        }
        if (c.movingPistonAt()) {
            return Landing.BOUNCE;
        }
        if (canPlaceAt(c.at(), c.survivesAt(), c.below(), fallingToughness)) {
            return Landing.PLACE_AT;
        }
        if (!c.onBlock()) {
            return Landing.DROP;
        }
        if (canPlaceAt(c.above(), c.survivesAbove(), c.at(), BEDROCK_TOUGHNESS)) {
            return Landing.PLACE_ABOVE;
        }
        if (canFallThrough(c.above(), BEDROCK_TOUGHNESS)) {
            return Landing.DESTROY_ABOVE_AND_PLACE_ABOVE;
        }
        return Landing.DROP;
    }

    /**
     * TFC's in-air expiry: {@code (time > 100 && (y < minBuildHeight || y > maxBuildHeight)) || time > 600}, with
     * {@code time} already incremented for this tick.
     */
    public static boolean expires(int time, int blockY, int minBuildHeight, int maxBuildHeight) {
        return (time > OUT_OF_WORLD_TIME && (blockY < minBuildHeight || blockY > maxBuildHeight)) || time > MAX_AIR_TIME;
    }

    /**
     * Vanilla {@code FallingBlockEntity.causeFallDamage}: {@code i = ceil(fallDistance - 1)}; nothing when negative;
     * else {@code min(floor(i * perBlock), max)}. Returns 0 when nothing is dealt.
     */
    public static float damage(double fallDistance, float perBlock, int max) {
        int i = (int) Math.ceil((float) fallDistance - 1.0f);
        if (i < 0) {
            return 0f;
        }
        return Math.min((float) Math.floor(i * perBlock), (float) max);
    }

    /** The vertical velocity vanilla moves with this tick: the start-of-tick velocity after gravity. */
    public static double moveVelocity(double vy) {
        return vy - GRAVITY;
    }

    /**
     * Where a block falling from {@code y} by {@code dy} (negative) this tick comes to rest, given the absolute top
     * heights of the collision boxes under its footprint: the highest top in {@code [y + dy, y]}. Empty when it does
     * not touch anything this tick.
     */
    public static OptionalDouble restingTop(double y, double dy, double[] boxTops, double[] boxBottoms) {
        double best = Double.NEGATIVE_INFINITY;
        double lowest = y + Math.min(dy, 0.0);
        for (int i = 0; i < boxTops.length; i++) {
            double top = boxTops[i];
            // the box must start below the block's bottom (a box beside or above it is not landed on)
            if (top >= lowest - 1.0e-7 && top <= y + 1.0e-7 && boxBottoms[i] < y - 1.0e-7 && top > best) {
                best = top;
            }
        }
        return best == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(best);
    }
}
