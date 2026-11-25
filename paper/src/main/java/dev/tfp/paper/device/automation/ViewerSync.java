/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (a hopper and a viewer share
 *       one menu over one block entity; here the viewer's copy is committed and reloaded around the move)
 */
package dev.tfp.paper.device.automation;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-INFRA-66: a device screen as a {@link DeviceAutomation.ScreenSync}. The screens commit and reload on their viewer's
 * thread; a move on the device's region thread can run them inline only when this thread also owns the viewer (always on
 * Paper). When it does not (Folia, viewer in another region) the move waits ({@link #unsyncable}) and the hopper retries.
 *
 * <p>Threads: the device's region thread.
 */
public record ViewerSync(Function<Block, Optional<Player>> viewer, Consumer<Block> edit, Consumer<Block> reload,
        Scheduler scheduler) implements DeviceAutomation.ScreenSync {
    public ViewerSync {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean unsyncable(Block device) {
        return viewer.apply(device).filter(v -> !owned(v)).isPresent();
    }

    @Override
    public void edit(Block device) {
        viewer.apply(device).ifPresent(v -> edit.accept(device));
    }

    @Override
    public void reload(Block device) {
        viewer.apply(device).ifPresent(v -> reload.accept(device));
    }

    private boolean owned(Player v) {
        Location at = v.getLocation();
        return scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}
