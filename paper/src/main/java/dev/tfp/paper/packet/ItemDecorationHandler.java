/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.PatchableComponentMap;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Listener;

/**
 * Applies the client-only item tooltip lines (heat, food, size, contents...; ARCHITECTURE.md §10.1) to the stacks of
 * outgoing item packets and strips them from stacks a creative-mode client sends back (PAR-INFRA-05).
 *
 * <p>Clientbound: {@code SET_SLOT}, {@code WINDOW_ITEMS} (any window, with the carried item), {@code SET_CURSOR_ITEM}
 * and {@code SET_PLAYER_INVENTORY} (1.21.2+), and the result item of each {@code MERCHANT_OFFERS} trade. The lines
 * come from a {@link TooltipPipeline}; the pipeline records them in its one {@code custom_data} marker
 * ({@value TooltipPipeline#MARKER}), and {@code CREATIVE_INVENTORY_ACTION} strips exactly that. Entity equipment and
 * dropped items are not decorated: a vanilla client shows no tooltip for them.
 *
 * <p>The handler the plugin registers at start has no pipeline and runs the older single-line decorators of
 * {@link #add}; the tooltip feature module replaces it with a pipeline handler through {@link #takeOver}.
 *
 * <p>Netty thread, under the {@link PacketHandler} rules: no blocking, no world access; the stacks changed are the
 * packet's own copies.
 */
public final class ItemDecorationHandler implements PacketHandler {
    public static final String NAME = "item-decoration";
    /** The language tracker {@link #takeOver} registers for its handler. */
    public static final String LOCALE_NAME = "tooltip-locale";

    private static final String VALUES = "PublicBukkitValues";
    private static final String CE_ID = "craftengine:id";

    private final List<BiPredicate<ItemStack, String>> decorators = new CopyOnWriteArrayList<>();
    private final Function<UUID, String> locales;
    private final TooltipPipeline pipeline;

    /** @param locales the client language code of a player (see {@link ClientLocaleHandler}); any thread */
    public ItemDecorationHandler(Function<UUID, String> locales) {
        this(locales, null);
    }

    /**
     * @param locales  the client language code of a player (see {@link ClientLocaleHandler}); any thread
     * @param pipeline the tooltip lines to add, or null for only the {@link #add} decorators
     */
    public ItemDecorationHandler(Function<UUID, String> locales, TooltipPipeline pipeline) {
        this.locales = Objects.requireNonNull(locales, "locales");
        this.pipeline = pipeline;
    }

    /**
     * Replaces the registered {@value #NAME} handler (the one the plugin registers at start) with one that adds the
     * lines of {@code pipeline}, and registers a client language tracker ({@value #LOCALE_NAME}) for it. Startup thread,
     * before the packet layer is installed. Does nothing when no {@value #NAME} handler is registered (PacketEvents is
     * not installed).
     *
     * @param listen registers the tracker's quit listener
     * @return whether the handler was replaced
     */
    public static boolean takeOver(PacketHandlerRegistry registry, TooltipPipeline pipeline, Consumer<Listener> listen) {
        Objects.requireNonNull(pipeline, "pipeline");
        if (!registry.handlers().contains(NAME)) {
            return false;
        }
        ClientLocaleHandler locales = new ClientLocaleHandler();
        registry.register(new Renamed(LOCALE_NAME, locales));
        listen.accept(locales);
        registry.unregister(NAME);
        registry.register(new ItemDecorationHandler(locales::locale, pipeline));
        return true;
    }

    /**
     * Adds a single-line decorator of the plugin's start-up wiring: it changes the stack in place, given the client
     * language code, and returns true if it did. Strips are registered with {@link CreativeReturnHandler}. Any thread.
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
        return Set.of("SET_SLOT", "WINDOW_ITEMS", "SET_CURSOR_ITEM", "SET_PLAYER_INVENTORY", "MERCHANT_OFFERS");
    }

    @Override
    public Set<String> receiveTypes() {
        return pipeline == null ? Set.of() : Set.of("CREATIVE_INVENTORY_ACTION");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        boolean usePipeline = pipeline != null && !pipeline.isEmpty();
        if (decorators.isEmpty() && !usePipeline) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        String locale = locales.apply(packet.playerId());
        Decorate d = new Decorate(locale, usePipeline ? pipeline.context(locale) : null);
        boolean changed = false;
        switch (packet.packetType()) {
            case "SET_SLOT" -> {
                WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
                ItemStack item = slot.getItem();
                if (d.apply(item)) {
                    slot.setItem(item);
                    changed = true;
                }
            }
            case "WINDOW_ITEMS" -> {
                WrapperPlayServerWindowItems window = new WrapperPlayServerWindowItems(event);
                List<ItemStack> items = window.getItems();
                for (ItemStack item : items) {
                    changed |= d.apply(item);
                }
                Optional<ItemStack> carried = window.getCarriedItem();
                if (carried.isPresent() && d.apply(carried.get())) {
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
                if (d.apply(item)) {
                    cursor.setStack(item);
                    changed = true;
                }
            }
            case "SET_PLAYER_INVENTORY" -> {
                WrapperPlayServerSetPlayerInventory inv = new WrapperPlayServerSetPlayerInventory(event);
                ItemStack item = inv.getStack();
                if (d.apply(item)) {
                    inv.setStack(item);
                    changed = true;
                }
            }
            case "MERCHANT_OFFERS" -> {
                WrapperPlayServerMerchantOffers merchant = new WrapperPlayServerMerchantOffers(event);
                List<MerchantOffer> offers = merchant.getMerchantOffers();
                for (MerchantOffer offer : offers) {
                    ItemStack out = offer.getOutputItem();
                    if (d.apply(out)) {
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
        if (pipeline == null) {
            return PacketDecision.PASS;
        }
        WrapperPlayClientCreativeInventoryAction action =
                new WrapperPlayClientCreativeInventoryAction(packet.nativeEvent(PacketReceiveEvent.class));
        ItemStack item = action.getItemStack();
        if (item == null || item.isEmpty() || !TooltipPipeline.strip(PacketStack.of(item))) {
            return PacketDecision.PASS;
        }
        action.setItemStack(item);
        return PacketDecision.MODIFIED;
    }

    /** One packet's decoration: the legacy decorators, then the pipeline. */
    private final class Decorate {
        private final String locale;
        private final TooltipContext context;

        Decorate(String locale, TooltipContext context) {
            this.locale = locale;
            this.context = context;
        }

        boolean apply(ItemStack item) {
            if (item == null || item.isEmpty()) {
                return false;
            }
            boolean changed = false;
            for (BiPredicate<ItemStack, String> d : decorators) {
                changed |= d.test(item, locale);
            }
            if (context != null) {
                changed |= pipeline.decorate(PacketStack.of(item), context);
            }
            return changed;
        }
    }

    /**
     * A PacketEvents stack as a {@link TooltipStack}: its lore component and the marker in its {@code custom_data}. The
     * logic works on {@link Access}, so tests can run it on plain NBT (a PacketEvents item stack needs a running
     * PacketEvents with Netty). Public for the tooltip tests.
     */
    public static final class PacketStack implements TooltipStack {
        /** The parts of a stack the pipeline reads and changes. */
        public interface Access {
            /** The vanilla item type id. */
            String material();

            int amount();

            /** The live {@code custom_data} compound, or null without the component. */
            NBTCompound customData();

            /** Sets ({@code data} non-null) or removes ({@code null}) the {@code custom_data} component. */
            void customData(NBTCompound data);

            /** The lore lines (empty without the component). */
            List<Component> lore();

            /** Sets the lore; an empty list removes the component. */
            void lore(List<Component> lines);
        }

        private final Access access;

        public PacketStack(Access access) {
            this.access = Objects.requireNonNull(access, "access");
        }

        /** The view of a PacketEvents stack (the packet's copy). */
        static PacketStack of(ItemStack item) {
            return new PacketStack(new Access() {
                @Override
                public String material() {
                    return item.getType().getName().toString();
                }

                @Override
                public int amount() {
                    return item.getAmount();
                }

                @Override
                public NBTCompound customData() {
                    return item.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null);
                }

                @Override
                public void customData(NBTCompound data) {
                    if (data == null) {
                        item.unsetComponent(ComponentTypes.CUSTOM_DATA);
                    } else {
                        item.setComponent(ComponentTypes.CUSTOM_DATA, data);
                    }
                }

                @Override
                public List<Component> lore() {
                    return item.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines();
                }

                @Override
                public void lore(List<Component> lines) {
                    if (lines.isEmpty()) {
                        resetLore(item.getComponents());
                    } else {
                        item.setComponent(ComponentTypes.LORE, new ItemLore(new ArrayList<>(lines)));
                    }
                }
            });
        }

        /**
         * Returns the stack's lore to its item type's default by dropping the lore patch. Not
         * {@code unsetComponent}: since 1.21.5 every item type has a default (empty) lore, so unsetting writes an
         * explicit removal ({@code !minecraft:lore}) into the patch. On the creative return path that removal reached
         * the server stack, which then no longer stacked with a fresh copy of the same item (in-game check
         * 2026-10-22: creative pick-up and put-back of a mutton left {@code "!minecraft:lore": {}} on it).
         */
        public static void resetLore(PatchableComponentMap components) {
            components.getPatches().remove(ComponentTypes.LORE);
        }

        @Override
        public TooltipItem item() {
            NBTCompound data = access.customData();
            String material = access.material();
            if (data == null) {
                return new TooltipItem(null, material, access.amount(), TooltipData.EMPTY);
            }
            NBTCompound values = data.getCompoundTagOrNull(VALUES);
            return new TooltipItem(data.getStringTagValueOrNull(CE_ID), material, access.amount(),
                    values == null ? TooltipData.EMPTY : new NbtData(values));
        }

        @Override
        public List<Component> lore() {
            return access.lore();
        }

        @Override
        public void setLore(List<Component> lines) {
            access.lore(lines);
        }

        @Override
        public Number marker() {
            NBTCompound data = access.customData();
            return data == null ? null : data.getNumberTagValueOrNull(TooltipPipeline.MARKER);
        }

        @Override
        public void setMarker(int addedLines) {
            NBTCompound data = access.customData();
            if (data == null) {
                data = new NBTCompound();
                access.customData(data);
            }
            data.setTag(TooltipPipeline.MARKER, new NBTInt(addedLines));
        }

        @Override
        public void clearMarker() {
            NBTCompound data = access.customData();
            if (data == null) {
                return;
            }
            data.removeTag(TooltipPipeline.MARKER);
            if (data.isEmpty()) {
                access.customData(null);
            }
        }
    }

    /** An NBT compound as {@link TooltipData}. Public for the tooltip tests. */
    public static final class NbtData implements TooltipData {
        private final NBTCompound nbt;

        public NbtData(NBTCompound nbt) {
            this.nbt = Objects.requireNonNull(nbt, "nbt");
        }

        @Override
        public Number number(String key) {
            return nbt.getNumberTagValueOrNull(key);
        }

        @Override
        public String string(String key) {
            return nbt.getStringTagValueOrNull(key);
        }

        @Override
        public TooltipData compound(String key) {
            NBTCompound c = nbt.getCompoundTagOrNull(key);
            return c == null ? null : new NbtData(c);
        }

        @Override
        public List<String> strings(String key) {
            NBTList<NBTString> list = nbt.getStringListTagOrNull(key);
            if (list == null) {
                return null;
            }
            List<String> out = new ArrayList<>(list.size());
            for (NBTString s : list.getTags()) {
                out.add(s.getValue());
            }
            return out;
        }
    }

    /** A handler under another name (the second language tracker next to the plugin's {@code client-locale}). */
    private record Renamed(String name, PacketHandler delegate) implements PacketHandler {
        @Override
        public Set<String> sendTypes() {
            return delegate.sendTypes();
        }

        @Override
        public Set<String> receiveTypes() {
            return delegate.receiveTypes();
        }

        @Override
        public PacketDecision onSend(PacketContext packet) {
            return delegate.onSend(packet);
        }

        @Override
        public PacketDecision onReceive(PacketContext packet) {
            return delegate.onReceive(packet);
        }
    }
}
