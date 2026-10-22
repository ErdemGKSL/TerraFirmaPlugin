/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.packet.ItemDecorationHandler.PacketStack;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Test stacks for the tooltip tests: the packet layer's {@link PacketStack} logic over plain NBT and a lore list, the
 * same {@code custom_data} layout a CraftEngine item with our PDC records has on the wire
 * ({@code craftengine:id}, {@code PublicBukkitValues.tfp:heat}/{@code tfp:food}). A PacketEvents item stack itself
 * cannot be built without a running PacketEvents (its item types need Netty).
 */
final class NbtStacks {
    static final Messages MESSAGES = new Messages(NbtStacks.class.getClassLoader());
    static final TooltipText TEXT = MESSAGES::get;

    private NbtStacks() {
    }

    /** One stack: material, amount, {@code custom_data} (null = no component) and lore. */
    static final class Access implements PacketStack.Access {
        String material = "minecraft:paper";
        int amount = 1;
        NBTCompound data;
        List<Component> lore = new ArrayList<>();

        @Override
        public String material() {
            return material;
        }

        @Override
        public int amount() {
            return amount;
        }

        @Override
        public NBTCompound customData() {
            return data;
        }

        @Override
        public void customData(NBTCompound data) {
            this.data = data;
        }

        @Override
        public List<Component> lore() {
            return List.copyOf(lore);
        }

        @Override
        public void lore(List<Component> lines) {
            lore = new ArrayList<>(lines);
        }

        PacketStack stack() {
            return new PacketStack(this);
        }
    }

    /** A CraftEngine item {@code id} with an empty PDC. */
    static Access custom(String id) {
        Access a = new Access();
        a.data = new NBTCompound();
        a.data.setTag("craftengine:id", new NBTString(id));
        a.data.setTag("PublicBukkitValues", new NBTCompound());
        return a;
    }

    static NBTCompound values(Access a) {
        return a.data.getCompoundTagOrNull("PublicBukkitValues");
    }

    /** Adds the {@code tfp:heat} record (HeatPdc layout). */
    static Access heated(Access a, float temperature, long tick) {
        NBTCompound heat = new NBTCompound();
        heat.setTag("tfp:v", new NBTInt(1));
        heat.setTag("tfp:temperature", new NBTFloat(temperature));
        heat.setTag("tfp:tick", new NBTLong(tick));
        values(a).setTag("tfp:heat", heat);
        return a;
    }

    /** Adds the {@code tfp:food} record (FoodPdc layout). */
    static Access food(Access a, long created, String... traits) {
        NBTCompound food = new NBTCompound();
        food.setTag("tfp:v", new NBTInt(1));
        food.setTag("tfp:created", new NBTLong(created));
        NBTList<NBTString> list = NBTList.createStringList();
        for (String t : traits) {
            list.addTag(new NBTString(t));
        }
        food.setTag("tfp:traits", list);
        values(a).setTag("tfp:food", food);
        return a;
    }

    /** A clock reading with player and calendar ticks equal (TFC's default calendar rate). */
    static ClockSnapshot clock(long ticks) {
        return new ClockSnapshot(1L, ticks, ticks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
