/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Applies the client-only item decorations (heat, food and size lines; ARCHITECTURE.md §10) to the stacks of outgoing
 * inventory packets: {@code SET_SLOT} and {@code WINDOW_ITEMS} (a whole container, with the carried item). Every
 * decoration is a function that changes the packet's own copy of a stack in place and returns whether it did; the
 * matching strip is registered with {@link CreativeReturnHandler}.
 *
 * <p>Netty thread, under the {@link PacketHandler} rules: no blocking, no world access.
 */
public final class ItemDecorationHandler implements PacketHandler {
    public static final String NAME = "item-decoration";

    private final List<BiPredicate<ItemStack, String>> decorators = new CopyOnWriteArrayList<>();
    private final Function<UUID, String> locales;

    /** @param locales the client language code of a player (see {@link ClientLocaleHandler}); any thread */
    public ItemDecorationHandler(Function<UUID, String> locales) {
        this.locales = locales;
    }

    /**
     * Adds a decoration: it changes the stack in place, given the client language code, and returns true if it did.
     * Any thread.
     */
    public void add(BiPredicate<ItemStack, String> decorator) {
        decorators.add(decorator);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("SET_SLOT", "WINDOW_ITEMS");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        if (decorators.isEmpty()) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        String locale = locales.apply(packet.playerId());
        boolean changed = false;
        if (packet.packetType().equals("SET_SLOT")) {
            WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
            ItemStack item = slot.getItem();
            if (decorate(item, locale)) {
                slot.setItem(item);
                changed = true;
            }
        } else {
            WrapperPlayServerWindowItems window = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = window.getItems();
            for (ItemStack item : items) {
                changed |= decorate(item, locale);
            }
            Optional<ItemStack> carried = window.getCarriedItem();
            if (carried.isPresent() && decorate(carried.get(), locale)) {
                window.setCarriedItem(carried.get());
                changed = true;
            }
            if (changed) {
                window.setItems(items);
            }
        }
        return changed ? PacketDecision.MODIFIED : PacketDecision.PASS;
    }

    private boolean decorate(ItemStack item, String locale) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (BiPredicate<ItemStack, String> d : decorators) {
            changed |= d.test(item, locale);
        }
        return changed;
    }
}
