/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the item bars ({@link ItemBarRender}) on the stacks of outgoing item packets and strips them from stacks a
 * creative-mode client sends back (PAR-HEAT-14).
 *
 * <p>Clientbound: {@code SET_SLOT}, {@code WINDOW_ITEMS} (with the carried item), {@code SET_CURSOR_ITEM},
 * {@code SET_PLAYER_INVENTORY} and the result items of {@code MERCHANT_OFFERS}, the paths the heat glow decorates.
 * Entity equipment is left alone: a client draws no item bar on another entity's items. Serverbound:
 * {@code CREATIVE_INVENTORY_ACTION}. Changes are made in place on the packet's own stacks, so they combine with the
 * tooltip and glow handlers on the same packet. The bar is the one at the time the packet is built and follows the
 * temperature when the stack is sent again (the tooltip refresher resends a view whose heat line changed).
 *
 * <p>Netty thread, under the {@link PacketHandler} rules. Links against PacketEvents: create it only when PacketEvents
 * is installed.
 */
public final class ItemBarHandler implements PacketHandler {
    public static final String NAME = "item-bar";

    private final ItemBarRender render;

    public ItemBarHandler(ItemBarRender render) {
        this.render = Objects.requireNonNull(render, "render");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("SET_SLOT", "WINDOW_ITEMS", "SET_CURSOR_ITEM", "SET_PLAYER_INVENTORY", "MERCHANT_OFFERS");
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("CREATIVE_INVENTORY_ACTION");
    }

    private boolean apply(ItemStack item) {
        return item != null && !item.isEmpty() && render.decorate(access(item));
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        boolean changed = false;
        switch (packet.packetType()) {
            case "SET_SLOT" -> {
                WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
                ItemStack item = slot.getItem();
                if (apply(item)) {
                    slot.setItem(item);
                    changed = true;
                }
            }
            case "WINDOW_ITEMS" -> {
                WrapperPlayServerWindowItems window = new WrapperPlayServerWindowItems(event);
                List<ItemStack> items = window.getItems();
                for (ItemStack item : items) {
                    changed |= apply(item);
                }
                Optional<ItemStack> carried = window.getCarriedItem();
                if (carried.isPresent() && apply(carried.get())) {
                    window.setCarriedItem(carried.get());
                    changed = true;
                }
                if (changed) {
                    window.setItems(items);
                }
            }
            case "SET_CURSOR_ITEM" -> {
                WrapperPlayServerSetCursorItem cursor = new WrapperPlayServerSetCursorItem(event);
                ItemStack item = cursor.getStack();
                if (apply(item)) {
                    cursor.setStack(item);
                    changed = true;
                }
            }
            case "SET_PLAYER_INVENTORY" -> {
                WrapperPlayServerSetPlayerInventory inv = new WrapperPlayServerSetPlayerInventory(event);
                ItemStack item = inv.getStack();
                if (apply(item)) {
                    inv.setStack(item);
                    changed = true;
                }
            }
            case "MERCHANT_OFFERS" -> {
                WrapperPlayServerMerchantOffers merchant = new WrapperPlayServerMerchantOffers(event);
                List<MerchantOffer> offers = merchant.getMerchantOffers();
                for (MerchantOffer offer : offers) {
                    ItemStack out = offer.getOutputItem();
                    if (apply(out)) {
                        offer.setOutputItem(out);
                        changed = true;
                    }
                }
                if (changed) {
                    merchant.setMerchantOffers(offers);
                }
            }
            default -> {
                return PacketDecision.PASS;
            }
        }
        return changed ? PacketDecision.MODIFIED : PacketDecision.PASS;
    }

    @Override
    public PacketDecision onReceive(PacketContext packet) {
        WrapperPlayClientCreativeInventoryAction action =
                new WrapperPlayClientCreativeInventoryAction(packet.nativeEvent(PacketReceiveEvent.class));
        ItemStack item = action.getItemStack();
        if (item == null || item.isEmpty() || !ItemBarRender.strip(access(item))) {
            return PacketDecision.PASS;
        }
        action.setItemStack(item);
        return PacketDecision.MODIFIED;
    }

    private static ComponentType<Integer> type(ItemBarRender.Part part) {
        return part == ItemBarRender.Part.MAX_DAMAGE ? ComponentTypes.MAX_DAMAGE : ComponentTypes.DAMAGE;
    }

    /** The view of a PacketEvents stack (the packet's copy). */
    static ItemBarRender.Access access(ItemStack item) {
        return new ItemBarRender.Access() {
            @Override
            public NBTCompound customData() {
                return item.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null);
            }

            @Override
            public void customData(NBTCompound data) {
                if (data == null) {
                    item.getComponents().getPatches().remove(ComponentTypes.CUSTOM_DATA);
                } else {
                    item.setComponent(ComponentTypes.CUSTOM_DATA, data);
                }
            }

            @Override
            public String material() {
                return item.getType().getName().toString();
            }

            @Override
            public int amount() {
                return item.getAmount();
            }

            @Override
            public boolean hasOwnBar() {
                return item.isDamaged();
            }

            @Override
            public Integer patch(ItemBarRender.Part part) {
                Optional<?> entry = item.getComponents().getPatches().get(type(part));
                if (entry == null) {
                    return null;
                }
                return entry.isEmpty() ? ItemBarRender.REMOVED : (Integer) entry.get();
            }

            @Override
            public void patch(ItemBarRender.Part part, Integer value) {
                Map<ComponentType<?>, Optional<?>> patches = item.getComponents().getPatches();
                if (value == null) {
                    patches.remove(type(part));
                } else if (value == ItemBarRender.REMOVED) {
                    patches.put(type(part), Optional.empty());
                } else {
                    patches.put(type(part), Optional.of(value));
                }
            }
        };
    }
}
