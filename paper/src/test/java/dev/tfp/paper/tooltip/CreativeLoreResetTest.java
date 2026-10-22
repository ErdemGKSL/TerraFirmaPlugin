/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.PatchableComponentMap;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import dev.tfp.paper.packet.ItemDecorationHandler.PacketStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-05 in-game check 2026-10-22: a creative pick-up and put-back of a decorated mutton left
 * {@code "!minecraft:lore": {}} on the server stack. The strip removed the lore with {@code unsetComponent}, which on a
 * 1.21.5+ item type (default lore: empty) records an explicit removal; the stack then differed from a fresh one. The
 * strip now drops the lore patch, so the stack carries no lore patch at all, as before it was decorated.
 */
class CreativeLoreResetTest {
    private static PacketEventsAPI<?> previous;

    /** Just enough of an API for PacketEvents' registries to load their bundled mappings (1.21.11 server). */
    @BeforeAll
    static void installApi() {
        previous = PacketEvents.getAPI();
        ServerManager server = () -> ServerVersion.V_1_21_11;
        PacketEvents.setAPI(new PacketEventsAPI<Object>() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public void init() {
            }

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public Object getPlugin() {
                return null;
            }

            @Override
            public ServerManager getServerManager() {
                return server;
            }

            @Override
            public ProtocolManager getProtocolManager() {
                return null;
            }

            @Override
            public PlayerManager getPlayerManager() {
                return null;
            }

            @Override
            public NettyManager getNettyManager() {
                return null;
            }

            @Override
            public ChannelInjector getInjector() {
                return null;
            }
        });
    }

    @AfterAll
    static void restoreApi() {
        PacketEvents.setAPI(previous);
    }

    /** A 1.21.5+ item's components: the type's default lore is empty; {@code patches} is what the stack changes. */
    private static PatchableComponentMap components(Optional<?> lorePatch) {
        Map<ComponentType<?>, Object> base = new HashMap<>();
        base.put(ComponentTypes.LORE, ItemLore.EMPTY);
        Map<ComponentType<?>, Optional<?>> patches = new HashMap<>();
        patches.put(ComponentTypes.LORE, lorePatch);
        return new PatchableComponentMap(base, patches);
    }

    @Test
    void resettingTheLoreOfADecoratedStackLeavesNoLorePatch() {
        PatchableComponentMap decorated = components(
                Optional.of(new ItemLore(List.of(Component.text("Expires on: ...")))));

        PacketStack.resetLore(decorated);

        assertThat(decorated.getPatches()).as("no lore patch, not a removal").doesNotContainKey(ComponentTypes.LORE);
        assertThat(decorated.get(ComponentTypes.LORE)).isEqualTo(ItemLore.EMPTY);
        assertThat(decorated).as("same components as an undecorated stack")
                .isEqualTo(new PatchableComponentMap(Map.of(ComponentTypes.LORE, ItemLore.EMPTY), new HashMap<>()));
    }

    @Test
    void unsettingTheLoreRecordsARemoval() {
        PatchableComponentMap decorated = components(Optional.of(new ItemLore(List.of(Component.text("Hot")))));
        decorated.unset(ComponentTypes.LORE); // ItemStack#unsetComponent: the old strip
        assertThat(decorated.getPatches()).as("'!minecraft:lore' on the server stack")
                .containsEntry(ComponentTypes.LORE, Optional.empty());
        assertThat(decorated.get(ComponentTypes.LORE)).isNull();
    }
}
