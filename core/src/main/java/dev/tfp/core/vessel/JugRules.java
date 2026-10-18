/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JugItem.java @ c3b4d9536 (finishUsingItem: the drink drain and the break
 *       draw), src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (the container info),
 *       src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (fill, drain),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (jugCapacity 100, jugBreakChance 0.02)
 */
package dev.tfp.core.vessel;

/**
 * The pure rules of a ceramic jug: its capacity, how much a fill takes, how much a drain gives, and the break chance after
 * a drink. A jug holds no heat and no time, so there is no state to evaluate. Nothing here draws random numbers or reads
 * the world; the caller supplies the draw and the whitelist test.
 */
public final class JugRules {
    /** {@code ServerConfig.jugCapacity} default: a jug holds 100 mB. */
    public static final int CAPACITY = 100;
    /** {@code ServerConfig.jugBreakChance} default: the chance a jug breaks after a drink. */
    public static final double BREAK_CHANCE = 0.02;

    private JugRules() {
    }

    /**
     * The outcome of a fill.
     *
     * @param content the content after the fill
     * @param filled  the amount taken from the input, {@code 0..input}
     */
    public record FillResult(FluidAmount content, int filled) {
    }

    /**
     * The outcome of a drain.
     *
     * @param content the content left in the jug
     * @param drained the amount taken out
     */
    public record DrainResult(FluidAmount content, FluidAmount drained) {
    }

    /**
     * {@code FluidComponent.fill} into a jug. A fluid the jug does not accept fills nothing. Into an empty jug the input fills
     * up to the capacity. A different fluid fills nothing. A same-fluid fill adds up to the capacity.
     *
     * @param content    the current content
     * @param input      the fluid offered
     * @param canContain whether the jug's whitelist accepts this fluid
     */
    public static FillResult fill(FluidAmount content, FluidAmount input, boolean canContain) {
        MoldRules.FillResult result = MoldRules.fill(content, input, CAPACITY, canContain);
        return new FillResult(result.content(), result.filled());
    }

    /**
     * {@code FluidComponent.drain}: takes up to {@code amount} out. Asking for more than the content takes all of it. An
     * empty jug drains nothing.
     *
     * @param content the current content
     * @param amount  the most to take, {@code >= 0}
     */
    public static DrainResult drain(FluidAmount content, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        if (content.isEmpty()) {
            return new DrainResult(FluidAmount.EMPTY, FluidAmount.EMPTY);
        }
        if (amount > content.amount()) {
            return new DrainResult(FluidAmount.EMPTY, content);
        }
        return new DrainResult(new FluidAmount(content.fluid(), content.amount() - amount),
            new FluidAmount(content.fluid(), amount));
    }

    /** {@code JugItem.finishUsingItem}: the drink drains {@code Integer.MAX_VALUE}, so it takes the whole content. */
    public static DrainResult drink(FluidAmount content) {
        return drain(content, Integer.MAX_VALUE);
    }

    /**
     * {@code JugItem.finishUsingItem}: the jug breaks when the draw is below the break chance. The draw is taken once per
     * drink, whatever was drunk. TFC compares the float draw to the double config value.
     *
     * @param draw a draw in {@code [0, 1)}
     */
    public static boolean breaksAfterDrink(float draw) {
        return draw < BREAK_CHANCE;
    }
}
