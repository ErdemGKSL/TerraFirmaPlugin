/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.casting.CastingRecipe;
import dev.tfp.core.casting.CastingRules;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.metal.Metal;
import dev.tfp.core.metal.MetalTier;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code metal.run_rules} (M4, first slice of the metal run): the ported pure metal rules, run inside the
 * running server. No world, no CraftEngine block: only {@code core/} rules, compared with the values that the
 * core golden tests pin.
 *
 * <p>Checked, each against its golden source in {@code core/src/test/resources}:
 * <ul>
 *   <li>the metal table ({@code metal/metals.tsv}, {@code MetalTableTest}): 27 metals, 9 tool tiers, bronze
 *       melts at 950 and forges at 570, its tier is level 2 with 1300 uses, 7.3 speed and 4.0 damage; copper
 *       melts at 1080; the unknown fallback id is not a metal;</li>
 *   <li>one alloy ({@code alloy/alloy-recipes.tsv}, {@code FluidAlloyTest}): bronze is copper 0.88 to 0.92 with tin
 *       0.08 to 0.12, so 900 mB copper and 100 mB tin give bronze;</li>
 *   <li>one casting ({@code casting/casting-recipes.tsv}): {@code copper_fire_ingot} needs 100 mB copper in a fire
 *       ingot mold, yields one copper ingot and breaks with chance 0.01 (below 0.01 breaks, from 0.01 keeps);</li>
 *   <li>one forging step count ({@code ForgeRuleTest}): offset 4 is reached in 2 steps (+7 bend, -3 hit light).</li>
 * </ul>
 *
 * <p>Any difference fails the case with an {@link AssertionError}; nothing is skipped.
 */
public final class MetalRunCase implements SelfTestCase {
    static final int METAL_COUNT = 27;
    static final int TIER_COUNT = 9;

    @Override
    public String name() {
        return "metal.run_rules: the metal table, a tier, one alloy, one casting and one forging step count match the core golden values (M4)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(20);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        return CompletableFuture.supplyAsync(MetalRunCase::check, ctx.tfp().scheduler().ioExecutor());
    }

    static String check() {
        // Metal table (metal/metals.tsv, MetalTableTest).
        expect("metal count", METAL_COUNT, Metal.values().length);
        expect("tool tier count", TIER_COUNT, MetalTier.values().length);
        expect("unknown fallback is not a metal", true, Metal.fromId(Metal.UNKNOWN_ID).isEmpty());
        Metal bronze = Metal.fromId("tfc:metal/bronze").orElseThrow(() -> new AssertionError("tfc:metal/bronze is not a metal"));
        expect("bronze melt temperature", 950.0f, bronze.meltTemperature());
        expect("bronze ingot forging temperature", 570.0f, bronze.ingotForgingTemperature());
        expect("bronze tier level", 2, bronze.tier());
        MetalTier bronzeTier = bronze.toolTier().orElseThrow(() -> new AssertionError("bronze has no tool tier"));
        expect("bronze tier uses", 1300, bronzeTier.durability());
        expect("bronze tier speed", 7.3f, bronzeTier.speed());
        expect("bronze tier damage", 4.0f, bronzeTier.attackDamage());
        expect("copper melt temperature", 1080.0f, Metal.COPPER.meltTemperature());

        // One alloy (alloy/alloy-recipes.tsv, FluidAlloyTest): bronze is copper 0.88-0.92 and tin 0.08-0.12.
        AlloyRecipe bronzeRecipe = new AlloyRecipe("tfc:metal/bronze", List.of(
                new AlloyRange("tfc:metal/copper", 0.88, 0.92),
                new AlloyRange("tfc:metal/tin", 0.08, 0.12)));
        FluidAlloy mix = FluidAlloy.empty()
                .fill("tfc:metal/copper", 900, 16_000).alloy()
                .fill("tfc:metal/tin", 100, 16_000).alloy();
        expect("900 copper and 100 tin make bronze", "tfc:metal/bronze", mix.result(List.of(bronzeRecipe)));

        // One casting (casting/casting-recipes.tsv): copper_fire_ingot, chance 0.01.
        CastingRecipe fireIngot = new CastingRecipe("copper_fire_ingot", "tfc:ceramic/fire_ingot_mold",
                "tfc:metal/copper", 100, "tfc:metal/ingot/copper", 1, 0.01f);
        List<CastingRecipe> castings = List.of(fireIngot);
        expect("fire ingot mold with 100 copper matches", true,
                CastingRules.find(castings, "tfc:ceramic/fire_ingot_mold", "tfc:metal/copper", 100).isPresent());
        expect("fire ingot mold with 99 copper does not match", true,
                CastingRules.find(castings, "tfc:ceramic/fire_ingot_mold", "tfc:metal/copper", 99).isEmpty());
        expect("fire ingot yields one copper ingot", new CastingRules.Output("tfc:metal/ingot/copper", 1),
                CastingRules.assemble(fireIngot));
        expect("fire ingot breaks below its chance", true, CastingRules.breaksOnUse(fireIngot, 0.005f));
        expect("fire ingot does not break at its chance", false, CastingRules.breaksOnUse(fireIngot, 0.01f));

        // One forging check (ForgeRuleTest): offset 4 is two steps (+7 bend, -3 hit light).
        expect("forging offset 4 takes 2 steps", 2, ForgeStep.getOptimalStepsToTarget(4));
        return "27 metals, 9 tiers, bronze 950 / tier 2 / 1300 uses, 900 cu + 100 sn = bronze, copper_fire_ingot "
                + "100 mB -> 1 ingot (0.01), forge offset 4 = 2 steps";
    }

    private static void expect(String what, Object want, Object got) {
        if (!want.equals(got)) {
            throw new AssertionError(what + ": expected " + want + " but got " + got);
        }
    }
}
