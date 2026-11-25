/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (get(side): the face a hopper
 *       minecart touches is the device's bottom)
 * NeoForge VanillaInventoryCodeHooks.extractHook applies to every Hopper, the hopper minecart included; vanilla
 * MinecartHopper.tick (suckInItems above the minecart, 4 ticks after a success).
 */
package dev.tfp.paper.device.automation;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * PAR-INFRA-64: a hopper minecart under a device empties it like a hopper does. Paper has no search event for minecarts,
 * so every hopper minecart that enters a world gets an entity-scheduled task ({@link #PERIOD} ticks, vanilla's
 * {@code MinecartHopper} cooldown after a success) that looks at the block above it: when that is a registered device,
 * one item moves from the device's bottom face into the minecart through {@link HopperMover#pull}, with the same switch,
 * lock, busy and open-screen gates as a hopper. A minecart does not push (vanilla's does not either); a disabled minecart
 * (activator rail) moves nothing. The task ends with the entity.
 *
 * <p>Threads: the task runs on the minecart's entity thread; the device must be owned by the same region (Folia), else
 * nothing moves and the next run retries.
 */
public final class HopperMinecartAutomation implements Listener {
    /** Ticks between moves and between looks (vanilla {@code MinecartHopper}: 4 after a success). */
    public static final long PERIOD = 4L;

    /** A hopper minecart as the move sees it (tests replace it). */
    public interface Cart {
        boolean enabled();

        Location location();

        org.bukkit.inventory.Inventory inventory();
    }

    private final DeviceAutomation automation;
    private final HopperMover mover;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Predicate<Block> viewed;
    private final Logger logger;

    public HopperMinecartAutomation(DeviceAutomation automation, HopperMover mover, CustomBlocks blocks, Scheduler scheduler,
            Predicate<Block> viewed, Logger logger) {
        this.automation = Objects.requireNonNull(automation, "automation");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.viewed = Objects.requireNonNull(viewed, "viewed");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** {@code cart} as a {@link Cart}. */
    public static Cart of(HopperMinecart cart) {
        return new Cart() {
            @Override
            public boolean enabled() {
                return cart.isEnabled();
            }

            @Override
            public Location location() {
                return cart.getLocation();
            }

            @Override
            public org.bukkit.inventory.Inventory inventory() {
                return cart.getInventory();
            }
        };
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof HopperMinecart cart) {
            Cart view = of(cart);
            scheduler.entityRepeating(cart, PERIOD, PERIOD, () -> step(view), () -> { });
        }
    }

    /** One look: the block above the cart, TFC's source of a hopper ({@code getLevelY() + 1}). @return true when an item moved */
    boolean step(Cart cart) {
        try {
            if (!cart.enabled()) {
                return false;
            }
            Location at = cart.location();
            Block device = at.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() + 1.0), at.getBlockZ());
            if (device.getType().isAir()) {
                return false;
            }
            Optional<BlockStateRef> state = blocks.stateAt(device);
            if (state.isEmpty()) {
                return false;
            }
            Optional<DeviceAutomation.Spec> spec = automation.find(state.get().id());
            if (spec.isEmpty() || !spec.get().enabled().getAsBoolean()) {
                return false;
            }
            if (!scheduler.isOwner(device.getWorld(), device.getX() >> 4, device.getZ() >> 4)) {
                return false;
            }
            if (spec.get().busy().test(device) || viewed.test(device)) {
                return false;
            }
            SideTable table = spec.get().sides().at(state.get());
            return mover.pull(device, spec.get(), table, Face.DOWN, cart.inventory());
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Hopper minecart automation failed", e);
            return false;
        }
    }
}
