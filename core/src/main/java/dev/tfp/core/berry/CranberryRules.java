/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (onUpdate: a bush with no
 *       fresh water is dormant; updateShape and getStateForPlacement are the fluid glue),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (CRANBERRY_STAGES, createCranberry),
 *       src/generated/resources/data/tfc/tfc/climate_range/plant/cranberry_bush.json @ c3b4d9536
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;

import java.util.List;

/**
 * The cranberry: a stationary berry bush that only grows while its block holds fresh water. TFC's
 * {@code WaterloggedBerryBushBlock.onUpdate} sets a dry bush dormant, and the random tick returns before any growth, so a dry bush keeps
 * its stage and its growth counter and grows again once it is wet.
 *
 * <p>The rule reads the water at the catch-up's tick only. A dry spell between two catch-ups is not sampled (the wet cycles of the span
 * are counted as {@link BerryRules#catchUp} counts them, as TFC's counter does). That is the same grid-sampling difference as the
 * climate, recorded in the ledger.
 */
public final class CranberryRules {
    private CranberryRules() {
    }

    /**
     * Brings a cranberry up to {@code now}. A wet bush is a stationary bush, caught up by {@link BerryRules#catchUp}. A dry bush at
     * {@code now} is dormant: its climate is still checked (a withering on the way is kept), but it does not grow, and its stored stage,
     * growth budget and counter are left as they were.
     *
     * @param waterlogged whether the block holds fresh water at {@code now}
     */
    public static BerryRules.Outcome catchUp(BerryBush bush, long now, boolean waterlogged, BerryClock clock,
                                             BerryRules.ClimateSource climate, BerryRules.Settings settings) {
        BerryRules.Outcome outcome = BerryRules.catchUp(BushSpecies.CRANBERRY, bush, now, clock, climate, settings);
        if (waterlogged || !(outcome instanceof BerryRules.Alive)) {
            return outcome;
        }
        BerryBush dry = new BerryBush(DORMANT, bush.stage(), bush.growthsRemaining(), bush.lastUpdateTick(), bush.lastPickedTick());
        return new BerryRules.Alive(dry, List.of());
    }
}
