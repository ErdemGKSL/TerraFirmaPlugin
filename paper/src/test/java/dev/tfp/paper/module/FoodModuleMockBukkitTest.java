/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.paper.food.FoodMergeListener;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-01 worked example: the food stack rule is wired by {@link FoodModule}, not by TfpPlugin. */
class FoodModuleMockBukkitTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("tfp-test");

    private ModuleContext context(ModuleServices services) {
        return new ModuleContext.Of((JavaPlugin) plugin, LOG, null, null, null, null, null, Messages.fromJar(), null,
                null, null, null, null, services);
    }

    private boolean mergeListenerRegistered() {
        for (RegisteredListener r : HandlerList.getRegisteredListeners(plugin)) {
            if (r.getListener() instanceof FoodMergeListener) {
                return true;
            }
        }
        return false;
    }

    @Test
    void enablingRegistersTheMergeListenerOnTheProvidedHooks() {
        ModuleServices services = new ModuleServices();
        long step = FoodConfig.DEFAULT.stackTicks();
        services.provide(ItemFoodHooks.class,
                new ItemFoodHooks(() -> 10 * step, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT));
        FeatureModules modules = FeatureModules.of(List.of(new FoodModule()));
        assertThat(modules.enableAll(context(services), LOG)).containsExactly(FoodModule.ID);
        assertThat(mergeListenerRegistered()).isTrue();
        assertThat(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(r -> r.getListener() instanceof FoodMergeListener);
    }

    @Test
    void withoutTheFoodHooksTheModuleStaysOff() {
        FeatureModules modules = FeatureModules.of(List.of(new FoodModule()));
        assertThat(modules.enableAll(context(new ModuleServices()), LOG)).isEmpty();
        assertThat(modules.problems()).anyMatch(p -> p.contains(ItemFoodHooks.class.getName()));
        assertThat(mergeListenerRegistered()).isFalse();
    }
}
