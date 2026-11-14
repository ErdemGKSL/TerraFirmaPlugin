/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/item/HeatingItemTest.java @ c3b4d9536 (testCookMeat)
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.heat.HeatingSelfCheck;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@code heat.cook_meat} (PAR-HEAT-17): TFC's {@code HeatingItemTest.testCookMeat} on the running server. Every
 * non-vanilla item of {@code #c:foods/raw_meat} is created through CraftEngine, is heatable, reads back the max visible
 * temperature after being set to it, and has a heating recipe that fires at that temperature
 * ({@link HeatingSelfCheck}, the same check the MockBukkit test runs on the datagen output).
 */
public final class HeatCookMeatCase implements SelfTestCase {
    static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "heat.cook_meat: every TFC raw meat heats to max visible temperature and cooks (PAR-HEAT-17)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        ModuleServices services = tfp.moduleServices();
        if (services == null) {
            throw new Skip("the feature modules are not enabled");
        }
        if (services.get(HeatingService.class).isEmpty() || services.get(Recipes.class).isEmpty()
                || services.get(Tags.class).isEmpty() || services.get(ItemHeatHooks.class).isEmpty()
                || services.get(ItemHeatDefinitionsCache.class).isEmpty()) {
            throw new Skip("the heating, recipe or tag module is not enabled");
        }
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine absent: the TFC meat items cannot be created");
        }
        Recipes recipes = services.require(Recipes.class);
        return OnThread.script("heat-cook-meat", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            // The recipe index is built lazily (the first index() call starts a build and returns the previous one):
            // build it for the current data before looking up heating recipes.
            recipes.rebuild().get(STEP.toSeconds(), TimeUnit.SECONDS);
            HeatingSelfCheck.Result result = OnThread.global(tfp.scheduler(), STEP, () -> HeatingSelfCheck.run(
                new HeatingSelfCheck.Services(services.require(HeatingService.class),
                    services.require(ItemHeatHooks.class), services.require(ItemHeatDefinitionsCache.class).get(),
                    tfp.integrations().customItems(), services.require(Tags.class).snapshot(),
                    RecipeModule.vanillaIds(), () -> tfp.clock().get().playerTicks())));
            if (!result.passed()) {
                throw new AssertionError(result.problems().isEmpty() ? "no raw meats checked"
                    : String.join("; ", result.problems()));
            }
            return result.meats().size() + " raw meats heat to max visible temperature and cook";
        });
    }
}
