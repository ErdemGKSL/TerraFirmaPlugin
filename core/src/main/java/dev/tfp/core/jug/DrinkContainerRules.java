/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/GlassBottleItem.java @ c3b4d9536 (DRINK_AMOUNT, finishUsingItem),
 *       src/main/java/net/dries007/tfc/common/items/JugItem.java @ c3b4d9536 (finishUsingItem, afterEmptyFailed),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (the glass bottle break chances)
 */
package dev.tfp.core.jug;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.JugRules;
import java.util.EnumMap;
import java.util.Map;

/**
 * The drinkable containers of TFC: the ceramic jug and the four glass bottles. A drink takes the whole content of a jug
 * ({@code drain(Integer.MAX_VALUE)}) and {@value #BOTTLE_DRINK_AMOUNT} mB of a bottle, then draws once against the break
 * chance of the container. Pure: the caller supplies the draw.
 */
public final class DrinkContainerRules {
    /** {@code GlassBottleItem.DRINK_AMOUNT}. */
    public static final int BOTTLE_DRINK_AMOUNT = 100;

    /** {@code ServerConfig} defaults: {@code <glass>GlassBottleBreakChance}. */
    private static final Map<FluidContainerKind, Double> BREAK = new EnumMap<>(FluidContainerKind.class);

    static {
        BREAK.put(FluidContainerKind.JUG, JugRules.BREAK_CHANCE);
        BREAK.put(FluidContainerKind.SILICA_GLASS_BOTTLE, 0.005);
        BREAK.put(FluidContainerKind.HEMATITIC_GLASS_BOTTLE, 0.02);
        BREAK.put(FluidContainerKind.VOLCANIC_GLASS_BOTTLE, 0.04);
        BREAK.put(FluidContainerKind.OLIVINE_GLASS_BOTTLE, 0.01);
    }

    private DrinkContainerRules() {
    }

    /** Whether the kind is a jug or a glass bottle. */
    public static boolean isDrinkContainer(FluidContainerKind kind) {
        return BREAK.containsKey(kind);
    }

    /** The chance the container breaks after a drink. */
    public static double breakChance(FluidContainerKind kind) {
        Double chance = BREAK.get(kind);
        if (chance == null) {
            throw new IllegalArgumentException("not a drink container: " + kind);
        }
        return chance;
    }

    /** The most one drink takes: everything from a jug, {@value #BOTTLE_DRINK_AMOUNT} mB from a bottle. */
    public static int drinkAmount(FluidContainerKind kind) {
        breakChance(kind);
        return kind == FluidContainerKind.JUG ? Integer.MAX_VALUE : BOTTLE_DRINK_AMOUNT;
    }

    /**
     * The result of a drink.
     *
     * @param content the content left
     * @param drained the fluid drunk (feeds the drinkable's {@code onDrink}); empty when the container was empty
     * @param breaks  whether the container breaks (the draw is below the chance, whatever was drunk)
     */
    public record Drink(FluidAmount content, FluidAmount drained, boolean breaks) {
    }

    /**
     * {@code finishUsingItem}: drains the drink amount, then the break draw.
     *
     * @param draw a float draw in {@code [0, 1)} (TFC compares the float with the double chance)
     */
    public static Drink drink(FluidContainerKind kind, FluidAmount content, float draw) {
        JugRules.DrainResult drain = JugRules.drain(content, drinkAmount(kind));
        return new Drink(drain.content(), drain.drained(), draw < breakChance(kind));
    }

    /** {@code afterEmptyFailed} while sneaking: the whole content is poured out. */
    public static FluidAmount pourOut(FluidAmount content) {
        return JugRules.drain(content, Integer.MAX_VALUE).drained();
    }
}
