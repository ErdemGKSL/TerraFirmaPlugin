/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Mold.java @ c3b4d9536 (fill, drainIgnoringTemperature,
 *       isMolten, updateFluid), src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (use, the cast),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatComponent.java @ c3b4d9536 (withHeatCapacity settles
 *       the temperature at the current tick before it changes the capacity)
 */
package dev.tfp.core.vessel;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;

/**
 * A mold item's state, evaluated lazily from the calendar tick. Immutable, and nothing ticks: the temperature at any tick
 * is cooled from the last recorded one by {@link HeatState}. A change of fluid settles the temperature to the change tick
 * before it changes the heat capacity, as TFC's {@code HeatComponent.withHeatCapacity} does, so the cooling of each span
 * uses the capacity that held over it.
 *
 * @param kind         the mold kind
 * @param capacity     the capacity in mB (the config value; the kind's default unless overridden)
 * @param fluid        the content
 * @param heatCapacity the heat capacity of the mold with its content
 * @param heat         the temperature recorded at its tick
 * @param fired        the clay state: {@code false} while the mold is unfired clay, {@code true} once it reached the firing
 *                     temperature. Firing is a heat threshold, not a time: TFC has no drying step for molds.
 */
public record MoldState(MoldKind kind, int capacity, FluidAmount fluid, float heatCapacity, HeatState heat, boolean fired) {

    public MoldState {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
        }
        if (fluid == null || heat == null) {
            throw new IllegalArgumentException("fluid and heat must not be null");
        }
        if (!(heatCapacity > 0f)) {
            throw new IllegalArgumentException("heatCapacity must be > 0: " + heatCapacity);
        }
    }

    /** An empty, unfired mold of the given capacity, recorded cold at {@code tick}. */
    public static MoldState empty(MoldKind kind, int capacity, long tick) {
        return new MoldState(kind, capacity, FluidAmount.EMPTY, MoldRules.POTTERY_HEAT_CAPACITY, HeatState.cold(tick), false);
    }

    /** An empty, unfired mold at the kind's default capacity. */
    public static MoldState empty(MoldKind kind, long tick) {
        return empty(kind, kind.defaultCapacity(), tick);
    }

    /** The temperature at calendar tick {@code now}. */
    public float temperatureAt(long now, HeatConfig config) {
        return heat.temperatureAt(now, heatCapacity, config);
    }

    /**
     * The result of a fill: the mold after it, and the amount taken from the input.
     *
     * @param state  the mold after the fill
     * @param filled the amount taken, {@code 0..input.amount()}
     */
    public record FillOutcome(MoldState state, int filled) {
    }

    /**
     * {@code Mold.fill} followed by {@code updateFluid}. The state is always settled to {@code now}, whether or not
     * fluid moved. To simulate a fill, read {@code filled} and discard the state.
     *
     * @param now            the calendar tick of the fill
     * @param input          the fluid offered
     * @param canContain     whether the mold accepts this fluid
     * @param specificHeat   the specific heat of the fluid the mold holds after the fill, per mB
     * @param config         the heat config
     */
    public FillOutcome fill(long now, FluidAmount input, boolean canContain, float specificHeat, HeatConfig config) {
        MoldRules.FillResult result = MoldRules.fill(fluid, input, capacity, canContain);
        return new FillOutcome(withContent(now, result.content(), specificHeat, config), result.filled());
    }

    /** {@code drainIgnoringTemperature(Integer.MAX_VALUE)}: the mold is emptied, whatever its temperature. */
    public MoldState drain(long now, HeatConfig config) {
        return withContent(now, FluidAmount.EMPTY, 0f, config);
    }

    /** {@code Mold.isMolten} at {@code now}, against the melt temperature of the fluid in the mold. */
    public boolean isMolten(long now, float meltTemperature, HeatConfig config) {
        return MoldRules.isMolten(temperatureAt(now, config), meltTemperature);
    }

    /**
     * {@code setTemperature} on the mold. The heat capacity does not change, and the clay fires when the temperature
     * reaches {@link MoldRules#FIRING_TEMPERATURE}.
     *
     * @param now         the calendar tick of the change
     * @param temperature the new temperature, {@code >= 0}
     */
    public MoldState heatTo(long now, float temperature) {
        return new MoldState(kind, capacity, fluid, heatCapacity, new HeatState(temperature, now),
            fired || MoldRules.firesAt(temperature));
    }

    /**
     * The result of a cast attempt.
     *
     * @param mold          the mold after the attempt (drained when the cast was made)
     * @param produced      whether the casting recipe ran, giving one result item
     * @param resultTemperature the temperature of the result item, which is the mold's temperature when it was produced
     * @param moldBroken    whether the mold broke, so the caller removes it from the player's hand
     */
    public record CastOutcome(MoldState mold, boolean produced, float resultTemperature, boolean moldBroken) {
    }

    /**
     * The casting step of {@code MoldItem.use} and {@code overrideOtherStackedOnMe}. A cast needs a mold that is not
     * molten, and a content of the required fluid at least the required amount. A cast drains the mold, and the mold
     * breaks when the draw is below the break chance.
     *
     * @param now            the calendar tick
     * @param config         the heat config
     * @param required       the casting recipe's fluid ingredient
     * @param meltTemperature the melt temperature of the mold's fluid
     * @param breakChance    the recipe's break chance
     * @param breakDraw      a draw in {@code [0, 1)}, taken by the caller only when a cast is made
     */
    public CastOutcome cast(long now, HeatConfig config, FluidAmount required, float meltTemperature, float breakChance,
                            float breakDraw) {
        boolean castable = !isMolten(now, meltTemperature, config)
            && !fluid.isEmpty()
            && fluid.fluid().equals(required.fluid())
            && fluid.amount() >= required.amount();
        if (!castable) {
            return new CastOutcome(this, false, 0f, false);
        }
        float moldTemperature = temperatureAt(now, config);
        MoldState drained = drain(now, config);
        return new CastOutcome(drained, true, moldTemperature, MoldRules.breaks(breakDraw, breakChance));
    }

    private MoldState withContent(long now, FluidAmount content, float specificHeat, HeatConfig config) {
        float settled = temperatureAt(now, config);
        return new MoldState(kind, capacity, content, MoldRules.heatCapacity(specificHeat, content.amount()),
            new HeatState(settled, now), fired);
    }
}
