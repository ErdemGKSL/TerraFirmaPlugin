/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.module.FeatureModules;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-03: the module provides the service and routes clicks; without CraftEngine titles are plain. */
class GuiModuleMockBukkitTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("tfp-test");

    @Test
    void enablingProvidesTheServiceAndTheListener() {
        ModuleServices services = new ModuleServices();
        Integrations none = Integrations.create(null, name -> false, List.of());
        ModuleContext ctx = new ModuleContext.Of((JavaPlugin) plugin, LOG, new EntityScheduler(), none, null, null, null,
                Messages.fromJar(), null, null, null, null, null, services);
        FeatureModules modules = FeatureModules.of(List.of(new GuiModule()));
        assertThat(modules.enableAll(ctx, LOG)).containsExactly(GuiModule.ID);
        GuiService service = services.require(GuiService.class);
        assertThat(service.glyphs()).isFalse();
        assertThat(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(r -> r.getListener() instanceof GuiListener);
    }
}
