/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.packet.PacketHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-58: the block sound module loads its PacketEvents-linked handler by name from the packet layer and declares
 * no PacketEvents type itself, so a server without PacketEvents still enables it.
 */
class BlockSoundModuleWiringTest {
    @Test
    void theReflectiveHandlerNameResolvesInThePacketLayer() throws Exception {
        Class<?> handler = Class.forName(BlockSoundModule.PACKET_HANDLER);
        assertThat(handler.getPackageName()).isEqualTo("dev.tfp.paper.packet.blocksound");
        assertThat(PacketHandler.class).isAssignableFrom(handler);
        Object created = handler.getConstructor(BlockSoundRewrite.class, Logger.class)
                .newInstance(new BlockSoundRewrite(new TrackedBlocks()), Logger.getLogger("test"));
        assertThat(((PacketHandler) created).sendTypes()).containsExactly("SOUND_EFFECT");
    }

    @Test
    void featureSideClassesNameNoPacketEventsTypes() {
        for (Class<?> type : new Class<?>[] {BlockSoundModule.class, BlockSoundRewrite.class, BlockSoundTracker.class}) {
            for (Method m : type.getDeclaredMethods()) {
                assertThat(m.toGenericString()).as(type.getSimpleName()).doesNotContain("retrooper");
            }
            for (Field f : type.getDeclaredFields()) {
                assertThat(f.getType().getName()).as(type.getSimpleName()).doesNotContain("retrooper");
            }
        }
    }
}
