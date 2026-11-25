/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTickStep: the climate sampled at the step ends)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.crop.CropGrowth;
import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.crop.CropRules;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.farm.SoilTier;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.farm.HydrationSnapshot;
import dev.tfp.paper.worldgen.WorldSettingsLookup;

/**
 * The pure decision of one crop catch-up (decide async, apply sync): given what the region thread read ({@link Environment}), the stored
 * crop, the soil and the clock, it says how the crop ended up. Any thread.
 */
public final class CropSettle {
    /** TFC's {@code cropGrowthModifier} and {@code cropExpiryModifier} config defaults. */
    public static final float GROWTH_MODIFIER = 1f;
    public static final float EXPIRY_MODIFIER = 1f;

    private CropSettle() {
    }

    /**
     * What the region thread read around a crop.
     *
     * @param water          the water around the farmland below (null without farmland)
     * @param farmland       whether a farmland block is below
     * @param lightValid     {@code getRawBrightness >= 12} at the crop
     * @param fluidInCell    whether the crop's cell holds fluid (the flooded crop)
     * @param aboveSameOrAir the cell above is air or the same crop
     * @param aboveStickTop  the cell above is the same crop's top part with a stick
     * @param tier           the soil tier of the farmland
     * @param chunk          the chunk climate
     * @param worldSeed      the world seed
     * @param pos            the crop's position
     */
    public record Environment(HydrationSnapshot water, boolean farmland, boolean lightValid, boolean fluidInCell,
                              boolean aboveSameOrAir, boolean aboveStickTop, SoilTier tier, ChunkClimate chunk, long worldSeed,
                              BlockPos pos) {
    }

    /** The climate of the crop's place by calendar tick: the instant hydration of its farmland and the instant temperature. */
    public static CropRules.ClimateSource climate(Environment env, int daysInMonth) {
        OverworldClimate model = WorldSettingsLookup.climate(env.worldSeed());
        boolean flooded = env.water() != null && env.water().waterloggedAbove();
        boolean source = env.water() != null && FarmlandRules.sourcePresent(env.water().minCostWater());
        float soilMultiplier = env.water() == null ? 1f : env.water().soil().multiplier();
        return tick -> {
            int rain = FarmlandRules.rainHydration(model.instantRainfall(env.chunk(), tick, daysInMonth));
            return new CropRules.Sample(FarmlandRules.instantHydration(rain, flooded, source, soilMultiplier),
                model.instantTemperature(env.pos(), env.chunk(), tick, daysInMonth));
        };
    }

    /** The per-step random factor, deterministic per crop position and step. */
    public static CropRules.Jitter jitter(Environment env) {
        BlockPos p = env.pos();
        return CropRules.Jitter.seeded(env.worldSeed() ^ ((long) p.x() * 3129871L) ^ ((long) p.z() * 116129781L) ^ p.y());
    }

    /**
     * TFC {@code growthTick}: the crop to the clock's calendar tick.
     *
     * @param soil the farmland's nutrients (null without farmland)
     */
    public static CropGrowth.Result settle(CropType type, Environment env, CropPlant plant, Nutrients soil, ClockSnapshot clock) {
        return settle(type, env, plant, soil, clock, null);
    }

    /**
     * As above, with TFC's {@code getGrowthLimit} override of the spreading crops: {@code limitAdjust} takes the base limit (a crowded
     * crop caps it) and returns the limit to use; null leaves it.
     */
    public static CropGrowth.Result settle(CropType type, Environment env, CropPlant plant, Nutrients soil, ClockSnapshot clock,
            java.util.function.UnaryOperator<Float> limitAdjust) {
        float limit = CropGrowth.growthLimit(type, env.lightValid(), env.fluidInCell(), env.aboveSameOrAir(), env.aboveStickTop());
        if (limitAdjust != null) {
            limit = limitAdjust.apply(limit);
        }
        CropRules.Spec spec = type.spec(GROWTH_MODIFIER, EXPIRY_MODIFIER, limit);
        float bonus = env.tier().bonus();
        return CropGrowth.advance(type, plant, env.farmland() ? soil : null, bonus, spec, climate(env, clock.daysInMonth()),
            jitter(env), clock.calendarTicks());
    }
}
