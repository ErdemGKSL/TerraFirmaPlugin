/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.blastfurnace.BlastFurnaceScreenLayout;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-METAL-27: the textured blast furnace screen over the furnace's device: the tuyere slot, the buttons and the frames. */
class BlastFurnaceScreensMockBukkitTest extends BlastFurnaceMockBukkitTest {
    /** Every task runs at once: the test thread owns everything. */
    private static Scheduler inline() {
        Scheduler.TaskHandle handle = new Scheduler.TaskHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, m, args) -> {
                switch (m.getName()) {
                    case "region" -> {
                        ((Runnable) args[args.length - 1]).run();
                        return null;
                    }
                    case "entity", "entityDelayed" -> {
                        ((Runnable) args[m.getName().equals("entity") ? 1 : 2]).run();
                        return true;
                    }
                    case "entityRepeating" -> {
                        return handle;
                    }
                    case "isOwner" -> {
                        return true;
                    }
                    default -> throw new UnsupportedOperationException(m.getName());
                }
            });
    }

    private BlastFurnaceScreens screens;
    private Player player;

    private GuiSession open() {
        Scheduler sch = inline();
        GuiService gui = new GuiService(server, sch, clock::get, true);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        screens = new BlastFurnaceScreens(device, service, gui, sch, Messages.fromJar());
        player = server.addPlayer("Bot");
        screens.open(player, furnace);
        return GuiService.session(player.getOpenInventory().getTopInventory());
    }

    @Test
    void theScreenOpensOnTheFourRowChestWithTheTuyereSlotAndTheTooltipButtons() {
        GuiSession s = open();
        assertThat(s).isNotNull();
        assertThat(s.getInventory().getSize()).isEqualTo(36);
        assertThat(s.getInventory().getItem(BlastFurnaceScreens.TUYERE_SLOT)).isNull();
        for (int slot : BlastFurnaceScreenLayout.THERMOMETER_SLOTS) {
            assertThat(GuiButtons.isButton(s.getInventory().getItem(slot))).as("thermometer %d", slot).isTrue();
        }
        for (int slot : BlastFurnaceScreenLayout.ORE_SLOTS) {
            assertThat(GuiButtons.isButton(s.getInventory().getItem(slot))).isTrue();
        }
        for (int slot : BlastFurnaceScreenLayout.TANK_SLOTS) {
            assertThat(GuiButtons.isButton(s.getInventory().getItem(slot))).isTrue();
        }
    }

    @Test
    void aTuyerePlacedInTheSlotIsCommittedToTheFurnaceAndTakenBackOut() {
        GuiSession s = open();
        s.getInventory().setItem(BlastFurnaceScreens.TUYERE_SLOT, new ItemStack(Material.BLAZE_ROD));
        screens.touched(s);
        assertThat(device.hasTuyere(record())).isTrue();
        assertThat(device.tuyere(record()).getType()).isEqualTo(Material.BLAZE_ROD);
        s.getInventory().setItem(BlastFurnaceScreens.TUYERE_SLOT, null);
        screens.touched(s);
        assertThat(device.hasTuyere(record())).isFalse();
    }

    @Test
    void anOpenScreenShowsTheTuyereTheDeviceHolds() {
        service.edit(furnace, device.kind(), (r, now) -> r.withSlot(BlastFurnaceDevice.TUYERE,
            codec.encode(new ItemStack(Material.BLAZE_ROD))));
        GuiSession s = open();
        assertThat(s.getInventory().getItem(BlastFurnaceScreens.TUYERE_SLOT).getType()).isEqualTo(Material.BLAZE_ROD);
    }

    @Test
    void theFramesFollowTheChimneyAndTheContents() {
        load(8, 8, 8);
        GuiSession s = open();
        var frames = s.gui().frames(s);
        int cap = 5 * config.capacityPerLevel();
        int max = config.maxChimneyHeight() * config.capacityPerLevel();
        assertThat(frames).containsEntry(BlastFurnaceScreenLayout.ORE_BAR_LAYER, BlastFurnaceScreenLayout.barFrame(cap, max));
        assertThat(frames.get(BlastFurnaceScreenLayout.ORE_FILL_LAYER)).isGreaterThanOrEqualTo(0);
        assertThat(frames.get(BlastFurnaceScreenLayout.FUEL_FILL_LAYER)).isGreaterThanOrEqualTo(0);
    }
}
