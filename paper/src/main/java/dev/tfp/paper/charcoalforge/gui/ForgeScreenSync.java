/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java @ c3b4d9536 (addContainerSlots, moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java @ c3b4d9536 (renderBg: the temperature bar;
 *       the tooltip heatTooltipStyle.formatColored(temperature))
 */
package dev.tfp.paper.charcoalforge.gui;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-METAL-09: keeps an open forge screen in step with the other writers of the forge's slots (a heat run converting an
 * item, removal, the structure scan): commits its copy before, reloads it after. On the viewer's thread: inline when this
 * thread owns the viewer, otherwise the reload is queued there.
 */
public final class ForgeScreenSync implements DeviceService.AccessHook {
    private final ForgeScreens screens;
    private final Scheduler scheduler;

    public ForgeScreenSync(ForgeScreens screens, Scheduler scheduler) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void before(Block block) {
        withViewer(block, screens::edit, false);
    }

    @Override
    public void after(Block block) {
        withViewer(block, screens::reload, true);
    }

    private void withViewer(Block block, Consumer<Object> action, boolean queue) {
        ForgeScreenLink.Key key = ForgeScreenLink.Key.of(block);
        Optional<GuiSession> session = screens.viewer(key);
        if (session.isEmpty()) {
            return;
        }
        Player viewer = session.get().viewer();
        Location at = viewer.getLocation();
        if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            action.accept(key);
        } else if (queue) {
            scheduler.entity(viewer, () -> action.accept(key), () -> { });
        }
    }
}
