/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.paper.packet.ClientLocaleHandler;
import dev.tfp.paper.packet.ItemDecorationHandler;
import dev.tfp.paper.packet.PacketDirection;
import dev.tfp.paper.packet.PacketHandlerRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-05: the pipeline handler replaces the start-up handler and covers the item packets; the NBT view. */
class TooltipPacketWiringTest {
    private static TooltipPipeline pipeline() {
        return new TooltipPipeline(() -> NbtStacks.clock(0L), null);
    }

    @Test
    void takeOverReplacesTheStartupHandlerAndAddsALanguageTracker() {
        PacketHandlerRegistry registry = new PacketHandlerRegistry(Logger.getLogger("test"));
        registry.register(new ClientLocaleHandler());
        registry.register(new ItemDecorationHandler(id -> "en_us"));
        assertThat(registry.wants(PacketDirection.RECEIVE, "CREATIVE_INVENTORY_ACTION")).isFalse();
        List<Listener> listeners = new ArrayList<>();

        assertThat(ItemDecorationHandler.takeOver(registry, pipeline(), listeners::add)).isTrue();

        assertThat(registry.handlers()).containsExactly(ClientLocaleHandler.NAME, ItemDecorationHandler.LOCALE_NAME,
                ItemDecorationHandler.NAME);
        assertThat(listeners).singleElement().isInstanceOf(ClientLocaleHandler.class);
        for (String type : List.of("SET_SLOT", "WINDOW_ITEMS", "SET_CURSOR_ITEM", "SET_PLAYER_INVENTORY", "MERCHANT_OFFERS")) {
            assertThat(registry.wants(PacketDirection.SEND, type)).as(type).isTrue();
        }
        assertThat(registry.wants(PacketDirection.RECEIVE, "CREATIVE_INVENTORY_ACTION")).isTrue();
        assertThat(registry.wants(PacketDirection.RECEIVE, "CLIENT_SETTINGS")).isTrue();
    }

    @Test
    void withoutPacketEventsThereIsNothingToTakeOver() {
        PacketHandlerRegistry registry = new PacketHandlerRegistry(Logger.getLogger("test"));
        List<Listener> listeners = new ArrayList<>();
        assertThat(ItemDecorationHandler.takeOver(registry, pipeline(), listeners::add)).isFalse();
        assertThat(registry.handlers()).isEmpty();
        assertThat(listeners).isEmpty();
    }

    @Test
    void theNbtViewReadsNumbersStringsCompoundsAndStringLists() {
        NBTCompound food = new NBTCompound();
        food.setTag("tfp:v", new NBTInt(1));
        NBTList<NBTString> traits = NBTList.createStringList();
        traits.addTag(new NBTString("tfp:salted"));
        traits.addTag(new NBTString("tfp:brined"));
        food.setTag("tfp:traits", traits);
        NBTCompound root = new NBTCompound();
        root.setTag("tfp:food", food);
        root.setTag("name", new NBTString("x"));

        TooltipData data = new ItemDecorationHandler.NbtData(root);
        assertThat(data.string("name")).isEqualTo("x");
        assertThat(data.number("name")).isNull();
        assertThat(data.compound("tfp:food").number("tfp:v")).isEqualTo(1);
        assertThat(data.compound("tfp:food").strings("tfp:traits")).containsExactly("tfp:salted", "tfp:brined");
        assertThat(data.compound("tfp:food").strings("missing")).isNull();
        assertThat(data.compound("name")).isNull();
    }
}
