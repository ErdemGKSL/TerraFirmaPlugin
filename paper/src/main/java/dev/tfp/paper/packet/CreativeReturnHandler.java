/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Strips client-only decoration from items a creative-mode client sends back
 * ({@code CREATIVE_INVENTORY_ACTION}), so lore we only show never becomes real item data. Delegates to the
 * registered {@link ClientItemDecoration}s; with none registered it costs nothing beyond the dispatch.
 *
 * <p>Verified in M0-NET-01 only through PacketEvents' injected receive path, not a real creative click on
 * the wire (see the spike record); the in-server test belongs to the item-tooltip feature.
 */
public final class CreativeReturnHandler implements PacketHandler {
    public static final String NAME = "creative-return-strip";

    private final List<ClientItemDecoration> decorations = new CopyOnWriteArrayList<>();

    /** Adds a decoration to strip. Any thread. */
    public void add(ClientItemDecoration decoration) {
        decorations.add(decoration);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("CREATIVE_INVENTORY_ACTION");
    }

    @Override
    public PacketDecision onReceive(PacketContext packet) {
        if (decorations.isEmpty()) {
            return PacketDecision.PASS;
        }
        WrapperPlayClientCreativeInventoryAction action =
                new WrapperPlayClientCreativeInventoryAction(packet.nativeEvent(PacketReceiveEvent.class));
        ItemStack item = action.getItemStack();
        if (item == null || item.isEmpty()) {
            return PacketDecision.PASS;
        }
        boolean changed = false;
        for (ClientItemDecoration d : decorations) {
            changed |= d.strip(item);
        }
        if (!changed) {
            return PacketDecision.PASS;
        }
        action.setItemStack(item);
        return PacketDecision.MODIFIED;
    }
}
