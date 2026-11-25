/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/BlowpipeItem.java @ c3b4d9536 (transform, the durations),
 * file: src/main/java/net/dries007/tfc/common/items/GlassBlowpipeItem.java @ c3b4d9536 (use, finishUsingItem, consumeBlowpipe,
 *       the break chances),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassOperation.java @ c3b4d9536 (get: the operation of the
 *       other hand)
 */
package dev.tfp.core.glass;

import dev.tfp.core.craft.CraftOutput;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * The blowpipe's work with a glassworking tool in the other hand, as TFC's {@code GlassBlowpipeItem} does it: which operation
 * the other hand gives, whether the blowpipe is hot enough, and what a finished operation does (a matching recipe gives its
 * result and turns the blowpipe back into an empty one, which breaks with the blowpipe's chance).
 *
 * <p>Item ids here are TFC's ({@code tfc:blowpipe_with_glass}); the plugin maps its {@code tfp:} ids on the way in. Pure and
 * deterministic given the random source.
 */
public final class GlassBlowpipes {
    /** {@code BlowpipeItem.OPERATION_USE_DURATION}: ticks of holding use for one operation. */
    public static final int OPERATION_USE_TICKS = 40;
    /** {@code BlowpipeItem.COOLDOWN_DURATION}: ticks every blowpipe waits after an operation. */
    public static final int COOLDOWN_TICKS = 20;
    /** {@code GlassOperation.get}: looking down further than this (y component of the look vector) stretches instead of blowing. */
    public static final double STRETCH_LOOK_Y = -0.95;

    public static final String BLOWPIPE = "tfc:blowpipe";
    public static final String BLOWPIPE_WITH_GLASS = "tfc:blowpipe_with_glass";
    public static final String CERAMIC_BLOWPIPE = "tfc:ceramic_blowpipe";
    public static final String CERAMIC_BLOWPIPE_WITH_GLASS = "tfc:ceramic_blowpipe_with_glass";

    /** The glassworking tools ({@code IGlassworkingTool}) and the operation each performs. */
    private static final Map<String, GlassOperation> TOOLS = Map.of(
        "tfc:wool_cloth", GlassOperation.ROLL,
        "tfc:jacks", GlassOperation.PINCH,
        "tfc:paddle", GlassOperation.FLATTEN,
        "tfc:gem_saw", GlassOperation.SAW);

    private GlassBlowpipes() {}

    /** Whether {@code item} is a blowpipe that holds glass (TFC's {@code GlassBlowpipeItem}). */
    public static boolean isGlassBlowpipe(String item) {
        return BLOWPIPE_WITH_GLASS.equals(item) || CERAMIC_BLOWPIPE_WITH_GLASS.equals(item);
    }

    /** Whether {@code item} is a blowpipe without glass (TFC's plain {@code BlowpipeItem}). */
    public static boolean isEmptyBlowpipe(String item) {
        return BLOWPIPE.equals(item) || CERAMIC_BLOWPIPE.equals(item);
    }

    /** {@code BlowpipeItem.transform}: with glass becomes without and the other way round; anything else is unchanged. */
    public static String transform(String item) {
        return switch (item) {
            case BLOWPIPE -> BLOWPIPE_WITH_GLASS;
            case BLOWPIPE_WITH_GLASS -> BLOWPIPE;
            case CERAMIC_BLOWPIPE_WITH_GLASS -> CERAMIC_BLOWPIPE;
            case CERAMIC_BLOWPIPE -> CERAMIC_BLOWPIPE_WITH_GLASS;
            default -> item;
        };
    }

    /** The chance a finished blowpipe breaks: the clay one 0.1, the metal one 0 ({@code TFCItems}). */
    public static double breakChance(String item) {
        return CERAMIC_BLOWPIPE_WITH_GLASS.equals(item) ? 0.1 : 0.0;
    }

    /**
     * {@code GlassOperation.get(otherHand, player)}: an empty hand blows, or stretches when the player looks nearly straight
     * down; a glassworking tool gives its operation; any other item gives none.
     *
     * @param otherHand the item id in the other hand, or {@code ""} for an empty hand
     * @param lookY     the y component of the player's look direction
     */
    public static Optional<GlassOperation> operationOf(String otherHand, double lookY) {
        if (otherHand == null || otherHand.isEmpty()) {
            return Optional.of(lookY < STRETCH_LOOK_Y ? GlassOperation.STRETCH : GlassOperation.BLOW);
        }
        return Optional.ofNullable(TOOLS.get(otherHand));
    }

    /** The tool item ids and their operations. */
    public static Map<String, GlassOperation> tools() {
        return TOOLS;
    }

    /** What finishing an operation did. */
    public sealed interface Outcome {
        /** The steps grew; no recipe matched. */
        record Stepped(GlassState state) implements Outcome {}

        /**
         * A recipe matched: the player gets {@code result}; the blowpipe becomes {@code blowpipe} (empty when it broke).
         *
         * @param blowpipe the item id the blowpipe becomes, or {@code ""} when it broke
         */
        record Finished(CraftOutput result, String blowpipe, boolean broke) implements Outcome {}
    }

    /**
     * {@code GlassBlowpipeItem.finishUsingItem}: applies {@code op} to the state, then looks for a recipe. A match draws one
     * random number for the break roll ({@code consumeBlowpipe}: {@code nextFloat() < breakChance}); no match draws none.
     *
     * @param blowpipe the item id of the blowpipe being used
     */
    public static Outcome finish(Collection<GlassRecipe> recipes, String blowpipe, GlassState state, GlassOperation op,
                                 DoubleSupplier random) {
        final GlassState next = GlassRules.apply(state, op);
        final Optional<CraftOutput> result = GlassRules.output(recipes, next);
        if (result.isEmpty()) {
            return new Outcome.Stepped(next);
        }
        final boolean broke = random.getAsDouble() < breakChance(blowpipe);
        return new Outcome.Finished(result.get(), broke ? "" : transform(blowpipe), broke);
    }
}
