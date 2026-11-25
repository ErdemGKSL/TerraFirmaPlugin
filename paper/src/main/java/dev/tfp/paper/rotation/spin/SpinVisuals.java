/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.packet.PacketLayers;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PAR-MECH-12: starts the spinning shafts for the rotation module: the {@link SpinShafts} fed by the network actors'
 * {@link RotationService.ShaftHook}, the PacketEvents side ({@link PacketSpinWire}, {@link SpinPacketHandler}, looked up by
 * name so this class links without PacketEvents, the rule of {@code PacketLayers}), the one global task per tick that
 * advances the spin clock, and the quit listener. Off (the axles draw nothing) when PacketEvents is missing.
 */
public final class SpinVisuals {
    static final String WIRE_CLASS = "dev.tfp.paper.rotation.spin.PacketSpinWire";
    static final String HANDLER_CLASS = "dev.tfp.paper.rotation.spin.SpinPacketHandler";
    /** The render item of an axle block: {@code tfp:wood/axle/<wood>} draws {@code tfp:render/rotation/wood/axle/<wood>}. */
    static final String AXLE_PREFIX = "tfp:wood/axle/";
    static final String RENDER_PREFIX = "tfp:render/rotation/wood/axle/";
    /** PAR-MECH-20: {@code tfp:wood/bladed_axle/<wood>} draws TFC's shaft-and-blade model {@code tfp:render/rotation/wood/bladed_axle/<wood>}. */
    static final String BLADED_PREFIX = "tfp:wood/bladed_axle/";
    static final String BLADED_RENDER_PREFIX = "tfp:render/rotation/wood/bladed_axle/";
    /** First id of our own range when the server has no shared counter (as the ore overlays'). */
    static final int OWN_ID_BASE = 0x71000000;

    private SpinVisuals() {
    }

    /** The display item of the axle block {@code blockId}, empty for any other block. */
    public static Optional<String> renderItem(String blockId) {
        if (blockId != null && blockId.startsWith(BLADED_PREFIX) && blockId.length() > BLADED_PREFIX.length()) {
            return Optional.of(BLADED_RENDER_PREFIX + blockId.substring(BLADED_PREFIX.length()));
        }
        if (blockId == null || !blockId.startsWith(AXLE_PREFIX) || blockId.length() == AXLE_PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(RENDER_PREFIX + blockId.substring(AXLE_PREFIX.length()));
    }

    /**
     * Wires the spinning shafts, or returns empty with a log line when PacketEvents is not installed.
     *
     * @param items reads an axle's render item on its region thread
     */
    public static Optional<SpinShafts> start(ModuleContext context, RotationService networks, SpinShafts.ItemResolver items)
            throws ReflectiveOperationException {
        if (context.plugin().getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) == null) {
            context.logger().warning("Rotation: PacketEvents is not installed; axles are not drawn");
            return Optional.empty();
        }
        Class<?> wireClass = Class.forName(WIRE_CLASS, true, SpinVisuals.class.getClassLoader());
        SpinWire wire = (SpinWire) wireClass.getConstructor().newInstance();
        SpinShafts shafts = new SpinShafts(wire, entityIds(), items);
        context.packets().register((PacketHandler) Class.forName(HANDLER_CLASS, true, SpinVisuals.class.getClassLoader())
            .getConstructor(SpinShafts.class, wireClass, Function.class).newInstance(shafts, wire, worlds()));
        context.listen(new Quit(shafts));
        Scheduler scheduler = context.scheduler();
        scheduler.globalRepeating(1L, 1L, shafts::tick);
        networks.shaftHook(shafts::shafts);
        return Optional.of(shafts);
    }

    /** The world of a dimension key, or null. */
    static Function<String, UUID> worlds() {
        return name -> {
            NamespacedKey key = name == null ? null : NamespacedKey.fromString(name);
            World w = key == null ? null : Bukkit.getWorld(key);
            return w == null ? null : w.getUID();
        };
    }

    /**
     * Display entity ids: the server's counter ({@code UnsafeValues#nextEntityId}, Paper 1.21.x) when it exists, else our own
     * range (26.x removed the method), as the ore overlays do.
     */
    @SuppressWarnings("deprecation")
    static IntSupplier entityIds() {
        try {
            Object unsafe = Bukkit.getUnsafe();
            java.lang.reflect.Method next = org.bukkit.UnsafeValues.class.getMethod("nextEntityId");
            if ((int) next.invoke(unsafe) > 0) {
                return () -> {
                    try {
                        return (int) next.invoke(unsafe);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // no shared counter: our own range
        }
        AtomicInteger own = new AtomicInteger(OWN_ID_BASE);
        return () -> {
            int id = own.incrementAndGet();
            if (id < OWN_ID_BASE) {
                own.set(OWN_ID_BASE);
                id = own.incrementAndGet();
            }
            return id;
        };
    }

    /** Forgets a player who left. */
    static final class Quit implements Listener {
        private final SpinShafts shafts;

        Quit(SpinShafts shafts) {
            this.shafts = shafts;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            shafts.quit(event.getPlayer().getUniqueId());
        }
    }
}
