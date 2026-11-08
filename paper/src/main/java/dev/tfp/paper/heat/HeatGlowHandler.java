/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the {@link HeatGlow} to the stacks of outgoing item packets and strips it from stacks a creative-mode client
 * sends back (PAR-HEAT-01).
 *
 * <p>Clientbound: {@code SET_SLOT}, {@code WINDOW_ITEMS} (with the carried item), {@code SET_CURSOR_ITEM},
 * {@code SET_PLAYER_INVENTORY}, the result items of {@code MERCHANT_OFFERS} and {@code ENTITY_EQUIPMENT} (a hot item in
 * another player's hand glows too). Serverbound: {@code CREATIVE_INVENTORY_ACTION}. Changes are made in place on the
 * packet's own stacks, so they combine with the tooltip handler on the same packet.
 *
 * <p>Netty thread, under the {@link PacketHandler} rules. Links against PacketEvents: create it only when PacketEvents
 * is installed.
 */
public final class HeatGlowHandler implements PacketHandler {
    public static final String NAME = "heat-glow";

    private final HeatGlow glow;

    public HeatGlowHandler(HeatGlow glow) {
        this.glow = Objects.requireNonNull(glow, "glow");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("SET_SLOT", "WINDOW_ITEMS", "SET_CURSOR_ITEM", "SET_PLAYER_INVENTORY", "MERCHANT_OFFERS",
                "ENTITY_EQUIPMENT");
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("CREATIVE_INVENTORY_ACTION");
    }

    private boolean apply(ItemStack item) {
        return item != null && !item.isEmpty() && glow.decorate(access(item));
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
            case "ENTITY_EQUIPMENT" -> {
                WrapperPlayServerEntityEquipment equipment = new WrapperPlayServerEntityEquipment(event);
                List<Equipment> list = equipment.getEquipment();
                for (Equipment e : list) {
                    changed |= apply(e.getItem());
                }
                if (changed) {
                    equipment.setEquipment(list);
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
        if (item == null || item.isEmpty() || !HeatGlow.strip(access(item))) {
            return PacketDecision.PASS;
        }
        action.setItemStack(item);
        return PacketDecision.MODIFIED;
    }

    /** The view of a PacketEvents stack (the packet's copy). */
    static HeatGlow.Access access(ItemStack item) {
        return new HeatGlow.Access() {
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
            public boolean hasModelData() {
                return item.hasComponent(ComponentTypes.CUSTOM_MODEL_DATA_LISTS)
                        || item.hasComponent(ComponentTypes.CUSTOM_MODEL_DATA);
            }

            @Override
            public void modelData(Float value) {
                if (value == null) {
                    item.getComponents().getPatches().remove(ComponentTypes.CUSTOM_MODEL_DATA_LISTS);
                } else {
                    item.setComponent(ComponentTypes.CUSTOM_MODEL_DATA_LISTS,
                            new ItemCustomModelData(List.of(value), List.of(), List.of(), List.of()));
                }
            }

            @Override
            public String itemModel() {
                Optional<?> patch = item.getComponents().getPatches().get(ComponentTypes.ITEM_MODEL);
                if (patch == null || patch.isEmpty()) {
                    return null;
                }
                return ((ItemModel) patch.get()).getModelLocation().toString();
            }

            @Override
            public void itemModel(String model) {
                if (model == null) {
                    item.getComponents().getPatches().remove(ComponentTypes.ITEM_MODEL);
                } else {
                    item.setComponent(ComponentTypes.ITEM_MODEL, new ItemModel(new ResourceLocation(model)));
                }
            }
        };
    }
}
