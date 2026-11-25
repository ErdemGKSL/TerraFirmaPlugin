/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (the hammer branch of interactWithAnvil)
 */
package dev.tfp.paper.anvil.weld;

import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.forge.AnvilForgeScreens;
import dev.tfp.paper.anvil.forge.ForgeHeat;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import org.bukkit.Bukkit;

/**
 * PAR-METAL-06: welding on the anvil (flux). Plugs the weld button of the forging screen ({@link AnvilForgeScreens#welding}) and
 * adds the hammer click ({@link AnvilWeldListener}). Needs the anvil, device, tags, recipe, heat and forge modules before it
 * in the service file.
 */
public final class AnvilWeldModule implements FeatureModule {
    public static final String ID = "anvil_weld";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        AnvilEffects effects = context.services().require(AnvilEffects.class);
        AnvilForgeScreens screens = context.services().require(AnvilForgeScreens.class);
        ItemHeatHooks heatHooks = context.services().require(ItemHeatHooks.class);
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        ForgeHeat heat = ForgeHeat.of(heatHooks, context.integrations().customItems()::idOf, definitions,
            () -> context.clock().get().playerTicks());
        WeldBook book = new WeldBook(recipes);
        Welder welder = new Welder(new Welder.Env(devices.stacks(), book, recipes::ingredients, stack -> {
            Optional<String> id = context.integrations().customItems().idOf(stack);
            Optional<dev.tfp.core.heat.ItemHeatDefinitions.HeatData> data = id.flatMap(i -> definitions.get().get(i));
            return data.isEmpty() || heatHooks.temperature(stack) >= data.get().weldingTemperature();
        }, heat, (entry, main) -> recipes.singleOutput(entry.provider(), main), () -> ForgingConfig.DEFAULTS));
        AnvilWelding welding = new AnvilWelding(devices, context.integrations().customBlocks(), effects, context.messages(), welder);
        screens.welding(welding);
        context.listen(new AnvilWeldListener(welding, context.integrations().customBlocks(), context.scheduler(),
            stack -> tags.is(stack, AnvilDevice.HAMMERS), Bukkit::getCurrentTick));
    }

    @Override
    public void disable() {
    }
}
