/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.protocol.item.ItemStack;

/**
 * The hook for client-only item decoration (heat/food/size lore, {@code custom_model_data} for heat glow;
 * ARCHITECTURE.md §10). Whatever a feature adds to outgoing item packets, it must be able to remove again:
 * creative-mode clients send the decorated stack back as the new server stack, so
 * {@link CreativeReturnHandler} calls {@link #strip} on every creative-inventory item.
 *
 * <p>Netty thread, under the {@link PacketHandler} rules. Recognise our own additions by a marker the
 * decorator put in the stack's {@code custom_data} (M0-NET-01 used a line count), never by text.
 */
public interface ClientItemDecoration {
    /** Stable name for logs. */
    String name();

    /**
     * Removes this decoration from {@code item} (the packet's copy) in place.
     *
     * @return true if anything was removed
     */
    boolean strip(ItemStack item);
}
